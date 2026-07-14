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

package com.wire.kalium.logic.client

import com.wire.kalium.logic.CoreLogic
import com.wire.kalium.logic.featureFlags.KaliumConfigs
import com.wire.kalium.usernetwork.di.PlatformUserAuthenticatedNetworkProvider
import com.wire.kalium.usernetwork.di.UserAuthenticatedNetworkProvider

public actual class KaliumClient(
    rootPath: String,
    kaliumConfigs: KaliumConfigs,
    userAgent: String,
    useInMemoryStorage: Boolean = false,
    userAuthenticatedNetworkProvider: UserAuthenticatedNetworkProvider = PlatformUserAuthenticatedNetworkProvider(),
) {
    public actual val logic: CoreLogic = CoreLogic(
        rootPath,
        kaliumConfigs,
        userAgent,
        useInMemoryStorage,
        userAuthenticatedNetworkProvider,
    )
}
