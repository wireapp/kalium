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

/**
 * Use case that reports whether the backend-driven "prevent adminless groups" policy
 * is enabled for the current user.
 *
 * When enabled, clients are expected to block actions that would leave a group conversation
 * without any admins — for example, the sole remaining admin attempting to leave the group.
 * Callers should consult this flag before initiating such flows and present the user with
 * alternative options (promote another member, delete the group) when it returns `true`.
 *
 * The value is sourced from [UserConfigRepository] and reflects the most recently synced
 * server feature configuration.
 *
 * @return `true` if adminless-group prevention is enabled for the current user, `false` otherwise.
 */
public interface IsPreventAdminlessGroupsEnabledUseCase {
    public suspend operator fun invoke(): Boolean
}

internal class IsPreventAdminlessGroupsEnabledUseCaseImpl(
    private val userConfigRepository: UserConfigRepository
) : IsPreventAdminlessGroupsEnabledUseCase {

    override suspend operator fun invoke(): Boolean =
        userConfigRepository.isPreventAdminlessGroupsEnabled()
}
