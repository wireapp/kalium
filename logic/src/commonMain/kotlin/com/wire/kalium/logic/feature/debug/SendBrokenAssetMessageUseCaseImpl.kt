@file:Suppress("MaximumLineLength")
package com.wire.kalium.logic.feature.debug

import com.benasher44.uuid.uuid4
import com.wire.kalium.cryptography.utils.AES256Key
import com.wire.kalium.cryptography.utils.SHA256Key
import com.wire.kalium.cryptography.utils.generateRandomAES256Key
import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.data.asset.AssetRepository
import com.wire.kalium.logic.data.asset.UploadedAssetId
import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.message.AssetContent
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.MessageEncryptionAlgorithm
import com.wire.kalium.logic.data.sync.SlowSyncRepository
import com.wire.kalium.logic.data.sync.SlowSyncStatus
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.feature.message.MessageSender
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.flatMap
import com.wire.kalium.logic.functional.fold
import com.wire.kalium.logic.functional.onFailure
import com.wire.kalium.logic.kaliumLogger
import com.wire.kalium.logic.util.fileExtension
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.datetime.Clock
import okio.Path

fun interface SendBrokenAssetMessageUseCase {
    /**
     * Function that can be used to send manipulated asset messages to a given conversation. Manipulation can be either a wrong
     * checksum or a changed otrKey. This debug function can be used to test correct client behaviour. It should not be used by
     * clients itself.
     *
     * In contrast to SendAssetMessageUseCase this debug function does not persist the message.
     *
     * @param conversationId the id of the conversation where the asset wants to be sent
     * @param assetDataPath the raw data of the asset to be uploaded to the backend and sent to the given conversation
     * @param assetDataSize the size of the original asset file
     * @param assetName the name of the original asset file
     * @param assetMimeType the type of the asset file
     * @param brokenState the type of manipulation
     * @return an [SendBrokenAssetMessageResult] containing a [CoreFailure] in case anything goes wrong and [Unit] in case everything succeeds
     */
    @Suppress("LongParameterList")
    suspend operator fun invoke(
        conversationId: ConversationId,
        assetDataPath: Path,
        assetDataSize: Long,
        assetName: String,
        assetMimeType: String,
        brokenState: BrokenState
    ): SendBrokenAssetMessageResult
}

@Suppress("LongParameterList", "MaxLineLength")
internal class SendBrokenAssetMessageUseCaseImpl(
    private val clientRepository: ClientRepository,
    private val assetDataSource: AssetRepository,
    private val userRepository: UserRepository,
    private val slowSyncRepository: SlowSyncRepository,
    private val messageSender: MessageSender
) : SendBrokenAssetMessageUseCase {
    private lateinit var currentAssetMessageContent: AssetMessageMetadata
    override suspend fun invoke(
        conversationId: ConversationId,
        assetDataPath: Path,
        assetDataSize: Long,
        assetName: String,
        assetMimeType: String,
        brokenState: BrokenState
    ): SendBrokenAssetMessageResult {
        slowSyncRepository.slowSyncStatus.first {
            it is SlowSyncStatus.Complete
        }

        // Generate the otr asymmetric key that will be used to encrypt the data
        val otrKey = generateRandomAES256Key()
        currentAssetMessageContent = AssetMessageMetadata(
            conversationId = conversationId,
            mimeType = assetMimeType,
            assetDataPath = assetDataPath,
            assetDataSize = assetDataSize,
            assetName = assetName,
            assetWidth = 0,
            assetHeight = 0,
            otrKey = otrKey,
            sha256Key = SHA256Key(ByteArray(DEFAULT_BYTE_ARRAY_SIZE)), // Sha256 will be replaced with right values after asset upload
            assetId = UploadedAssetId("", ""), // Asset ID will be replaced with right value after asset upload
        )
        lateinit var message: Message.Regular

        return clientRepository.currentClientId().flatMap { currentClientId ->
            // Get my current user
            val selfUser = userRepository.observeSelfUser().firstOrNull()

            if (selfUser == null) {
                kaliumLogger.e("There was an error obtaining the self user object :(")
                return@flatMap Either.Left(StorageFailure.DataNotFound)
            }

            // Create a unique message ID
            val generatedMessageUuid = uuid4().toString()

            message = Message.Regular(
                id = generatedMessageUuid,
                content = MessageContent.Asset(
                    provideAssetMessageContent(
                        currentAssetMessageContent,
                        Message.UploadStatus.UPLOAD_IN_PROGRESS,
                        brokenState
                    )
                ),
                conversationId = conversationId,
                date = Clock.System.now().toString(),
                senderUserId = selfUser.id,
                senderClientId = currentClientId,
                status = Message.Status.PENDING,
                editStatus = Message.EditStatus.NotEdited
            )

            uploadAssetAndUpdateMessage(message, conversationId, brokenState)
        }.fold({
            SendBrokenAssetMessageResult.Failure(it)
        }, { SendBrokenAssetMessageResult.Success })
    }

    @Suppress("MaxLineLength")
    private suspend fun uploadAssetAndUpdateMessage(message: Message.Regular, conversationId: ConversationId, brokenState: BrokenState): Either<CoreFailure, Unit> =
        // The assetDataSource will encrypt the data with the provided otrKey and upload it if successful
        assetDataSource.uploadAndPersistPrivateAsset(
            currentAssetMessageContent.mimeType,
            currentAssetMessageContent.assetDataPath,
            currentAssetMessageContent.otrKey,
            currentAssetMessageContent.assetName.fileExtension()
        ).flatMap { (assetId, sha256) ->
            // We update the message with the remote data (assetId & sha256 key) obtained by the successful asset upload
            currentAssetMessageContent = currentAssetMessageContent.copy(sha256Key = sha256, assetId = assetId)
            val updatedMessage = message.copy(
                // We update the upload status to UPLOADED as the upload succeeded
                content = MessageContent.Asset(
                    provideAssetMessageContent(currentAssetMessageContent, Message.UploadStatus.UPLOADED, brokenState)
                )
            )
            prepareAndSendAssetMessage(updatedMessage)
        }.flatMap {
            Either.Right(Unit)
        }.onFailure {
            Either.Left(it)
        }

    @Suppress("LongParameterList")
    private suspend fun prepareAndSendAssetMessage(
        message: Message.Regular
    ): Either<CoreFailure, Unit> =
        messageSender.sendMessage(message).onFailure {
            kaliumLogger.e("There was an error when trying to send the asset on the conversation")
        }

    @Suppress("LongParameterList", "MaxLineLength")
    private fun provideAssetMessageContent(
        assetMessageMetadata: AssetMessageMetadata,
        uploadStatus: Message.UploadStatus,
        brokenState: BrokenState,
    ): AssetContent {
        with(assetMessageMetadata) {
            val manipulatedSha256KeyData = if (brokenState.invalidHash) {
                ByteArray(DEFAULT_BYTE_ARRAY_SIZE)
            } else if (brokenState.otherHash) {
                SHA256Key(ByteArray(DEFAULT_BYTE_ARRAY_SIZE)).data
            } else {
                sha256Key.data
            }
            return AssetContent(
                sizeInBytes = assetDataSize,
                name = assetName,
                mimeType = mimeType,
                metadata = null,
                remoteData = AssetContent.RemoteData(
                    otrKey = if (brokenState.otherAlgorithm) otrKey.data + 1 else otrKey.data,
                    sha256 = manipulatedSha256KeyData,
                    assetId = assetId.key,
                    encryptionAlgorithm = MessageEncryptionAlgorithm.AES_CBC,
                    assetDomain = assetId.domain,
                    assetToken = assetId.assetToken
                ),
                downloadStatus = Message.DownloadStatus.SAVED_EXTERNALLY,
                uploadStatus = uploadStatus
            )
        }
    }

    private companion object {
        const val DEFAULT_BYTE_ARRAY_SIZE = 16
    }
}

sealed class SendBrokenAssetMessageResult {
    object Success : SendBrokenAssetMessageResult()
    class Failure(val coreFailure: CoreFailure) : SendBrokenAssetMessageResult()
}

private data class AssetMessageMetadata(
    val conversationId: ConversationId,
    val mimeType: String,
    val assetId: UploadedAssetId,
    val assetDataPath: Path,
    val assetDataSize: Long,
    val assetName: String,
    val assetWidth: Int?,
    val assetHeight: Int?,
    val otrKey: AES256Key,
    val sha256Key: SHA256Key
)

data class BrokenState(
    val invalidHash: Boolean,
    val otherHash: Boolean,
    val otherAlgorithm: Boolean
)
