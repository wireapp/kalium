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
package com.wire.kalium.logic.feature.search

import com.wire.kalium.logic.data.session.SessionRepository
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.functional.fold

class FederatedSearchParser(
    private val sessionRepository: SessionRepository,
    private val selfUserId: UserId
) {
    private val regex by lazy {
        Regex(DOMAIN_REGEX)
    }

    private val isFederationEnabled by lazy {
        sessionRepository.isFederated(selfUserId).fold(
            { false },
            { it }
        )
    }

    operator fun invoke(searchQuery: String): Result =
        when {
            isFederationEnabled.not() -> Result(searchQuery, selfUserId.domain)

            searchQuery.matches(regex) -> {
                val domain = searchQuery.substringAfterLast(DOMAIN_SEPARATOR)
                val searchTerm = searchQuery.substringBeforeLast(DOMAIN_SEPARATOR)
                Result(searchTerm, domain)
            }

            else -> Result(searchQuery, selfUserId.domain)
        }

    private companion object {
        const val DOMAIN_SEPARATOR = "@"
        const val DOMAIN_REGEX = ".+\\$DOMAIN_SEPARATOR[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}"
    }

    data class Result(
        val searchTerm: String,
        val domain: String
    )
}
