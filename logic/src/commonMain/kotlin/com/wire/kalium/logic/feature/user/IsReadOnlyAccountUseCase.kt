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

package com.wire.kalium.logic.feature.user

import com.wire.kalium.logic.data.session.SessionRepository
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.common.functional.fold
import com.wire.kalium.logic.kaliumLogger
import com.wire.kalium.util.KaliumDispatcher
import com.wire.kalium.util.KaliumDispatcherImpl
import kotlinx.coroutines.withContext

/**
 * Computes the managed_by property of the account to define if this is a read only account or not.
 */
interface IsReadOnlyAccountUseCase {
    suspend operator fun invoke(): Boolean
}

internal class IsReadOnlyAccountUseCaseImpl(
    private val selfUserId: UserId,
    private val sessionRepository: SessionRepository,
    private val dispatchers: KaliumDispatcher = KaliumDispatcherImpl
) : IsReadOnlyAccountUseCase {
    override suspend operator fun invoke(): Boolean = withContext(dispatchers.io) {
        sessionRepository.isAccountReadOnly(selfUserId).fold(
            {
                kaliumLogger.e("Error while computing if the account is read only, fallback to true $it")
                true
            },
            {
                // Only WIRE managed accounts are able to edit some information of the profile
                it
            }
        )
    }
}
