package com.wire.kalium.logic.feature.user

import com.wire.kalium.logic.feature.SelfTeamIdProvider
import com.wire.kalium.logic.functional.fold

fun interface IsSelfATeamMemberUseCase {
    suspend operator fun invoke(): Boolean
}

/**
 * Return if self user is part of a team or not
 */
internal class IsSelfATeamMemberUseCaseImpl internal constructor(
    private val selfTeamIdProvider: SelfTeamIdProvider
): IsSelfATeamMemberUseCase {
    override suspend operator fun invoke(): Boolean = selfTeamIdProvider().fold({ false }, {
        it != null
    })
}
