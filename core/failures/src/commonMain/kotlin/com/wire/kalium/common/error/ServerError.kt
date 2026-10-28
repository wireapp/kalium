/*
 * Wire
 * Copyright (C) 2025 Wire Swiss GmbH
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

package com.wire.kalium.common.error

import com.wire.kalium.logic.data.user.UserId

/** Infrastructure-independent details used to classify a server failure. */
sealed interface ServerError {
    data class Response(val code: Int, val message: String, val label: String)

    data class InvalidRequest(
        val errorResponse: Response,
        val retryAfterSeconds: Int? = null
    ) : ServerError

    data class Redirect(val errorResponse: Response) : ServerError
    data class Server(val errorResponse: Response) : ServerError
    data class Unauthorized(val errorCode: Int) : ServerError
    data class AdminlessConversation(
        val errorResponse: Response,
        val eligibleMembers: List<UserId>
    ) : ServerError
    data class Other(val message: String?) : ServerError
}
