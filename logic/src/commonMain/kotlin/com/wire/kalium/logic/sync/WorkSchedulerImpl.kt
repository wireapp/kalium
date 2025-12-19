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

import com.wire.kalium.logic.GlobalKaliumScope
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.feature.UserSessionScope
import com.wire.kalium.logic.feature.message.MessageSendingScheduler
import com.wire.kalium.logic.sync.periodic.UpdateApiVersionsScheduler
import com.wire.kalium.logic.sync.periodic.UserConfigSyncScheduler
import com.wire.kalium.logic.sync.receiver.asset.AudioNormalizedLoudnessScheduler
import io.mockative.Mockable

interface WorkSchedulerProvider {
    fun globalWorkScheduler(scope: GlobalKaliumScope): GlobalWorkScheduler
    fun userSessionWorkScheduler(scope: UserSessionScope): UserSessionWorkScheduler
}

interface GlobalWorkScheduler : UpdateApiVersionsScheduler {
    val scope: GlobalKaliumScope
}

@Mockable
interface UserSessionWorkScheduler : MessageSendingScheduler, UserConfigSyncScheduler, AudioNormalizedLoudnessScheduler {
    val scope: UserSessionScope

    /**
     * Schedules a retry attempt for failed message sync operations.
     * Typically enqueued with a delay (e.g., 30 seconds) and network connectivity constraint.
     *
     * @param userId The user whose messages failed to sync
     */
    fun scheduleMessageSyncRetry(userId: UserId)
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
    override fun scheduleBuildingAudioNormalizedLoudness(conversationId: ConversationId, messageId: String)
    override fun scheduleMessageSyncRetry(userId: UserId)
}
