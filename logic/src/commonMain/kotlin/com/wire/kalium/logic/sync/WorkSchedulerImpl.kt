package com.wire.kalium.logic.sync

import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.feature.message.MessageSendingScheduler

interface GlobalWorkScheduler : UpdateApiVersionsScheduler
interface UserSessionWorkScheduler : MessageSendingScheduler, SlowSyncScheduler {
    val userId: UserId
}

internal expect class GlobalWorkSchedulerImpl: GlobalWorkScheduler
internal expect class UserSessionWorkSchedulerImpl: UserSessionWorkScheduler
