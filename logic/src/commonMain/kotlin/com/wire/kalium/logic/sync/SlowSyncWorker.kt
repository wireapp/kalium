package com.wire.kalium.logic.sync

import com.wire.kalium.logic.feature.UserSessionScope
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.suspending

class SlowSyncWorker(userSessionScope: UserSessionScope) : UserSessionWorker(userSessionScope) {

    override suspend fun doWork(): Result = suspending {

        val result = userSessionScope.users.syncSelfUser()
            .flatMap { userSessionScope.conversations.syncConversations() }
            .onSuccess { userSessionScope.syncManager.completeSlowSync() }

        when ( result ) {
            is Either.Left -> {
                return@suspending Result.Failure
            }
            is Either.Right -> {
                return@suspending Result.Success
            }
        }
    }

    companion object {
        const val name: String = "SLOW_SYNC"
    }

}
