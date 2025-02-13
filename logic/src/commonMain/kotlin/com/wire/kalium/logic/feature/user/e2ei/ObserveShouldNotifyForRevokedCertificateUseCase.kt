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
package com.wire.kalium.logic.feature.user.e2ei

import com.wire.kalium.logic.configuration.UserConfigRepository
import com.wire.kalium.common.functional.fold
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf

/**
 * Use case that observes if the user should be notified about revoked E2ei certificate.
 */
interface ObserveShouldNotifyForRevokedCertificateUseCase {
    suspend operator fun invoke(): Flow<Boolean>
}

internal class ObserveShouldNotifyForRevokedCertificateUseCaseImpl(
    private val userConfigRepository: UserConfigRepository
) : ObserveShouldNotifyForRevokedCertificateUseCase {
    @OptIn(ExperimentalCoroutinesApi::class)
    override suspend operator fun invoke(): Flow<Boolean> =
        userConfigRepository.observeShouldNotifyForRevokedCertificate().flatMapLatest {
            it.fold(
                { flowOf(false) },
                { shouldNotify ->
                    flowOf(shouldNotify)
                }
            )
        }
}
