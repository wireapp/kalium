/*
 * Wire
 * Copyright (C) 2024 Wire Swiss GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see http://www.gnu.org/licenses/.
 */

package com.wire.kalium.logic.feature.publicuser.search

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.logic.data.publicuser.model.UserSearchResult

sealed class SearchUsersResult {
    data class Success(val userSearchResult: UserSearchResult) : SearchUsersResult()
    sealed class Failure : SearchUsersResult() {
        data object InvalidQuery : Failure()
        data object InvalidRequest : Failure()
        data class Generic(val genericFailure: CoreFailure) : Failure()
    }
}
