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

import com.wire.kalium.cryptography.utils.CryptoServiceInfo
import com.wire.kalium.cryptography.utils.cryptoServices
import com.wire.kalium.logic.util.SecureRandom
import com.wire.kalium.util.DebugKaliumApi
import com.wire.kalium.util.KaliumDispatcher
import kotlinx.coroutines.withContext

@DebugKaliumApi("Debug-only API for inspecting which security provider served each of kalium's crypto call sites.")
public interface GetCryptoServiceReportUseCase {
    /**
     * Reports which security provider serves each cryptographic lookup in kalium.
     *
     * Every entry is read off an instance the platform handed back, not assumed on a call site's behalf.
     * Which lookups those are varies per platform, so the list can be empty.
     */
    public suspend operator fun invoke(): List<CryptoServiceUsage>
}

@OptIn(DebugKaliumApi::class)
internal class GetCryptoServiceReportUseCaseImpl(
    private val dispatcher: KaliumDispatcher,
) : GetCryptoServiceReportUseCase {

    override suspend fun invoke(): List<CryptoServiceUsage> = withContext(dispatcher.io) {
        val services = cryptoServices() + listOfNotNull(SecureRandom().serviceInfo())
        services.map { it.toUsage() }
    }
}

/**
 * `core:cryptography` is an `implementation` dependency, so its types cannot appear in kalium's public API.
 */
@OptIn(DebugKaliumApi::class)
private fun CryptoServiceInfo.toUsage(): CryptoServiceUsage = CryptoServiceUsage(
    name = name,
    lookup = lookup,
    algorithm = algorithm,
    providerName = providerName,
    providerVersion = providerVersion,
)
