package com.wire.kalium.logic.sync

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.ProteusFailure
import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.feature.UserSessionScope
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.suspending
import com.wire.kalium.logic.kaliumLogger

class SlowSyncWorker(
    userSessionScope: UserSessionScope
) : UserSessionWorker(userSessionScope) {

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


                when(failure) {
                    CoreFailure.MissingClientRegistration -> kaliumLogger.e("SLOW SYNC FAILED MissingClientRegistration $failure")
                    is CoreFailure.Unknown -> kaliumLogger.e("SLOW SYNC FAILED Unknown ${failure.rootCause}")
                    is CoreFailure.FeatureFailure ->kaliumLogger.e("SLOW SYNC FAILED FeatureFailure $failure")
                    NetworkFailure.NoNetworkConnection -> kaliumLogger.e("SLOW SYNC FAILED NoNetworkConnection $failure")
                    is NetworkFailure.ServerMiscommunication -> kaliumLogger.e("SLOW SYNC FAILED ServerMiscommunication ${failure.rootCause}")
                    is ProteusFailure ->kaliumLogger.e("SLOW SYNC FAILED ProteusFailure ${failure.rootCause}")
                    StorageFailure.DataNotFound -> kaliumLogger.e("SLOW SYNC FAILED DataNotFound $failure")
                    is StorageFailure.Generic -> kaliumLogger.e("SLOW SYNC FAILED Generic ${failure.rootCause}")
                }
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
