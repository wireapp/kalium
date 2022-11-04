package com.wire.kalium.logic.feature.message

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.message.MigratedMessage
import com.wire.kalium.logic.data.message.PersistMessageUseCase
import com.wire.kalium.logic.functional.Either

/**
 * Persist migrated messages from old datasource
 */
fun interface PersistMigratedMessagesUseCase {
    suspend operator fun invoke(messages: List<MigratedMessage>): Either<CoreFailure, Unit>
}

internal class PersistMigratedMessagesUseCaseImpl(val persistMessage: PersistMessageUseCase) : PersistMigratedMessagesUseCase {
    override suspend fun invoke(messages: List<MigratedMessage>): Either<CoreFailure, Unit> {
       // todo: map to message class and let the current usecase do the work
        return Either.Right(Unit)
    }
}
