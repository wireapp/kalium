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
package com.wire.kalium.logic.feature.auth

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.configuration.server.CustomServerConfigRepository
import com.wire.kalium.logic.configuration.server.ServerConfig
import com.wire.kalium.logic.data.auth.login.SSOLoginRepository
import com.wire.kalium.common.functional.flatMap
import com.wire.kalium.common.functional.fold

/**
 * Use case for domain lookup.
 * will return the server config for the domain.
 * if the domain is not found then it will return a failure.
 * @param email the email to lookup
 */
class DomainLookupUseCase internal constructor(
    private val customServerConfigRepository: CustomServerConfigRepository,
    private val ssoLoginRepository: SSOLoginRepository
) {
    suspend operator fun invoke(email: String): Result {
        val domain = email.substringAfterLast('@').ifBlank {
            // if the text is not an email then use the text as domain
            email
        }

        return ssoLoginRepository.domainLookup(domain).flatMap {
            customServerConfigRepository.fetchRemoteConfig(it.configJsonUrl)
        }.fold(Result::Failure, Result::Success)
    }

    sealed interface Result {
        data class Success(val serverLinks: ServerConfig.Links) : Result
        data class Failure(val coreFailure: CoreFailure) : Result
    }
}
