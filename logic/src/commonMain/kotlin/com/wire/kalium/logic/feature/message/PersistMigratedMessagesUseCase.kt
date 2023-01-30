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
import com.wire.kalium.logic.data.id.toDao
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.MessageMapper
import com.wire.kalium.logic.data.message.MigratedMessage
import com.wire.kalium.logic.data.message.PlainMessageBlob
import com.wire.kalium.logic.data.message.ProtoContent
import com.wire.kalium.logic.data.message.ProtoContentMapper
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.persistence.dao.MigrationDAO
import com.wire.kalium.persistence.dao.message.MessageEntity
import com.wire.kalium.persistence.dao.message.MessageEntityContent
import com.wire.kalium.util.KaliumDispatcherImpl
import io.ktor.util.collections.ConcurrentMap
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
    suspend operator fun invoke(
        messages: List<MigratedMessage>,
        coroutineScope: CoroutineScope
    ): Either<CoreFailure, Unit>
}

internal class PersistMigratedMessagesUseCaseImpl @OptIn(ExperimentalCoroutinesApi::class) constructor(
    private val selfUserId: UserId,
    private val migrationDAO: MigrationDAO,
    private val coroutineContext: CoroutineDispatcher = KaliumDispatcherImpl.default.limitedParallelism(2),
    private val protoContentMapper: ProtoContentMapper = MapperProvider.protoContentMapper(selfUserId),
    private val messageMapper: MessageMapper = MapperProvider.messageMapper(selfUserId),
) : PersistMigratedMessagesUseCase {

    @Suppress("ComplexMethod", "LongMethod", "TooGenericExceptionCaught")
    override suspend fun invoke(messages: List<MigratedMessage>, coroutineScope: CoroutineScope): Either<CoreFailure, Unit> {
        val protoMessages: ConcurrentMap<MigratedMessage, ProtoContent> = ConcurrentMap()

        messages.filter { it.encryptedProto != null }.map { migratedMessage ->
            coroutineScope.launch(coroutineContext) {
                (try {
                    protoContentMapper.decodeFromProtobuf(
                        PlainMessageBlob(migratedMessage.encryptedProto!!)
                    )
                } catch (e: Exception) {
                    null
                })?.let {
                    protoMessages[migratedMessage] = it
                }
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
                            onRegularMessage(updatedProto.messageUid, migratedMessage, protoContent)
                        }

                        is MessageContent.Signaling -> {
                            onSignalingMessage(updatedProto.messageUid, migratedMessage, protoContent)
                        }
                    }
                }
            }
        }.sortedBy { it.date.epochSeconds }
        migrationDAO.insertMessages(messageEntityList)
        return Either.Right(Unit)
    }

    private fun onRegularMessage(
        messageId: String,
        migratedMessage: MigratedMessage,
        protoContent: MessageContent.Regular
    ) = MessageEntity.Regular(
        id = messageId,
        // mapped from migratedMessage content to MessageEntityContent
        content = messageMapper.toMessageEntityContent(protoContent),
        conversationId = migratedMessage.conversationId.toDao(),
        date = Instant.fromEpochMilliseconds(migratedMessage.timestamp),
        senderUserId = migratedMessage.senderUserId.toDao(),
        senderClientId = migratedMessage.senderClientId.value,
        status = MessageEntity.Status.SENT,
        editStatus = migratedMessage.editTime?.let {
            MessageEntity.EditStatus.Edited(Instant.fromEpochMilliseconds(it))
        } ?: MessageEntity.EditStatus.NotEdited,
        visibility = protoContent.visibility(),
        senderName = null,
        expectsReadConfirmation = false
    )

    private fun onSignalingMessage(messageId: String, migratedMessage: MigratedMessage, protoContent: MessageContent.FromProto) =
        if (protoContent is MessageContent.TextEdited) {
            MessageEntity.Regular(
                id = messageId,
                // mapped from migratedMessage content to MessageEntityContent
                content = MessageEntityContent.Text(
                    protoContent.newContent,
                    protoContent.newMentions.map {
                        MessageEntity.Mention(
                            it.start,
                            it.length,
                            it.userId.toDao()
                        )
                    }
                ),
                conversationId = migratedMessage.conversationId.toDao(),
                date = Instant.fromEpochMilliseconds(migratedMessage.timestamp),
                senderUserId = migratedMessage.senderUserId.toDao(),
                senderClientId = migratedMessage.senderClientId.value,
                status = MessageEntity.Status.SENT,
                editStatus = migratedMessage.editTime?.let {
                    MessageEntity.EditStatus.Edited(Instant.fromEpochMilliseconds(it))
                } ?: MessageEntity.EditStatus.NotEdited,
                visibility = MessageEntity.Visibility.VISIBLE,
                senderName = null,
                expectsReadConfirmation = false // no need to send read confirmation for messages migrated from old clients
            )
        } else {
            null
        }

    @Suppress("ComplexMethod")
    private fun MessageContent.FromProto.visibility() = when (this) {
        is MessageContent.DeleteMessage -> MessageEntity.Visibility.HIDDEN
        is MessageContent.TextEdited -> MessageEntity.Visibility.HIDDEN
        is MessageContent.DeleteForMe -> MessageEntity.Visibility.HIDDEN
        is MessageContent.Unknown -> if (this.hidden) MessageEntity.Visibility.HIDDEN
        else MessageEntity.Visibility.VISIBLE

        is MessageContent.Text -> MessageEntity.Visibility.VISIBLE
        is MessageContent.Calling -> MessageEntity.Visibility.VISIBLE
        is MessageContent.Asset -> MessageEntity.Visibility.VISIBLE
        is MessageContent.Knock -> MessageEntity.Visibility.VISIBLE
        is MessageContent.RestrictedAsset -> MessageEntity.Visibility.VISIBLE
        is MessageContent.FailedDecryption -> MessageEntity.Visibility.VISIBLE
        is MessageContent.LastRead -> MessageEntity.Visibility.HIDDEN
        is MessageContent.Cleared -> MessageEntity.Visibility.HIDDEN
        is MessageContent.Availability -> MessageEntity.Visibility.HIDDEN
        MessageContent.ClientAction -> MessageEntity.Visibility.HIDDEN
        MessageContent.Ignored -> MessageEntity.Visibility.HIDDEN
        is MessageContent.Reaction -> MessageEntity.Visibility.HIDDEN
        is MessageContent.Receipt -> MessageEntity.Visibility.HIDDEN
    }
}
