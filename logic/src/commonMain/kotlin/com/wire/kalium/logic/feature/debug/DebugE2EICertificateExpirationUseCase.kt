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
package com.wire.kalium.logic.feature.debug

import com.wire.kalium.logic.data.client.E2EIClientProvider
import kotlin.time.Duration.Companion.days

/**
 * Returns the currently configured debug E2EI certificate expiration time in seconds.
 *
 * If no debug override is configured for the current session, it returns the default
 * production TTL value.
 */
public interface GetDebugE2EICertificateExpirationUseCase {
    public suspend operator fun invoke(): Long
}

/**
 * Sets debug E2EI certificate expiration time in seconds for the current session.
 *
 * Values lower than the minimum supported expiration are clamped to that minimum.
 */
public interface SetDebugE2EICertificateExpirationUseCase {
    public suspend operator fun invoke(seconds: Long)
}

internal class GetDebugE2EICertificateExpirationUseCaseImpl(
    private val e2EIClientProvider: E2EIClientProvider
) : GetDebugE2EICertificateExpirationUseCase {
    override suspend fun invoke(): Long =
        e2EIClientProvider.getDebugCertificateExpirationOverride()
            ?.coerceAtLeast(MIN_DEBUG_E2EI_CERTIFICATE_EXPIRATION_SECONDS)
            ?: DEFAULT_E2EI_CERTIFICATE_EXPIRATION_SECONDS
}

internal class SetDebugE2EICertificateExpirationUseCaseImpl(
    private val e2EIClientProvider: E2EIClientProvider
) : SetDebugE2EICertificateExpirationUseCase {
    override suspend fun invoke(seconds: Long) {
        e2EIClientProvider.setDebugCertificateExpirationOverride(
            seconds.coerceAtLeast(MIN_DEBUG_E2EI_CERTIFICATE_EXPIRATION_SECONDS)
        )
    }
}

public const val MIN_DEBUG_E2EI_CERTIFICATE_EXPIRATION_SECONDS: Long = 360L
internal val DEFAULT_E2EI_CERTIFICATE_EXPIRATION_SECONDS = 90.days.inWholeSeconds
