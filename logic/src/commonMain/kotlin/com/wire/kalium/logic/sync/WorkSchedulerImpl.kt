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

package com.wire.kalium.logic.sync

import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.feature.message.MessageSendingScheduler
import com.wire.kalium.logic.sync.periodic.UpdateApiVersionsScheduler
import io.mockative.Mockable

interface GlobalWorkScheduler : UpdateApiVersionsScheduler

@Mockable
interface UserSessionWorkScheduler : MessageSendingScheduler {
    val userId: UserId
}

internal expect class GlobalWorkSchedulerImpl : GlobalWorkScheduler
internal expect class UserSessionWorkSchedulerImpl : UserSessionWorkScheduler {
    override val userId: UserId
    override fun scheduleSendingOfPendingMessages()
    override fun cancelScheduledSendingOfPendingMessages()
}
