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
package com.wire.kalium.logic.feature.auth

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.auth.LoginDomainPath
import com.wire.kalium.logic.data.auth.login.LoginRepository
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.fold
import com.wire.kalium.logic.functional.left
import com.wire.kalium.logic.functional.right

/**
 * Use case to get the login flow for the client app/user to follow.
 * This is determined by the backend according to registration of the domain.¬
 *
 * @param email the email to look up the domain registration for
 */
interface GetLoginFlowForDomainUseCase {
    suspend operator fun invoke(email: String): Either<CoreFailure, LoginDomainPath>
}

@Suppress("FunctionNaming")
internal fun GetLoginFlowForDomainUseCase(
    loginRepository: LoginRepository
) = object : GetLoginFlowForDomainUseCase {
    override suspend fun invoke(email: String): Either<CoreFailure, LoginDomainPath> {
        return loginRepository.getDomainRegistration(email).fold({
            it.left()
        }, {
            it.right()
        })
    }
}
