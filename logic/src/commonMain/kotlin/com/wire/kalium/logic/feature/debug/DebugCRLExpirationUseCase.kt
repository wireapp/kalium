/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
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
package com.wire.kalium.logic.feature.debug

import com.wire.kalium.logic.data.e2ei.CertificateRevocationListRepository
import kotlinx.coroutines.flow.Flow

/**
 * Observes whether debug CRL expiration override is enabled.
 */
public interface ObserveDebugCRLExpirationAfterOneMinuteUseCase {
    public operator fun invoke(): Flow<Boolean>
}

/**
 * Enables or disables debug CRL expiration override.
 *
 * When enabled, stored CRL expiration dates are cleared so CRLs can be refetched using the debug expiration interval.
 */
public interface SetDebugCRLExpirationAfterOneMinuteUseCase {
    public suspend operator fun invoke(enabled: Boolean)
}

internal class ObserveDebugCRLExpirationAfterOneMinuteUseCaseImpl(
    private val certificateRevocationListRepository: CertificateRevocationListRepository
) : ObserveDebugCRLExpirationAfterOneMinuteUseCase {
    override fun invoke(): Flow<Boolean> =
        certificateRevocationListRepository.observeShouldForceCRLExpirationAfterOneMinute()
}

internal class SetDebugCRLExpirationAfterOneMinuteUseCaseImpl(
    private val certificateRevocationListRepository: CertificateRevocationListRepository
) : SetDebugCRLExpirationAfterOneMinuteUseCase {
    override suspend fun invoke(enabled: Boolean) {
        certificateRevocationListRepository.setShouldForceCRLExpirationAfterOneMinute(enabled)
        if (enabled) {
            certificateRevocationListRepository.clearCRLExpirationDates()
        }
    }
}
