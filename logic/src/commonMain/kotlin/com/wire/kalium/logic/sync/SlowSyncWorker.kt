package com.wire.kalium.logic.sync

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.feature.UserSessionScope
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.suspending

class SlowSyncWorker(userSessionScope: UserSessionScope) : UserSessionWorker(userSessionScope) {

    override suspend fun doWork(): Result = suspending {

        val result = userSessionScope.users.syncSelfUser()
            .flatMap { userSessionScope.conversations.syncConversations() }
            .flatMap { userSessionScope.users.syncContacts() }
            .onSuccess { userSessionScope.syncManager.completeSlowSync() }

        when  (result) {
            is Either.Left -> {
                val failure = result.value
                //TODO Use multi-platform logging solution here
                println("SYNC FAILED $failure")
                (failure as? CoreFailure.Unknown)?.let {
                    it.rootCause?.printStackTrace()
                }
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
