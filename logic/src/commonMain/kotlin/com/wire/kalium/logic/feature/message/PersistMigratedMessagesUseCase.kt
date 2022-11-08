package com.wire.kalium.logic.feature.message

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.message.MigratedMessage
import com.wire.kalium.logic.data.message.PlainMessageBlob
import com.wire.kalium.logic.data.message.ProtoContent
import com.wire.kalium.logic.data.message.ProtoContentMapper
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.kaliumLogger
import com.wire.kalium.logic.sync.receiver.conversation.message.ApplicationMessageHandler
import com.wire.kalium.logic.sync.receiver.conversation.message.ProteusMessageUnpacker

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
                        applicationMessageHandler.handleContent(
                            message.conversationId,
                            message.timestampIso,
                            message.senderUserId,
                            message.senderClientId,
                            proto
                        )
                    }
                }
            }
        return Either.Right(Unit)
    }
}
