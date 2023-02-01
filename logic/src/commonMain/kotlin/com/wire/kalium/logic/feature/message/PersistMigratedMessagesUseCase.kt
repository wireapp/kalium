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
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.MigratedMessage
import com.wire.kalium.logic.data.message.PlainMessageBlob
import com.wire.kalium.logic.data.message.ProtoContent
import com.wire.kalium.logic.data.message.ProtoContentMapper
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.kaliumLogger
import com.wire.kalium.logic.sync.receiver.conversation.message.ApplicationMessageHandler

/**
 * Persist migrated messages from old datasource
 */
fun interface PersistMigratedMessagesUseCase {
    suspend operator fun invoke(messages: List<MigratedMessage>): Either<CoreFailure, Unit>
}

internal class PersistMigratedMessagesUseCaseImpl(
    private val applicationMessageHandler: ApplicationMessageHandler,
    private val protoContentMapper: ProtoContentMapper,
) : PersistMigratedMessagesUseCase {
    override suspend fun invoke(messages: List<MigratedMessage>): Either<CoreFailure, Unit> {
        messages.filter { it.encryptedProto != null }
            .map { migratedMessage ->
                migratedMessage to protoContentMapper.decodeFromProtobuf(PlainMessageBlob(migratedMessage.encryptedProto!!))
            }.forEach { (message, proto) ->
                when (proto) {
                    is ProtoContent.ExternalMessageInstructions -> kaliumLogger.w("Ignoring external message")
                    is ProtoContent.Readable -> {
                        val updatedProto =
                            if (message.assetSize != null && message.assetName != null && proto.messageContent is MessageContent.Asset) {
                                proto.copy(
                                    messageContent = proto.messageContent.copy(
                                        value = proto.messageContent.value.copy(
                                            name = message.assetName,
                                            sizeInBytes = message.assetSize.toLong()
                                        )
                                    )
                                )
                            } else proto
                        applicationMessageHandler.handleContent(
                            message.conversationId,
                            message.timestampIso,
                            message.senderUserId,
                            message.senderClientId,
                            updatedProto
                        )
                    }
                }
            }
        return Either.Right(Unit)
    }
}
