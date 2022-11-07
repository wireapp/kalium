package com.wire.kalium.logic.feature.message

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.message.MigratedMessage
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.fold
import com.wire.kalium.logic.kaliumLogger
import com.wire.kalium.logic.sync.receiver.conversation.message.ApplicationMessageHandler
import com.wire.kalium.logic.sync.receiver.conversation.message.MessageUnpackResult
import com.wire.kalium.logic.sync.receiver.conversation.message.ProteusMessageUnpacker

/**
 * Persist migrated messages from old datasource
 */
fun interface PersistMigratedMessagesUseCase {
    suspend operator fun invoke(messages: List<MigratedMessage>): Either<CoreFailure, Unit>
}

internal class PersistMigratedMessagesUseCaseImpl(
    private val applicationMessageHandler: ApplicationMessageHandler,
    private val proteusMessageUnpacker: ProteusMessageUnpacker,
) : PersistMigratedMessagesUseCase {
    override suspend fun invoke(messages: List<MigratedMessage>): Either<CoreFailure, Unit> {
        messages.map { migratedMessage ->
            proteusMessageUnpacker.unpackMigratedProteusMessage(migratedMessage)
                .fold({
                    kaliumLogger.d("Ignoring signal message")
                }, { unpackResult ->
                    when (unpackResult) {
                        is MessageUnpackResult.ApplicationMessage -> applicationMessageHandler.handleContent(
                            migratedMessage.conversationId,
                            migratedMessage.timestampIso,
                            migratedMessage.senderUserId,
                            migratedMessage.senderClientId,
                            unpackResult.content
                        )

                        MessageUnpackResult.HandshakeMessage -> kaliumLogger.d("Ignoring signal message")
                    }
                })
        }
        return Either.Right(Unit)
    }
}
