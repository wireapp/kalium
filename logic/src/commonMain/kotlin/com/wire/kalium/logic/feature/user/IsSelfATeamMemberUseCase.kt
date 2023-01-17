package com.wire.kalium.logic.feature.user

import com.wire.kalium.logic.feature.SelfTeamIdProvider
import com.wire.kalium.logic.functional.fold
import com.wire.kalium.util.KaliumDispatcher
import com.wire.kalium.util.KaliumDispatcherImpl
import kotlinx.coroutines.withContext

/**
 * Return if self user is part of a team or noz
 */
class IsSelfATeamMemberUseCase internal constructor(
    private val selfTeamIdProvider: SelfTeamIdProvider,
    private val dispatchers: KaliumDispatcher = KaliumDispatcherImpl
) {
    suspend operator fun invoke(): Boolean = withContext(dispatchers.default) {
        selfTeamIdProvider().fold({ false }, {
            it != null
        })
    }
}
