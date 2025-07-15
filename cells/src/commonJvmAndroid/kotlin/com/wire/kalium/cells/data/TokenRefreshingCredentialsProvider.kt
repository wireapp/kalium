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
package com.wire.kalium.cells.data

import aws.smithy.kotlin.runtime.auth.awscredentials.Credentials
import aws.smithy.kotlin.runtime.auth.awscredentials.CredentialsProvider
import aws.smithy.kotlin.runtime.collections.Attributes
import com.wire.kalium.network.api.base.authenticated.AccessTokenApi
import com.wire.kalium.network.session.SessionManager

public class TokenRefreshingCredentialsProvider(
    private val sessionManager: SessionManager,
    private val accessTokenAPI: AccessTokenApi
) : CredentialsProvider {
    override suspend fun resolve(attributes: Attributes): Credentials {
        val tokens = sessionManager.updateToken(accessTokenAPI, sessionManager.session()?.accessToken ?: "")
        return Credentials(
            accessKeyId = tokens.accessToken,
            secretAccessKey = tokens.refreshToken
        )
    }
}
