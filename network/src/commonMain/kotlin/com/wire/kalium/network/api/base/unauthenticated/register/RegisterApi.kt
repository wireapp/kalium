/*
 * Wire
 * Copyright (C) 2023 Wire Swiss GmbH
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

package com.wire.kalium.network.api.base.unauthenticated.register

import com.wire.kalium.network.api.base.model.SessionDTO
import com.wire.kalium.network.api.base.model.NewUserDTO
import com.wire.kalium.network.api.base.model.UserDTO
import com.wire.kalium.network.utils.NetworkResponse

interface RegisterApi {

    sealed class RegisterParam(
        open val name: String
    ) {
        internal abstract fun toBody(): NewUserDTO

        data class PersonalAccount(
            val email: String,
            val emailCode: String,
            override val name: String,
            val password: String,
        ) : RegisterParam(name) {
            override fun toBody(): NewUserDTO = NewUserDTO(
                email = email,
                emailCode = emailCode,
                password = password,
                name = name,
                accentId = null,
                assets = null,
                invitationCode = null,
                label = null,
                locale = null,
                phone = null,
                phoneCode = null,
                newBindingTeamDTO = null,
                teamCode = null,
                expiresIn = null,
                managedByDTO = null,
                ssoID = null,
                teamID = null,
                uuid = null
            )
        }

        data class TeamAccount(
            val email: String,
            val emailCode: String,
            override val name: String,
            val password: String,
            val teamName: String,
            val teamIcon: String
        ) : RegisterParam(name) {
            override fun toBody(): NewUserDTO = NewUserDTO(
                email = email,
                emailCode = emailCode,
                password = password,
                name = name,
                accentId = null,
                assets = null,
                invitationCode = null,
                label = null,
                locale = null,
                phone = null,
                phoneCode = null,
                newBindingTeamDTO = NewBindingTeamDTO(
                    currency = null,
                    iconAssetId = teamIcon,
                    iconKey = null,
                    name = teamName,
                ),
                teamCode = null,
                expiresIn = null,
                managedByDTO = null,
                ssoID = null,
                teamID = null,
                uuid = null
            )
        }
    }

    sealed class RequestActivationCodeParam {
        internal abstract fun toBody(): RequestActivationRequest
        data class Email(
            val email: String
        ) : RequestActivationCodeParam() {
            override fun toBody(): RequestActivationRequest = RequestActivationRequest(email, null, null, null)
        }
    }

    sealed class ActivationParam(val dryRun: Boolean = true) {
        internal abstract fun toBody(): ActivationRequest
        data class Email(
            val email: String,
            val code: String
        ) : ActivationParam() {
            override fun toBody(): ActivationRequest = ActivationRequest(code = code, dryRun = dryRun, email = email, null, null, null)
        }
    }

    suspend fun register(
        param: RegisterParam
    ): NetworkResponse<Pair<UserDTO, SessionDTO>>

    suspend fun requestActivationCode(
        param: RequestActivationCodeParam
    ): NetworkResponse<Unit>

    suspend fun activate(
        param: ActivationParam
    ): NetworkResponse<Unit>
}
