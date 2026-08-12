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

package com.wire.kalium.cryptography.utils

/**
 * The Web Crypto API exposes a fixed algorithm set rather than a pluggable provider registry,
 * so there is nothing to enumerate here.
 */
actual fun cryptoServiceReport(): CryptoServiceReport = CryptoServiceReport(
    strongSecureRandom = CryptoServiceStatus.Failed(NO_PROVIDER_REGISTRY),
    aesKeyGenerator = CryptoServiceStatus.Failed(NO_PROVIDER_REGISTRY),
    secureRandomAlgorithms = emptyList(),
    keyGeneratorAlgorithms = emptyList(),
)

private const val NO_PROVIDER_REGISTRY = "Web Crypto exposes no security provider registry"