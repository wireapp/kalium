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

package com.wire.kalium.logic.feature.user

import com.wire.kalium.logic.configuration.UserConfigRepository
import com.wire.kalium.logic.featureFlags.FeatureSupport

/**
 * Use case that reports whether the meetings feature is enabled for the current user and current API version supports it.
 * @return `true` if meetings is enabled for the current user and supported by the current API version, `false` otherwise.
 */
public interface IsMeetingsEnabledUseCase {
    /** Whether meetings are enabled for this user: requires the user feature flag and API 17 or newer. */
    public suspend operator fun invoke(): Boolean

    /** Whether this user can share guest links: requires meetings enabled and API 19 or newer. */
    public suspend fun isGuestLinkSharingEnabled(): Boolean

    /** Whether API 19 guest links are supported, regardless of the user's meetings feature flag. */
    public suspend fun isGuestLinkSharingSupported(): Boolean
}

internal class IsMeetingsEnabledUseCaseImpl(
    private val userConfigRepository: UserConfigRepository,
    private val featureSupport: FeatureSupport,
) : IsMeetingsEnabledUseCase {
    override suspend operator fun invoke(): Boolean = userConfigRepository.isMeetingsEnabled() && featureSupport.isMeetingsSupported

    override suspend fun isGuestLinkSharingEnabled(): Boolean = invoke() && featureSupport.isMeetingsGuestLinksSupported

    override suspend fun isGuestLinkSharingSupported(): Boolean = featureSupport.isMeetingsGuestLinksSupported
}
