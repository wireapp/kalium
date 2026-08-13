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
 * Fills [CryptoServiceRegistry] for the call sites in this module that have not run yet.
 *
 * Most crypto paths only execute on a specific user action — asset encryption needs an upload, for
 * instance — which would leave the security providers debug screen almost empty. This runs the same
 * lookups the call sites run, by calling the very functions they call, so nothing here can claim a
 * provider that real usage would not have got. If a call site has already recorded, this simply records
 * the same result again.
 *
 * Only performs lookups. No key is persisted and no crypto state is mutated.
 *
 * `SecureRandom.getInstanceStrong()` can block while the platform gathers entropy, so call this off the
 * main thread.
 */
expect fun probeCryptoServices()
