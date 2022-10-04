package com.wire.kalium.logic.sync.receiver

import com.wire.kalium.cryptography.CryptoClientId
import com.wire.kalium.cryptography.CryptoSessionId
import com.wire.kalium.cryptography.ProteusClient
import com.wire.kalium.cryptography.utils.AES256Key
import com.wire.kalium.cryptography.utils.EncryptedData
import com.wire.kalium.cryptography.utils.decryptDataWithAES256
import com.wire.kalium.logger.KaliumLogger
import com.wire.kalium.logger.KaliumLogger.Companion.ApplicationFlow.EVENT_RECEIVER
import com.wire.kalium.logger.obfuscateId
import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.ProteusFailure
import com.wire.kalium.logic.configuration.UserConfigRepository
import com.wire.kalium.logic.data.asset.AssetRepository
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.conversation.MLSConversationRepository
import com.wire.kalium.logic.data.event.Event
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.GroupID
import com.wire.kalium.logic.data.id.IdMapper
import com.wire.kalium.logic.data.message.AssetContent
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.MessageRepository
import com.wire.kalium.logic.data.message.PersistMessageUseCase
import com.wire.kalium.logic.data.message.PlainMessageBlob
import com.wire.kalium.logic.data.message.ProtoContent
import com.wire.kalium.logic.data.message.ProtoContentMapper
import com.wire.kalium.logic.data.user.AssetId
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.logic.feature.call.CallManager
import com.wire.kalium.logic.feature.message.EphemeralConversationNotification
import com.wire.kalium.logic.feature.message.EphemeralNotificationsMgr
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
import io.ktor.utils.io.core.toByteArray
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.toInstant
import kotlin.time.Duration.Companion.seconds

interface ConversationEventReceiver : EventReceiver<Event.Conversation>

// Suppressed as it's an old issue
// TODO(refactor): Create a `MessageEventReceiver` to offload some logic from here
@Suppress("LongParameterList", "TooManyFunctions", "ComplexMethod")
internal class ConversationEventReceiverImpl(
    private val proteusClient: ProteusClient,
    private val persistMessage: PersistMessageUseCase,
    private val messageRepository: MessageRepository,
    private val assetRepository: AssetRepository,
    private val conversationRepository: ConversationRepository,
    private val mlsConversationRepository: MLSConversationRepository,
    private val userRepository: UserRepository,
    private val callManagerImpl: Lazy<CallManager>,
    private val editTextHandler: MessageTextEditHandler,
    private val lastReadContentHandler: LastReadContentHandler,
    private val clearConversationContentHandler: ClearConversationContentHandler,
    private val deleteForMeHandler: DeleteForMeHandler,
    private val userConfigRepository: UserConfigRepository,
    private val ephemeralNotificationsManager: EphemeralNotificationsMgr,
    private val pendingProposalScheduler: PendingProposalScheduler,
    private val selfUserId: UserId,
    private val idMapper: IdMapper = MapperProvider.idMapper(),
    private val protoContentMapper: ProtoContentMapper = MapperProvider.protoContentMapper(),
) : ConversationEventReceiver {

    private val logger by lazy { kaliumLogger.withFeatureId(EVENT_RECEIVER) }

    override suspend fun onEvent(event: Event.Conversation) {
        when (event) {
            is Event.Conversation.NewMessage -> handleNewMessage(event)
            is Event.Conversation.NewConversation -> handleNewConversation(event)
            is Event.Conversation.DeletedConversation -> handleDeletedConversation(event)
            is Event.Conversation.MemberJoin -> handleMemberJoin(event)
            is Event.Conversation.MemberLeave -> handleMemberLeave(event)
            is Event.Conversation.MLSWelcome -> handleMLSWelcome(event)
            is Event.Conversation.NewMLSMessage -> handleNewMLSMessage(event)
            is Event.Conversation.MemberChanged -> handleMemberChange(event)
            is Event.Conversation.RenamedConversation -> handleRenamedConversation(event)
            is Event.Conversation.AccessUpdate -> TODO()
        }
    }

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

    private suspend fun handleNewMessage(event: Event.Conversation.NewMessage) {
        val decodedContentBytes = Base64.decodeFromBase64(event.content.toByteArray())
        val cryptoSessionId = CryptoSessionId(
            idMapper.toCryptoQualifiedIDId(event.senderUserId),
            CryptoClientId(event.senderClientId.value)
        )
        wrapCryptoRequest {
            proteusClient.decrypt(decodedContentBytes, cryptoSessionId)
        }.map { PlainMessageBlob(it) }
            .flatMap { plainMessageBlob -> getReadableMessageContent(plainMessageBlob, event) }
            .onFailure {
                when (it) {
                    is CoreFailure.Unknown -> logger.e("$TAG - UnknownFailure when processing message: $it", it.rootCause)
                    is ProteusFailure -> logger.e("$TAG - ProteusFailure when processing message: $it", it.proteusException)
                    else -> logger.e("$TAG - Failure when processing message: $it")
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

    private suspend fun handleNewConversation(event: Event.Conversation.NewConversation) =
        conversationRepository.insertConversationFromEvent(event).flatMap {
            conversationRepository.updateConversationModifiedDate(event.conversationId, Clock.System.now().toString())
        }.onFailure { logger.e("$TAG - failure on new conversation event: $it") }

    private suspend fun handleMemberJoin(event: Event.Conversation.MemberJoin) =
        // Attempt to fetch conversation details if needed, as this might be an unknown conversation
        conversationRepository.fetchConversationIfUnknown(event.conversationId)
            .run {
                onSuccess {
                    logger.v("Succeeded fetching conversation details on MemberJoin Event: $event")
                }
                onFailure {
                    logger.w("Failure fetching conversation details on MemberJoin Event: $event")
                }
                // Even if unable to fetch conversation details, at least attempt adding the member
                conversationRepository.persistMembers(event.members, event.conversationId)
            }.onSuccess {
                val message = Message.System(
                    id = event.id,
                    content = MessageContent.MemberChange.Added(members = event.members.map { it.id }),
                    conversationId = event.conversationId,
                    date = event.timestampIso,
                    senderUserId = event.addedBy,
                    status = Message.Status.SENT,
                    visibility = Message.Visibility.VISIBLE
                )
                processMessage(message) // TODO(exception-handling): processMessage exceptions are not caught
            }.onFailure { logger.e("$TAG - failure on member join event: $it") }

    private suspend fun handleMemberLeave(event: Event.Conversation.MemberLeave) = conversationRepository
        .deleteMembersFromEvent(event.removedList, event.conversationId)
        .flatMap {
            // fetch required unknown users that haven't been persisted during slow sync, e.g. from another team
            // and keep them to properly show this member-leave message
            userRepository.fetchUsersIfUnknownByIds(event.removedList.toSet())
        }
        .onSuccess {
            val message = Message.System(
                id = event.id,
                content = MessageContent.MemberChange.Removed(members = event.removedList),
                conversationId = event.conversationId,
                date = event.timestampIso,
                senderUserId = event.removedBy,
                status = Message.Status.SENT,
                visibility = Message.Visibility.VISIBLE
            )
            processMessage(message)
        }
        .onFailure { logger.e("$TAG - failure on member leave event: $it") }

    private suspend fun handleMemberChange(event: Event.Conversation.MemberChanged) {
        if (event is Event.Conversation.IgnoredMemberChanged) {
            logger.w("failure? member role is missing from event: $event")
            return
        } else {
            // Attempt to fetch conversation details if needed, as this might be an unknown conversation
            conversationRepository.fetchConversationIfUnknown(event.conversationId)
                .run {
                    onSuccess {
                        logger.v("Succeeded fetching conversation details on MemberChange Event: $event")
                    }
                    onFailure {
                        logger.w("Failure fetching conversation details on MemberChange Event: $event")
                    }
                    // Even if unable to fetch conversation details, at least attempt updating the member
                    conversationRepository.updateMemberFromEvent(event.member!!, event.conversationId)
                }.onFailure { logger.e("$TAG - failure on member update event: $it") }
        }
    }

    private suspend fun handleMLSWelcome(event: Event.Conversation.MLSWelcome) {
        mlsConversationRepository.establishMLSGroupFromWelcome(event)
            .onFailure { logger.e("$TAG - failure on MLS welcome event: $it") }
    }

    private suspend fun handleNewMLSMessage(event: Event.Conversation.NewMLSMessage) =
        mlsConversationRepository.messageFromMLSMessage(event)
            .onFailure {
                logger.e("$TAG - failure on MLS message: $it")
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

                bundle.message?.let {
                    val protoContent = protoContentMapper.decodeFromProtobuf(PlainMessageBlob(it))
                    if (protoContent !is ProtoContent.Readable) {
                        throw KaliumSyncException("MLS message with external content", CoreFailure.Unknown(null))
                    }
                    handleContent(
                        conversationId = event.conversationId,
                        timestampIso = event.timestampIso,
                        senderUserId = event.senderUserId,
                        senderClientId = ClientId(""), // TODO(mls): client ID not available for MLS messages
                        content = protoContent
                    )
                }
            }

    private suspend fun handleDeletedConversation(event: Event.Conversation.DeletedConversation) {
        val conversation = conversationRepository.getConversationById(event.conversationId)
        if (conversation != null) {
            conversationRepository.deleteConversation(event.conversationId)
                .onFailure { coreFailure ->
                    logger.e("$TAG - Error deleting the contents of a conversation $coreFailure")
                }.onSuccess {
                    val senderUser = userRepository.observeUser(event.senderUserId).firstOrNull()
                    val dataNotification = EphemeralConversationNotification(event, conversation, senderUser)
                    ephemeralNotificationsManager.scheduleNotification(dataNotification)
                    logger.d("$TAG - Deleted the conversation ${event.conversationId}")
                }
        } else {
            logger.d("$TAG - Skipping conversation delete event already handled")
        }
    }

    private suspend fun handleRenamedConversation(event: Event.Conversation.RenamedConversation) {
        conversationRepository.updateConversationName(event.conversationId, event.conversationName, event.timestampIso)
            .onSuccess {
                logger
                    .d("$TAG - The Conversation was renamed: ${event.conversationId.toString().obfuscateId()}")
                val message = Message.System(
                    id = event.id,
                    content = MessageContent.ConversationRenamed(event.conversationName),
                    conversationId = event.conversationId,
                    date = event.timestampIso,
                    senderUserId = event.senderUserId,
                    status = Message.Status.SENT,
                )
                persistMessage(message)
            }
            .onFailure { coreFailure ->
                logger
                    .e("$TAG - Error renaming the conversation [${event.conversationId.toString().obfuscateId()}] $coreFailure")
            }
    }

    private suspend fun handlePendingProposal(timestamp: Instant, groupId: GroupID, commitDelay: Long) {
        logger.d("Received MLS proposal, scheduling commit in $commitDelay seconds")
        pendingProposalScheduler.scheduleCommit(
            groupId,
            timestamp.plus(commitDelay.seconds)
        )
    }

    private suspend fun processSignaling(senderUserId: UserId, signaling: MessageContent.Signaling) {
        when (signaling) {
            MessageContent.Ignored -> {
                logger
                    .i(message = "$TAG Ignored Signaling Message received: $signaling")
            }

            is MessageContent.Availability -> {
                logger
                    .i(message = "$TAG Availability status update received: ${signaling.status}")
                userRepository.updateOtherUserAvailabilityStatus(senderUserId, signaling.status)
            }
        }
    }

    // TODO(qol): split this function so it's easier to maintain
    @Suppress("ComplexMethod", "LongMethod")
    private suspend fun processMessage(message: Message) {
        logger.i(message = "$TAG Message received: $message")

        when (message) {
            is Message.Regular -> when (val content = message.content) {
                // Persist Messages - > lists
                is MessageContent.Text, is MessageContent.FailedDecryption -> persistMessage(message)
                is MessageContent.Asset -> handleAssetMessage(message)
                is MessageContent.DeleteMessage -> handleDeleteMessage(content, message)
                is MessageContent.DeleteForMe -> deleteForMeHandler.handle(message, content)
                is MessageContent.Calling -> {
                    logger.d("$TAG - MessageContent.Calling")
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

    private companion object {
        const val TAG = "ConversationEventReceiver"
    }
}

fun AssetContent.hasValidRemoteData() = this.remoteData.let {
    it.assetId.isNotEmpty() && it.sha256.isNotEmpty() && it.otrKey.isNotEmpty()
}
