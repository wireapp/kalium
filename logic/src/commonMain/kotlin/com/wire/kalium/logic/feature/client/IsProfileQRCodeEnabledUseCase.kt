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

import com.wire.kalium.logic.configuration.UserConfigRepository

/**
 * Use case to check if Simplified Connection Request feature is enabled.
 */
public interface IsProfileQRCodeEnabledUseCase {
    public suspend operator fun invoke(): Boolean
}

internal class IsProfileQRCodeEnabledUseCaseImpl(
    private val userConfigRepository: UserConfigRepository,
) : IsProfileQRCodeEnabledUseCase {
    override suspend fun invoke() = userConfigRepository.isProfileQRCodeEnabled()
}
