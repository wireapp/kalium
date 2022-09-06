package com.wire.kalium.logic.feature.publicuser.search

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.user.OtherUser
import kotlinx.coroutines.flow.Flow

sealed class SearchUserResult {
    data class Success(val userSearchResultFlow: Flow<List<OtherUser>>) : SearchUserResult()
    sealed class Failure : SearchUserResult() {
        object InvalidQuery : Failure()
        object InvalidRequest : Failure()
        class Generic(val genericFailure: CoreFailure) : Failure()
    }
}
