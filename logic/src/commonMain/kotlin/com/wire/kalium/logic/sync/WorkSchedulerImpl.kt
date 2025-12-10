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
@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package com.wire.kalium.logic.sync

import com.wire.kalium.logic.GlobalKaliumScope
import com.wire.kalium.logic.feature.UserSessionScope
import com.wire.kalium.logic.feature.message.MessageSendingScheduler
import com.wire.kalium.logic.sync.periodic.UpdateApiVersionsScheduler
import com.wire.kalium.logic.sync.periodic.UserConfigSyncScheduler
import io.mockative.Mockable

interface WorkSchedulerProvider {
    fun globalWorkScheduler(scope: GlobalKaliumScope): GlobalWorkScheduler
    fun userSessionWorkScheduler(scope: UserSessionScope): UserSessionWorkScheduler
}

interface GlobalWorkScheduler : UpdateApiVersionsScheduler {
    val scope: GlobalKaliumScope
}

@Mockable
interface UserSessionWorkScheduler : MessageSendingScheduler, UserConfigSyncScheduler {
    val scope: UserSessionScope
}

internal expect class WorkSchedulerProviderImpl : WorkSchedulerProvider {
    override fun globalWorkScheduler(scope: GlobalKaliumScope): GlobalWorkScheduler
    override fun userSessionWorkScheduler(scope: UserSessionScope): UserSessionWorkScheduler
}
internal expect class GlobalWorkSchedulerImpl : GlobalWorkScheduler {
    override val scope: GlobalKaliumScope
    override fun schedulePeriodicApiVersionUpdate()
    override fun scheduleImmediateApiVersionUpdate()
}
internal expect class UserSessionWorkSchedulerImpl : UserSessionWorkScheduler {
    override val scope: UserSessionScope
    override fun scheduleSendingOfPendingMessages()
    override fun cancelScheduledSendingOfPendingMessages()
    override fun schedulePeriodicUserConfigSync()
    override fun resetBackoffForPeriodicUserConfigSync()
}
