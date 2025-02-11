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

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.logic.data.user.AccountRepository
import com.wire.kalium.common.functional.fold
import com.wire.kalium.util.KaliumDispatcher
import com.wire.kalium.util.KaliumDispatcherImpl
import kotlinx.coroutines.withContext

/**
 * Updates the display name of the current user.
 */
fun interface UpdateDisplayNameUseCase {
    /**
     * @param displayName The new display name.
     * @return The result of the operation [DisplayNameUpdateResult.Success] or a mapped [CoreFailure].
     */
    suspend operator fun invoke(displayName: String): DisplayNameUpdateResult
}

internal class UpdateDisplayNameUseCaseImpl(
    private val accountRepository: AccountRepository,
    private val dispatchers: KaliumDispatcher = KaliumDispatcherImpl
) : UpdateDisplayNameUseCase {
    override suspend fun invoke(displayName: String): DisplayNameUpdateResult = withContext(dispatchers.io) {
        accountRepository.updateSelfDisplayName(displayName)
            .fold(
                { DisplayNameUpdateResult.Failure(it) },
                { DisplayNameUpdateResult.Success }
            )
    }
}

sealed class DisplayNameUpdateResult {
    data object Success : DisplayNameUpdateResult()
    data class Failure(val coreFailure: CoreFailure) : DisplayNameUpdateResult()
}
