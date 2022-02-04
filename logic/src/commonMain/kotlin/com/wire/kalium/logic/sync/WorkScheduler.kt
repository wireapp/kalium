package com.wire.kalium.logic.sync

import com.wire.kalium.logic.feature.auth.AuthSession
import kotlin.reflect.KClass

expect class WorkScheduler {

    fun schedule(work: KClass<out UserSessionWorker>, name: String)

}
