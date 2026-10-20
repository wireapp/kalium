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
package com.wire.kalium.logic.feature.service

import com.wire.kalium.logic.feature.featureConfig.ObserveIsAppsAllowedForUsageUseCase

/** Compatibility facade retaining only its own fresh extension and its constructor captures. */
public class ServiceScope internal constructor(private val api: LegacyServiceApi) {
    public val getServiceById: GetServiceByIdUseCase
        get() = api.serviceGetServiceById

    public val observeIsServiceMember: ObserveIsServiceMemberUseCase
        get() = api.serviceObserveIsServiceMember

    public val observeAllServices: ObserveAllServicesUseCase
        get() = api.serviceObserveAllServices

    public val syncServices: SyncServicesUseCase
        get() = api.serviceSyncServices

    public val searchServicesByName: SearchServicesByNameUseCase
        get() = api.serviceSearchServicesByName

    public val observeIsAppsAllowedForUsage: ObserveIsAppsAllowedForUsageUseCase
        get() = api.serviceObserveIsAppsAllowedForUsage
}
