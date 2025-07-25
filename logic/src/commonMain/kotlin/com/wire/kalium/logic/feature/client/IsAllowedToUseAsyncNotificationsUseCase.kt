/*
 * Wire
 * Copyright (C) 2025 Wire Swiss GmbH
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
package com.wire.kalium.logic.feature.client

import io.mockative.Mockable

/**
 * This use case is responsible for determining if the client is allowed to use async notifications.
 * This by build feature flag and backend current API version.
 */
@Mockable
interface IsAllowedToUseAsyncNotificationsUseCase {
    operator fun invoke(): Boolean
}

internal class IsAllowedToUseAsyncNotificationsUseCaseImpl(
    private val isAllowedByFeatureFlag: Boolean,
    private val isAllowedByCurrentBackendVersionProvider: () -> Boolean
) : IsAllowedToUseAsyncNotificationsUseCase {
    override fun invoke(): Boolean = isAllowedByFeatureFlag && isAllowedByCurrentBackendVersionProvider.invoke()
}
