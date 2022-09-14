package com.wire.kalium.logic.feature.publicuser.search

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.publicuser.model.UserSearchResult

sealed class SearchUsersResult {
    data class Success(val userSearchResult: UserSearchResult) : SearchUsersResult()
    sealed class Failure : SearchUsersResult() {
        object InvalidQuery : Failure()
        object InvalidRequest : Failure()
        class Generic(val genericFailure: CoreFailure) : Failure()
    }
}
