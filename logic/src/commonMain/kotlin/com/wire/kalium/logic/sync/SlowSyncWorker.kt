package com.wire.kalium.logic.sync

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.feature.UserSessionScope
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.flatMap
import com.wire.kalium.logic.functional.onFailure
import com.wire.kalium.logic.functional.onSuccess
import com.wire.kalium.logic.kaliumLogger

class SlowSyncWorker(
    private val userSessionScope: UserSessionScope
) : DefaultWorker {

    // Any exception here is really unexpected, and we should log and figure out where it came from
    @Suppress("TooGenericExceptionCaught")
    override suspend fun doWork(): Result {
        kaliumLogger.d("Sync: Starting SlowSync")

        val result = try {
            // todo : to move the feature configs call outside the sync
            userSessionScope.syncFeatureConfigsUseCase()
            userSessionScope.users.syncSelfUser()
                .flatMap { userSessionScope.conversations.syncConversations() }
                .flatMap { userSessionScope.connection.syncConnections() }
                .flatMap { userSessionScope.team.syncSelfTeamUseCase() }
                .flatMap { userSessionScope.users.syncContacts() }
                .onSuccess { userSessionScope.syncManager.onSlowSyncComplete() }
                .onFailure { userSessionScope.syncManager.onSlowSyncFailure(it) }
        } catch (e: Exception) {
            kaliumLogger.e("An unexpected error happening during SlowSync", e)
            Either.Left(CoreFailure.Unknown(e))
        }

        return when (result) {
            is Either.Left -> {
                val failure = result.value
                kaliumLogger.e("SLOW SYNC FAILED $failure")
                (failure as? CoreFailure.Unknown)?.let {
                    it.rootCause?.printStackTrace()
                }
                Result.Failure
            }
            is Either.Right -> {
                kaliumLogger.i("SLOW SYNC SUCCESS $result")
                Result.Success
            }
        }
    }

    companion object {
        const val name: String = "SLOW_SYNC"
    }

}
