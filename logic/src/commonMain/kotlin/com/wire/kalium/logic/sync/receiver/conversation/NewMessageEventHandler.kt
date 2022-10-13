package com.wire.kalium.logic.sync.receiver.conversation

import com.wire.kalium.cryptography.CryptoClientId
import com.wire.kalium.cryptography.CryptoSessionId
import com.wire.kalium.cryptography.utils.AES256Key
import com.wire.kalium.cryptography.utils.EncryptedData
import com.wire.kalium.cryptography.utils.decryptDataWithAES256
import com.wire.kalium.logger.KaliumLogger
import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.ProteusFailure
import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.configuration.UserConfigRepository
import com.wire.kalium.logic.data.asset.AssetRepository
import com.wire.kalium.logic.data.client.MLSClientProvider
import com.wire.kalium.logic.data.conversation.ApplicationMessage
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.conversation.DecryptedMessageBundle
import com.wire.kalium.logic.data.event.Event
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.GroupID
import com.wire.kalium.logic.data.id.IdMapper
import com.wire.kalium.logic.data.message.AssetContent
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.MessageRepository
import com.wire.kalium.logic.data.message.PersistMessageUseCase
import com.wire.kalium.logic.data.message.PersistReactionUseCase
import com.wire.kalium.logic.data.message.PlainMessageBlob
import com.wire.kalium.logic.data.message.ProtoContent
import com.wire.kalium.logic.data.message.ProtoContentMapper
import com.wire.kalium.logic.data.user.AssetId
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.logic.feature.ProteusClientProvider
import com.wire.kalium.logic.feature.call.CallManager
import com.wire.kalium.logic.feature.message.PendingProposalScheduler
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.flatMap
import com.wire.kalium.logic.functional.map
import com.wire.kalium.logic.functional.onFailure
import com.wire.kalium.logic.functional.onSuccess
import com.wire.kalium.logic.kaliumLogger
import com.wire.kalium.logic.sync.KaliumSyncException
import com.wire.kalium.logic.sync.receiver.message.ClearConversationContentHandler
import com.wire.kalium.logic.sync.receiver.message.DeleteForMeHandler
import com.wire.kalium.logic.sync.receiver.message.LastReadContentHandler
import com.wire.kalium.logic.sync.receiver.message.MessageTextEditHandler
import com.wire.kalium.logic.util.Base64
import com.wire.kalium.logic.wrapCryptoRequest
import com.wire.kalium.logic.wrapMLSRequest
import io.ktor.util.decodeBase64Bytes
import io.ktor.utils.io.core.toByteArray
import kotlinx.datetime.Instant
import kotlinx.datetime.toInstant
import kotlin.time.Duration.Companion.seconds

interface NewMessageEventHandler {
    suspend fun handleNewProteusMessage(event: Event.Conversation.NewMessage)
    suspend fun handleNewMLSMessage(event: Event.Conversation.NewMLSMessage)
}

@Suppress("LongParameterList", "TooManyFunctions")
internal class NewMessageEventHandlerImpl(
    private val proteusClientProvider: ProteusClientProvider,
    private val mlsClientProvider: MLSClientProvider,
    private val userRepository: UserRepository,
    private val assetRepository: AssetRepository,
    private val messageRepository: MessageRepository,
    private val userConfigRepository: UserConfigRepository,
    private val conversationRepository: ConversationRepository,
    private val callManagerImpl: Lazy<CallManager>,
    private val persistMessage: PersistMessageUseCase,
    private val persistReaction: PersistReactionUseCase,
    private val editTextHandler: MessageTextEditHandler,
    private val lastReadContentHandler: LastReadContentHandler,
    private val clearConversationContentHandler: ClearConversationContentHandler,
    private val deleteForMeHandler: DeleteForMeHandler,
    private val pendingProposalScheduler: PendingProposalScheduler,
    private val idMapper: IdMapper = MapperProvider.idMapper(),
    private val protoContentMapper: ProtoContentMapper = MapperProvider.protoContentMapper()
) : NewMessageEventHandler {
    private val logger by lazy { kaliumLogger.withFeatureId(KaliumLogger.Companion.ApplicationFlow.EVENT_RECEIVER) }

    override suspend fun handleNewProteusMessage(event: Event.Conversation.NewMessage) {
        val decodedContentBytes = Base64.decodeFromBase64(event.content.toByteArray())
        val cryptoSessionId = CryptoSessionId(
            idMapper.toCryptoQualifiedIDId(event.senderUserId),
            CryptoClientId(event.senderClientId.value)
        )
        proteusClientProvider.getOrError()
            .flatMap {
                wrapCryptoRequest {
                    it.decrypt(decodedContentBytes, cryptoSessionId)
                }
            }
            .map { PlainMessageBlob(it) }
            .flatMap { plainMessageBlob -> getReadableMessageContent(plainMessageBlob, event) }
            .onFailure {
                when (it) {
                    is CoreFailure.Unknown -> logger.e("UnknownFailure when processing message: $it", it.rootCause)
                    is ProteusFailure -> logger.e("ProteusFailure when processing message: $it", it.proteusException)
                    else -> logger.e("Failure when processing message: $it")
                }
                handleFailedProteusDecryptedMessage(event)
            }.onSuccess { readableContent ->
                handleContent(
                    conversationId = event.conversationId,
                    timestampIso = event.timestampIso,
                    senderUserId = event.senderUserId,
                    senderClientId = event.senderClientId,
                    content = readableContent
                )
            }
    }

    private suspend fun handleFailedProteusDecryptedMessage(event: Event.Conversation.NewMessage) {
        with(event) {
            val message = Message.Regular(
                id = id,
                content = MessageContent.FailedDecryption(encryptedExternalContent?.data),
                conversationId = conversationId,
                date = timestampIso,
                senderUserId = senderUserId,
                senderClientId = senderClientId,
                status = Message.Status.SENT,
                editStatus = Message.EditStatus.NotEdited,
                visibility = Message.Visibility.VISIBLE
            )
            processMessage(message)
        }
    }
    override suspend fun handleNewMLSMessage(event: Event.Conversation.NewMLSMessage) {
        messageFromMLSMessage(event)
            .onFailure {
                logger.e("failure on MLS message: $it")
                handleFailedMLSDecryptedMessage(event)
            }.onSuccess { bundle ->
                if (bundle == null) return@onSuccess

                bundle.commitDelay?.let {
                    handlePendingProposal(
                        timestamp = event.timestampIso.toInstant(),
                        groupId = bundle.groupID,
                        commitDelay = it
                    )
                }

                bundle.applicationMessage?.let {
                    val protoContent = protoContentMapper.decodeFromProtobuf(PlainMessageBlob(it.message))
                    if (protoContent !is ProtoContent.Readable) {
                        throw KaliumSyncException("MLS message with external content", CoreFailure.Unknown(null))
                    }
                    handleContent(
                        conversationId = event.conversationId,
                        timestampIso = event.timestampIso,
                        senderUserId = event.senderUserId,
                        senderClientId = it.senderClientID,
                        content = protoContent
                    )
                }
            }
    }

    private suspend fun handleFailedMLSDecryptedMessage(event: Event.Conversation.NewMLSMessage) {
        with(event) {
            val message = Message.Regular(
                id = id,
                content = MessageContent.FailedDecryption(),
                conversationId = conversationId,
                date = timestampIso,
                senderUserId = senderUserId,
                senderClientId = ClientId(""), // TODO(mls): client ID not available for MLS messages
                status = Message.Status.SENT,
                editStatus = Message.EditStatus.NotEdited,
                visibility = Message.Visibility.VISIBLE
            )
            processMessage(message)
        }
    }

    private suspend fun messageFromMLSMessage(
        messageEvent: Event.Conversation.NewMLSMessage
    ): Either<CoreFailure, DecryptedMessageBundle?> =
        mlsClientProvider.getMLSClient().flatMap { mlsClient ->
            conversationRepository.getConversationById(messageEvent.conversationId)?.let { conversation ->
                if (conversation.protocol is Conversation.ProtocolInfo.MLS) {
                    val groupID = conversation.protocol.groupId
                    wrapMLSRequest {
                        mlsClient.decryptMessage(
                            idMapper.toCryptoModel(groupID),
                            messageEvent.content.decodeBase64Bytes()
                        ).let {
                            DecryptedMessageBundle(
                                groupID,
                                it.message?.let { message ->
                                    // We will always have senderClientId together with an application message
                                    // but CoreCrypto API doesn't express this
                                    val senderClientId = it.senderClientId?.let { senderClientId ->
                                        idMapper.fromCryptoQualifiedClientId(senderClientId)
                                    } ?: ClientId("")

                                    ApplicationMessage(
                                        message,
                                        senderClientId
                                    )
                                },
                                it.commitDelay
                            )
                        }
                    }
                } else {
                    Either.Right(null)
                }
            } ?: Either.Left(StorageFailure.DataNotFound)
        }

    @Suppress("ComplexMethod")
    private suspend fun handleContent(
        conversationId: ConversationId,
        timestampIso: String,
        senderUserId: UserId,
        senderClientId: ClientId,
        content: ProtoContent.Readable
    ) {
        when (content.messageContent) {
            is MessageContent.Regular -> {
                val visibility = when (content.messageContent) {
                    is MessageContent.DeleteMessage -> Message.Visibility.HIDDEN
                    is MessageContent.TextEdited -> Message.Visibility.HIDDEN
                    is MessageContent.DeleteForMe -> Message.Visibility.HIDDEN
                    is MessageContent.Empty -> Message.Visibility.HIDDEN
                    is MessageContent.Unknown ->
                        if (content.messageContent.hidden) Message.Visibility.HIDDEN
                        else Message.Visibility.VISIBLE

                    is MessageContent.Text -> Message.Visibility.VISIBLE
                    is MessageContent.Reaction -> Message.Visibility.HIDDEN
                    is MessageContent.Calling -> Message.Visibility.VISIBLE
                    is MessageContent.Asset -> Message.Visibility.VISIBLE
                    is MessageContent.Knock -> Message.Visibility.VISIBLE
                    is MessageContent.RestrictedAsset -> Message.Visibility.VISIBLE
                    is MessageContent.FailedDecryption -> Message.Visibility.VISIBLE
                    is MessageContent.LastRead -> Message.Visibility.HIDDEN
                    is MessageContent.Cleared -> Message.Visibility.HIDDEN
                }
                val message = Message.Regular(
                    id = content.messageUid,
                    content = content.messageContent,
                    conversationId = conversationId,
                    date = timestampIso,
                    senderUserId = senderUserId,
                    senderClientId = senderClientId,
                    status = Message.Status.SENT,
                    editStatus = Message.EditStatus.NotEdited,
                    visibility = visibility
                )
                processMessage(message)
            }

            is MessageContent.Signaling -> {
                processSignaling(senderUserId, content.messageContent)
            }
        }
    }

    private suspend fun handlePendingProposal(timestamp: Instant, groupId: GroupID, commitDelay: Long) {
        logger.d("Received MLS proposal, scheduling commit in $commitDelay seconds")
        pendingProposalScheduler.scheduleCommit(
            groupId,
            timestamp.plus(commitDelay.seconds)
        )
    }

    private fun getReadableMessageContent(
        plainMessageBlob: PlainMessageBlob,
        event: Event.Conversation.NewMessage
    ) = when (val protoContent = protoContentMapper.decodeFromProtobuf(plainMessageBlob)) {
        is ProtoContent.Readable -> Either.Right(protoContent)
        is ProtoContent.ExternalMessageInstructions -> event.encryptedExternalContent?.let {
            logger.d("Solving external content '$protoContent', EncryptedData='$it'")
            solveExternalContentForProteusMessage(protoContent, event.encryptedExternalContent)
        } ?: run {
            val rootCause = IllegalArgumentException("Null external content when processing external message instructions.")
            Either.Left(CoreFailure.Unknown(rootCause))
        }
    }

    private fun solveExternalContentForProteusMessage(
        externalInstructions: ProtoContent.ExternalMessageInstructions,
        externalData: EncryptedData
    ): Either<CoreFailure, ProtoContent.Readable> = wrapCryptoRequest {
        val decryptedExternalMessage = decryptDataWithAES256(externalData, AES256Key(externalInstructions.otrKey)).data
        logger.d("ExternalMessage - Decrypted external message content: '$decryptedExternalMessage'")
        PlainMessageBlob(decryptedExternalMessage)
    }.map(protoContentMapper::decodeFromProtobuf).flatMap { decodedProtobuf ->
        if (decodedProtobuf !is ProtoContent.Readable) {
            val rootCause = IllegalArgumentException("матрёшка! External message can't contain another external message inside!")
            Either.Left(CoreFailure.Unknown(rootCause))
        } else {
            Either.Right(decodedProtobuf)
        }
    }

    private fun updateAssetMessageWithDecryptionKeys(persistedMessage: Message.Regular, remoteData: AssetContent.RemoteData): Message {
        val assetMessageContent = persistedMessage.content as MessageContent.Asset
        // The message was previously received with just metadata info, so let's update it with the raw data info
        return persistedMessage.copy(
            content = assetMessageContent.copy(
                value = assetMessageContent.value.copy(
                    remoteData = remoteData
                )
            ),
            visibility = Message.Visibility.VISIBLE
        )
    }

    private suspend fun isSenderVerified(messageId: String, conversationId: ConversationId, senderUserId: UserId): Boolean {
        var verified = false
        messageRepository.getMessageById(
            messageUuid = messageId,
            conversationId = conversationId
        ).onSuccess {
            verified = senderUserId == it.senderUserId
        }
        return verified
    }

    private suspend fun processSignaling(senderUserId: UserId, signaling: MessageContent.Signaling) {
        when (signaling) {
            MessageContent.Ignored -> {
                logger
                    .i(message = "Ignored Signaling Message received: $signaling")
            }

            is MessageContent.Availability -> {
                logger
                    .i(message = "Availability status update received: ${signaling.status}")
                userRepository.updateOtherUserAvailabilityStatus(senderUserId, signaling.status)
            }
        }
    }

    // TODO(qol): split this function so it's easier to maintain
    @Suppress("ComplexMethod", "LongMethod")
    private suspend fun processMessage(message: Message) {
        logger.i(message = "Message received: $message")

        when (message) {
            is Message.Regular -> when (val content = message.content) {
                // Persist Messages - > lists
                is MessageContent.Text, is MessageContent.FailedDecryption -> persistMessage(message)
                is MessageContent.Reaction -> persistReaction(message, content)
                is MessageContent.Asset -> handleAssetMessage(message)
                is MessageContent.DeleteMessage -> handleDeleteMessage(content, message)
                is MessageContent.DeleteForMe -> deleteForMeHandler.handle(message, content)
                is MessageContent.Calling -> {
                    logger.d("MessageContent.Calling")
                    callManagerImpl.value.onCallingMessageReceived(
                        message = message,
                        content = content
                    )
                }

                is MessageContent.TextEdited -> editTextHandler.handle(message, content)
                is MessageContent.LastRead -> lastReadContentHandler.handle(message, content)
                is MessageContent.Unknown -> {
                    logger.i(message = "Unknown Message received: $message")
                    persistMessage(message)
                }

                is MessageContent.Cleared -> clearConversationContentHandler.handle(message, content)
                is MessageContent.Empty -> TODO()
                is MessageContent.RestrictedAsset -> TODO()
                is MessageContent.Knock -> TODO()
            }

            is Message.System -> when (message.content) {
                is MessageContent.MemberChange -> {
                    logger.i(message = "System MemberChange Message received: $message")
                    persistMessage(message)
                }

                is MessageContent.ConversationRenamed -> TODO()
                is MessageContent.MissedCall -> TODO()
            }
        }
    }

    private suspend fun handleAssetMessage(message: Message.Regular) {
        val content = message.content as MessageContent.Asset
        userConfigRepository.isFileSharingEnabled().onSuccess {
            if (it.isFileSharingEnabled != null && it.isFileSharingEnabled) {
                processNonRestrictedAssetMessage(message)
            } else {
                val newMessage = message.copy(
                    content = MessageContent.RestrictedAsset(
                        content.value.mimeType, content.value.sizeInBytes, content.value.name ?: ""
                    )
                )
                persistMessage(newMessage)
            }
        }
    }

    private suspend fun processNonRestrictedAssetMessage(message: Message.Regular) {
        val assetContent = message.content as MessageContent.Asset
        val isPreviewMessage = assetContent.value.sizeInBytes > 0 && !assetContent.value.hasValidRemoteData()
        messageRepository.getMessageById(message.conversationId, message.id)
            .onFailure {
                // No asset message was received previously, so just persist the preview of the asset message
                val isValidImage = assetContent.value.metadata?.let {
                    it is AssetContent.AssetMetadata.Image && it.width > 0 && it.height > 0
                } ?: false

                // Web/Mac clients split the asset message delivery into 2. One with the preview metadata (assetName, assetSize...) and
                // with empty encryption keys and the second with empty metadata but all the correct encryption keys. We just want to
                // hide the preview of generic asset messages with empty encryption keys as a way to avoid user interaction with them.
                val previewMessage = message.copy(
                    content = message.content.copy(value = assetContent.value),
                    visibility = if (isPreviewMessage && !isValidImage)
                        Message.Visibility.HIDDEN else Message.Visibility.VISIBLE
                )
                persistMessage(previewMessage)
            }
            .onSuccess { persistedMessage ->
                val validDecryptionKeys = message.content.value.remoteData
                // Check the second asset message is from the same original sender
                if (isSenderVerified(persistedMessage.id, persistedMessage.conversationId, message.senderUserId) &&
                    persistedMessage is Message.Regular
                ) {
                    // The second asset message received from Web/Mac clients contains the full asset decryption keys, so we need to update
                    // the preview message persisted previously with the rest of the data
                    persistMessage(
                        updateAssetMessageWithDecryptionKeys(
                            persistedMessage,
                            validDecryptionKeys
                        )
                    )
                }
            }
    }

    private suspend fun handleDeleteMessage(
        content: MessageContent.DeleteMessage,
        message: Message
    ) {
        if (isSenderVerified(content.messageId, message.conversationId, message.senderUserId)) {
            messageRepository.getMessageById(message.conversationId, content.messageId)
                .onSuccess { messageToRemove ->
                    (messageToRemove.content as? MessageContent.Asset)?.value?.remoteData?.let { assetToRemove ->
                        assetRepository.deleteAssetLocally(
                            AssetId(
                                assetToRemove.assetId,
                                assetToRemove.assetDomain.orEmpty()
                            )
                        )
                            .onFailure {
                                logger.withFeatureId(KaliumLogger.Companion.ApplicationFlow.ASSETS)
                                    .w("delete messageToRemove asset locally failure: $it")
                            }
                    }
                }
            messageRepository.markMessageAsDeleted(
                messageUuid = content.messageId,
                conversationId = message.conversationId
            )
        } else logger.i(message = "Delete message sender is not verified: $message")
    }
}

fun AssetContent.hasValidRemoteData() = this.remoteData.let {
    it.assetId.isNotEmpty() && it.sha256.isNotEmpty() && it.otrKey.isNotEmpty()
}
