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
 * Which security provider serves each cryptographic lookup in this module.
 *
 * Which implementation backs an algorithm is decided at runtime by walking the installed security
 * providers, so it varies per device, per OEM and per OS version. This performs the same lookups the call
 * sites perform, from the same file and with the same algorithm constants, and reads the provider off what
 * comes back.
 *
 * Only for the security providers debug screen. Performs lookups and nothing else: no key is persisted and
 * no crypto state is mutated.
 *
 * Empty on platforms that have no security provider registry.
 */
expect fun cryptoServices(): List<CryptoServiceInfo>
