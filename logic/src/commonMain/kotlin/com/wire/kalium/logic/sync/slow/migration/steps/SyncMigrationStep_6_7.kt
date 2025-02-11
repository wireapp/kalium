/*
 * Wire
 * Copyright (C) 2024 Wire Swiss GmbH
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
package com.wire.kalium.logic.sync.slow.migration.steps

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.id.SelfTeamIdProvider
import com.wire.kalium.logic.data.user.AccountRepository
import com.wire.kalium.logic.data.user.UserAvailabilityStatus
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.flatMap

@Suppress("ClassNaming", "MagicNumber")
internal class SyncMigrationStep_6_7(
    private val accountRepository: Lazy<AccountRepository>,
    private val selfTeamIdProvider: SelfTeamIdProvider
) : SyncMigrationStep {

    override val version: Int = 7
    override suspend fun invoke(): Either<CoreFailure, Unit> =
        selfTeamIdProvider().flatMap {
            if (it?.value.isNullOrBlank()) {
                accountRepository.value.updateSelfUserAvailabilityStatus(UserAvailabilityStatus.NONE)
            } else {
                Either.Right(Unit)
            }
        }
}
