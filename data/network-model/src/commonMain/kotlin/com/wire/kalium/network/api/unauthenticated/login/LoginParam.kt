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
package com.wire.kalium.network.api.unauthenticated.login

sealed class LoginParam(
    open val password: String,
    open val label: String?
) {
    data class LoginWithEmail(
        val email: String,
        override val password: String,
        override val label: String?,
        /**
         * Two-factor authentication code received in the user's email.
         * Optional as it may or may not be required depending on team settings.
         * @see VerificationCodeApi
         */
        val verificationCode: String? = null,
    ) : LoginParam(password, label)

    data class LoginWithHandle(
        val handle: String,
        override val password: String,
        override val label: String?,
    ) : LoginParam(password, label)
}
