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
package com.wire.kalium.network.api.v0.unauthenticated

import com.wire.kalium.network.UnauthenticatedNetworkClient
import com.wire.kalium.network.api.base.unauthenticated.VerificationCodeApi
import com.wire.kalium.network.api.base.unauthenticated.VerificationCodeApi.ActionToBeVerified
import com.wire.kalium.network.utils.NetworkResponse
import com.wire.kalium.network.utils.wrapKaliumResponse
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

internal open class VerificationCodeApiV0 internal constructor(
    private val unauthenticatedNetworkClient: UnauthenticatedNetworkClient
) : VerificationCodeApi {

    private val httpClient get() = unauthenticatedNetworkClient.httpClient

    override suspend fun sendVerificationCode(
        email: String,
        action: ActionToBeVerified
    ): NetworkResponse<Unit> {
        return wrapKaliumResponse {
            httpClient.post(PATH_VERIFICATION_CODE_SEND) {
                setBody(createSendVerificationCodeBody(action, email))
            }
        }
    }

    private fun createSendVerificationCodeBody(
        action: ActionToBeVerified,
        email: String
    ) = SendVerificationCodeRequest(
        when (action) {
            ActionToBeVerified.LOGIN_OR_CLIENT_REGISTRATION -> SendVerificationCodeRequest.Action.LOGIN
            ActionToBeVerified.CREATE_SCIM_TOKEN -> SendVerificationCodeRequest.Action.CREATE_SCIM_TOKEN
            ActionToBeVerified.DELETE_TEAM -> SendVerificationCodeRequest.Action.DELETE_TEAM
        },
        email
    )

    @Serializable
    internal data class SendVerificationCodeRequest(
        @SerialName("action") val action: Action,
        @SerialName("email") val email: String
    ) {
        @Serializable
        enum class Action(private val serialName: String) {
            @SerialName("create_scim_token")
            CREATE_SCIM_TOKEN("create_scim_token"),

            @SerialName("login")
            LOGIN("login"),

            @SerialName("delete_team")
            DELETE_TEAM("delete_team");

            override fun toString(): String {
                return serialName
            }
        }
    }

    internal companion object {
        const val PATH_VERIFICATION_CODE_SEND = "verification-code/send"
    }
}
