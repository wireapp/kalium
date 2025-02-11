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
package com.wire.kalium.logic.feature.publicuser

import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.common.functional.fold
import com.wire.kalium.logic.kaliumLogger
import com.wire.kalium.util.KaliumDispatcher
import com.wire.kalium.util.KaliumDispatcherImpl
import kotlinx.coroutines.withContext

/**
 * Refresh users without metadata, only if necessary.
 */
interface RefreshUsersWithoutMetadataUseCase {
    suspend operator fun invoke()
}

internal class RefreshUsersWithoutMetadataUseCaseImpl(
    private val userRepository: UserRepository,
    private val dispatchers: KaliumDispatcher = KaliumDispatcherImpl
) : RefreshUsersWithoutMetadataUseCase {

    override suspend fun invoke() = withContext(dispatchers.io) {
        userRepository.syncUsersWithoutMetadata()
            .fold({
                kaliumLogger.w("Error while syncing users without metadata $it")
            }) {
                kaliumLogger.d("Finished syncing users without metadata")
            }
    }

}
