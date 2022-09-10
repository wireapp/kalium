package com.wire.kalium.logic.feature.session

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.feature.auth.AccountInfo

sealed class GetAllSessionsResult {
    class Success(val sessions: List<AccountInfo>): GetAllSessionsResult()

    sealed class Failure : GetAllSessionsResult() {
        object NoSessionFound : Failure()
        class Generic(val genericFailure: CoreFailure) : Failure()
    }
}
