package com.wire.kalium.logic.sync

import com.wire.kalium.logic.CoreLogic
import com.wire.kalium.logic.feature.UserSessionScope
import com.wire.kalium.logic.feature.auth.AuthSession
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlin.reflect.KClass

actual class WorkScheduler(private val coreLogic: CoreLogic, private val session: AuthSession) {

    actual fun schedule(
        work: KClass<out UserSessionWorker>,
        name: String
    ) {
        GlobalScope.launch {
            val constructor = work.java.getDeclaredConstructor(UserSessionScope::class.java)
            val worker = constructor.newInstance(coreLogic.getSessionScope(session)) as UserSessionWorker
            worker.doWork()
        }
    }

}
