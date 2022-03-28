package com.wire.kalium.logic.sync

import com.wire.kalium.logic.CoreLogic
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.feature.UserSessionScope
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlin.reflect.KClass

actual class WorkScheduler(private val coreLogic: CoreLogic, private val userId: UserId) {

    actual fun schedule(
        work: KClass<out UserSessionWorker>,
        name: String
    ) {
        GlobalScope.launch {
            val constructor = work.java.getDeclaredConstructor(UserSessionScope::class.java)
            val worker = constructor.newInstance(coreLogic.getSessionScope(userId)) as UserSessionWorker
            worker.doWork()
        }
    }

}
