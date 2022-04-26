package com.wire.kalium.logic.feature.session

import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.data.session.SessionRepository
import com.wire.kalium.logic.functional.foldEither
import kotlinx.coroutines.flow.Flow

interface CurrentSessionFlowUseCase {
    operator fun invoke(): Flow<CurrentSessionResult>
}

class CurrentSessionFlowUseCaseImpl(private val sessionRepository: SessionRepository) : CurrentSessionFlowUseCase {
    override operator fun invoke(): Flow<CurrentSessionResult> =
        sessionRepository.currentSessionFlow()
            .foldEither(
                {
                    when (it) {
                        StorageFailure.DataNotFound -> CurrentSessionResult.Failure.SessionNotFound
                        is StorageFailure.Generic -> CurrentSessionResult.Failure.Generic(it)
                    }
                },
                { CurrentSessionResult.Success(it) }
            )
}
