/*
 * Wire
 * Copyright (C) 2023 Wire Swiss GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see http://www.gnu.org/licenses/.
 */

package com.wire.kalium.logic.feature.message

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.asset.AssetMapper
import com.wire.kalium.logic.data.id.toDao
import com.wire.kalium.logic.data.message.AssetContent.AssetMetadata.Audio
import com.wire.kalium.logic.data.message.AssetContent.AssetMetadata.Image
import com.wire.kalium.logic.data.message.AssetContent.AssetMetadata.Video
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.MigratedMessage
import com.wire.kalium.logic.data.message.PlainMessageBlob
import com.wire.kalium.logic.data.message.ProtoContent
import com.wire.kalium.logic.data.message.ProtoContentMapper
import com.wire.kalium.logic.data.message.mention.MessageMentionMapper
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.persistence.dao.MigrationDAO
import com.wire.kalium.persistence.dao.message.MessageEntity
import com.wire.kalium.persistence.dao.message.MessageEntityContent
import com.wire.kalium.util.KaliumDispatcherImpl
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant

/**
 * Persist migrated messages from old datasource
 */
fun interface PersistMigratedMessagesUseCase {
    suspend operator fun invoke(messages: List<MigratedMessage>, coroutineScope: CoroutineScope): Either<CoreFailure, Unit>
}

internal class PersistMigratedMessagesUseCaseImpl @OptIn(ExperimentalCoroutinesApi::class) constructor(
    private val selfUserId: UserId,
    private val migrationDAO: MigrationDAO,
    private val coroutineContext: CoroutineDispatcher = KaliumDispatcherImpl.default.limitedParallelism(2),
    private val protoContentMapper: ProtoContentMapper = MapperProvider.protoContentMapper(selfUserId),
    private val messageMentionMapper: MessageMentionMapper = MapperProvider.messageMentionMapper(selfUserId),
    private val assetMapper: AssetMapper = MapperProvider.assetMapper(),
) : PersistMigratedMessagesUseCase {

    @Suppress("ComplexMethod", "LongMethod")
    override suspend fun invoke(messages: List<MigratedMessage>, coroutineScope: CoroutineScope): Either<CoreFailure, Unit> {
        val protoMessages: MutableList<Pair<MigratedMessage, ProtoContent>> = mutableListOf()
        messages.filter { it.encryptedProto != null }.map { migratedMessage ->
            coroutineScope.launch(coroutineContext) {
                protoMessages.add(
                    migratedMessage to protoContentMapper.decodeFromProtobuf(
                        PlainMessageBlob(migratedMessage.encryptedProto!!)
                    )
                )
            }
        }.joinAll()
        val messageEntityList = protoMessages.mapNotNull { (migratedMessage, proto) ->
            when (proto) {
                is ProtoContent.ExternalMessageInstructions -> {
                    null
                }

                is ProtoContent.Readable -> {
                    val updatedProto =
                        if (migratedMessage.assetSize != null &&
                            migratedMessage.assetName != null &&
                            proto.messageContent is MessageContent.Asset
                        ) {
                            proto.copy(
                                messageContent = proto.messageContent.copy(
                                    value = proto.messageContent.value.copy(
                                        name = migratedMessage.assetName,
                                        sizeInBytes = migratedMessage.assetSize.toLong()
                                    )
                                )
                            )
                        } else proto

                    when (val protoContent = updatedProto.messageContent) {
                        is MessageContent.Regular -> {
                            val visibility = when (protoContent) {
                                is MessageContent.DeleteMessage -> MessageEntity.Visibility.HIDDEN
                                is MessageContent.TextEdited -> MessageEntity.Visibility.HIDDEN
                                is MessageContent.DeleteForMe -> MessageEntity.Visibility.HIDDEN
                                is MessageContent.Unknown -> if (protoContent.hidden) MessageEntity.Visibility.HIDDEN
                                else MessageEntity.Visibility.VISIBLE

                                is MessageContent.Text -> MessageEntity.Visibility.VISIBLE
                                is MessageContent.Calling -> MessageEntity.Visibility.VISIBLE
                                is MessageContent.Asset -> MessageEntity.Visibility.VISIBLE
                                is MessageContent.Knock -> MessageEntity.Visibility.VISIBLE
                                is MessageContent.RestrictedAsset -> MessageEntity.Visibility.VISIBLE
                                is MessageContent.FailedDecryption -> MessageEntity.Visibility.VISIBLE
                                is MessageContent.LastRead -> MessageEntity.Visibility.HIDDEN
                                is MessageContent.Cleared -> MessageEntity.Visibility.HIDDEN
                            }

                            MessageEntity.Regular(
                                id = updatedProto.messageUid,
                                // mapped from migratedMessage content to MessageEntityContent
                                content = protoContent.toMessageEntityContent(),
                                conversationId = migratedMessage.conversationId.toDao(),
                                date = Instant.fromEpochMilliseconds(migratedMessage.timestamp),
                                senderUserId = migratedMessage.senderUserId.toDao(),
                                senderClientId = migratedMessage.senderClientId.value,
                                status = MessageEntity.Status.SENT,
                                editStatus = MessageEntity.EditStatus.NotEdited, // TODO: get edit time form scala db
                                visibility = visibility,
                                senderName = null,
                                expectsReadConfirmation = updatedProto.expectsReadConfirmation
                            )
                        }

                        is MessageContent.Signaling -> {
                            null
                        }
                    }
                }
            }
        }.sortedBy { it.date.epochSeconds }
        migrationDAO.insertMessages(messageEntityList)
        return Either.Right(Unit)
    }

    @Suppress("ComplexMethod")
    private fun MessageContent.Regular.toMessageEntityContent(): MessageEntityContent.Regular = when (this) {
        is MessageContent.Text -> MessageEntityContent.Text(
            messageBody = this.value,
            mentions = this.mentions.map { messageMentionMapper.fromModelToDao(it) },
            quotedMessageId = this.quotedMessageReference?.quotedMessageId,
            isQuoteVerified = this.quotedMessageReference?.isVerified,
        )

        is MessageContent.Asset -> with(this.value) {
            val assetWidth = when (metadata) {
                is Image -> metadata.width
                is Video -> metadata.width
                else -> null
            }
            val assetHeight = when (metadata) {
                is Image -> metadata.height
                is Video -> metadata.height
                else -> null
            }
            val assetDurationMs = when (metadata) {
                is Video -> metadata.durationMs
                is Audio -> metadata.durationMs
                else -> null
            }
            MessageEntityContent.Asset(
                assetSizeInBytes = sizeInBytes,
                assetName = name,
                assetMimeType = mimeType,
                assetUploadStatus = assetMapper.fromUploadStatusToDaoModel(uploadStatus),
                assetDownloadStatus = assetMapper.fromDownloadStatusToDaoModel(downloadStatus),
                assetOtrKey = remoteData.otrKey,
                assetSha256Key = remoteData.sha256,
                assetId = remoteData.assetId,
                assetDomain = remoteData.assetDomain,
                assetToken = remoteData.assetToken,
                assetEncryptionAlgorithm = remoteData.encryptionAlgorithm?.name,
                assetWidth = assetWidth,
                assetHeight = assetHeight,
                assetDurationMs = assetDurationMs,
                assetNormalizedLoudness = if (metadata is Audio) metadata.normalizedLoudness else null,
            )
        }

        is MessageContent.RestrictedAsset -> MessageEntityContent.RestrictedAsset(this.mimeType, this.sizeInBytes, this.name)

        // We store the encoded data in case we decide to try to decrypt them again in the future
        is MessageContent.FailedDecryption -> MessageEntityContent.FailedDecryption(
            this.encodedData,
            this.isDecryptionResolved,
            this.senderUserId.toDao(),
            this.clientId?.value
        )

        // We store the unknown fields of the message in case we want to start handling them in the future
        is MessageContent.Unknown -> MessageEntityContent.Unknown(this.typeName, this.encodedData)

        // We don't care about the content of these messages as they are only used to perform other actions, i.e. update the content of a
        // previously stored message, delete the content of a previously stored message, etc... Therefore, we map their content to Unknown
        is MessageContent.Knock -> MessageEntityContent.Knock(hotKnock = this.hotKnock)
    }
}
