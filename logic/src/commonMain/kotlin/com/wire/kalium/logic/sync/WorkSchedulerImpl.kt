package com.wire.kalium.logic.sync

import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.feature.message.MessageSendingScheduler
import com.wire.kalium.logic.sync.full.SlowSyncScheduler
import com.wire.kalium.logic.sync.periodic.UpdateApiVersionsScheduler

interface GlobalWorkScheduler : UpdateApiVersionsScheduler
interface UserSessionWorkScheduler : MessageSendingScheduler, SlowSyncScheduler {
    val userId: UserId
}

internal expect class GlobalWorkSchedulerImpl : GlobalWorkScheduler
internal expect class UserSessionWorkSchedulerImpl : UserSessionWorkScheduler
