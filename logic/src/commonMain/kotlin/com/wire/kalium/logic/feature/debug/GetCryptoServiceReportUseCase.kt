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

import com.wire.kalium.cryptography.utils.CryptoServiceReport
import com.wire.kalium.cryptography.utils.CryptoServiceStatus
import com.wire.kalium.cryptography.utils.cryptoServiceReport
import com.wire.kalium.util.DebugKaliumApi
import com.wire.kalium.util.KaliumDispatcher
import kotlinx.coroutines.withContext

@DebugKaliumApi("Debug-only API for inspecting the platform randomness and key generation services.")
public interface GetCryptoServiceReportUseCase {
    public suspend operator fun invoke(): CryptoServiceInfo
}

@OptIn(DebugKaliumApi::class)
internal class GetCryptoServiceReportUseCaseImpl(
    private val dispatcher: KaliumDispatcher,
) : GetCryptoServiceReportUseCase {

    override suspend fun invoke(): CryptoServiceInfo = withContext(dispatcher.io) {
        cryptoServiceReport().toCryptoServiceInfo()
    }
}

@OptIn(DebugKaliumApi::class)
private fun CryptoServiceReport.toCryptoServiceInfo(): CryptoServiceInfo = CryptoServiceInfo(
    strongSecureRandom = strongSecureRandom.toCryptoServiceState(),
    aesKeyGenerator = aesKeyGenerator.toCryptoServiceState(),
    secureRandomAlgorithms = secureRandomAlgorithms,
    keyGeneratorAlgorithms = keyGeneratorAlgorithms,
)

@OptIn(DebugKaliumApi::class)
private fun CryptoServiceStatus.toCryptoServiceState(): CryptoServiceState = when (this) {
    is CryptoServiceStatus.Resolved -> CryptoServiceState.Resolved(
        algorithm = algorithm,
        providerName = providerName,
        providerVersion = providerVersion,
    )

    is CryptoServiceStatus.Failed -> CryptoServiceState.Unavailable(reason = reason)
}
