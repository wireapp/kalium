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

package com.wire.kalium.logic.data.sso

object SSOUtil {
    internal fun generateSuccessRedirect(serverConfigId: String) =
        "wire://$SSO_LOGIN_HOST/$SUCCESS_PATH/?$QUERY_COOKIE=\$cookie&$QUERY_USER_ID=\$userid&$QUERY_SERVER_CONFIG=$serverConfigId"

    internal fun generateErrorRedirect() = "wire://$SSO_LOGIN_HOST/$ERROR_PATH/?$QUERY_ERROR=\$label"

    private const val QUERY_USER_ID = "userId"
    private const val QUERY_COOKIE = "cookie"
    private const val QUERY_SERVER_CONFIG = "location"
    private const val QUERY_ERROR = "errorCode"
    private const val SSO_LOGIN_HOST = "sso-login"
    private const val SUCCESS_PATH = "success"
    private const val ERROR_PATH = "failure"
}
