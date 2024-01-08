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
package com.wire.kalium.logic.feature.user

import com.wire.kalium.logic.configuration.UserConfigRepository

/**
 * Mark the Self Deleting Messages team setting change as notified on the app.
 * It needs to be called after notifying the user about that specific change, e.g. after showing a dialog, or a toast etc.
 */
interface MarkSelfDeletionStatusAsNotifiedUseCase {
    suspend operator fun invoke()
}

internal class MarkSelfDeletionStatusAsNotifiedUseCaseImpl(
    private val userConfigRepository: UserConfigRepository
) : MarkSelfDeletionStatusAsNotifiedUseCase {
    override suspend operator fun invoke() {
        userConfigRepository.markTeamSettingsSelfDeletingMessagesStatusAsNotified()
    }
}
