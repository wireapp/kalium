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

import com.wire.kalium.cryptography.utils.CryptoServiceRegistry
import com.wire.kalium.cryptography.utils.probeCryptoServices
import com.wire.kalium.logic.util.SecureRandom
import com.wire.kalium.util.KaliumDispatcher
import kotlinx.coroutines.withContext
import com.wire.kalium.util.DebugKaliumApi
import com.wire.kalium.cryptography.utils.CryptoUsage as CryptographyCryptoUsage

@DebugKaliumApi("Debug-only API for inspecting which security provider served each of kalium's crypto call sites.")
public interface GetCryptoServiceReportUseCase {
    /**
     * Reports which security provider served each cryptographic call site in kalium, as observed when it
     * ran. Only used in the debug menu.
     *
     * Call sites that have not run yet are probed by performing the very same lookup they perform, so
     * every entry is a provider the platform actually handed back rather than one assumed on its behalf.
     */
    public suspend operator fun invoke(): List<CryptoServiceUsage>
}

@OptIn(DebugKaliumApi::class)
internal class GetCryptoServiceReportUseCaseImpl(
    private val dispatcher: KaliumDispatcher,
) : GetCryptoServiceReportUseCase {

    // Probing resolves a strong secure random, which can block while the platform gathers entropy.
    override suspend fun invoke(): List<CryptoServiceUsage> = withContext(dispatcher.io) {
        probeCryptoServices()
        probeDatabaseSecretRandom()
        CryptoServiceRegistry.recorded().map { (usage, record) ->
            CryptoServiceUsage(
                usage = usage.toLogicUsage(),
                lookup = record.lookup,
                algorithm = record.algorithm,
                providerName = record.providerName,
                providerVersion = record.providerVersion,
            )
        }
    }

    /**
     * The database secret is generated through kalium's own [SecureRandom], not the cryptography module,
     * so probe it here. Pulling a single byte is enough to resolve the provider and is thrown away.
     */
    private fun probeDatabaseSecretRandom() {
        SecureRandom().nextBytes(1)
    }
}

@OptIn(DebugKaliumApi::class)
private fun CryptographyCryptoUsage.toLogicUsage(): CryptoUsage = when (this) {
    CryptographyCryptoUsage.ASSET_ENCRYPTION_IV -> CryptoUsage.ASSET_ENCRYPTION_IV
    CryptographyCryptoUsage.ASSET_KEY -> CryptoUsage.ASSET_KEY
    CryptographyCryptoUsage.ASSET_CIPHER -> CryptoUsage.ASSET_CIPHER
    CryptographyCryptoUsage.DATABASE_SECRET -> CryptoUsage.DATABASE_SECRET
}
