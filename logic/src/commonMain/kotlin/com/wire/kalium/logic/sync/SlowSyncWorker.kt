package com.wire.kalium.logic.sync

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.feature.UserSessionScope
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.suspending
import com.wire.kalium.logic.kaliumLogger

class SlowSyncWorker(
    private val userSessionScope: UserSessionScope
) : DefaultWorker() {

    override suspend fun doWork(): Result = suspending {

        val result = userSessionScope.users.syncSelfUser()
            .flatMap { userSessionScope.conversations.syncConversations() }
            .flatMap { userSessionScope.connection.syncConnections() }
            .flatMap { userSessionScope.team.syncSelfTeamUseCase() }
            .flatMap { userSessionScope.users.syncContacts() }
            .onSuccess { userSessionScope.syncManager.completeSlowSync() }

        when (result) {
            is Either.Left -> {
                val failure = result.value
                kaliumLogger.e("SLOW SYNC FAILED $failure")
                (failure as? CoreFailure.Unknown)?.let {
                    it.rootCause?.printStackTrace()
                }
                return@suspending Result.Failure
            }
            is Either.Right -> {
                kaliumLogger.i("SLOW SYNC SUCCESS $result")
                return@suspending Result.Success
            }
        }
    }

    companion object {
        const val name: String = "SLOW_SYNC"
    }

}
