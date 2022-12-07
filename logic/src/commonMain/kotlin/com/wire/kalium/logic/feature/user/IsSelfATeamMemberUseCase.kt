package com.wire.kalium.logic.feature.user

import com.wire.kalium.logic.feature.SelfTeamIdProvider
import com.wire.kalium.logic.functional.fold

/**
 * Return if self user is part of a team or noz
 */
class IsSelfATeamMemberUseCase internal constructor(
    private val selfTeamIdProvider: SelfTeamIdProvider
) {
    suspend operator fun invoke(): Boolean = selfTeamIdProvider().fold({ false }, {
        it != null
    })
}
