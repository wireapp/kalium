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

import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.data.session.SessionRepository
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.common.functional.right
import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.everySuspend
import dev.mokkery.matcher.eq
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class FederatedSearchParserTest {

    @Test
    fun givenUserIsNotFederated_whenParsingSearchQuery_thenSearchQueryIsNotModified() = runTest {
        val (arrangement, federatedSearchParser) = Arrangement().arrange {
            withIsFederated(result = false.right())
        }

        val searchQuery = "searchQuery"
        val result = federatedSearchParser(searchQuery, true)

        assertEquals(searchQuery, result.searchTerm)
        assertEquals(selfUserId.domain, result.domain)

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.sessionRepository.isFederated(eq(selfUserId))
        }
    }

    @Test
    fun givenUserIsFederated_whenSearchQueryIncludeNoDomain_thenSearchQueryIsNotModified() = runTest {
        val (arrangement, federatedSearchParser) = Arrangement().arrange {
            withIsFederated(result = true.right())
        }

        val searchQuery = "search Query"
        val result = federatedSearchParser(searchQuery, true)

        assertEquals(searchQuery, result.searchTerm)
        assertEquals(selfUserId.domain, result.domain)

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.sessionRepository.isFederated(eq(selfUserId))
        }
    }

    @Test
    fun givenUserIsFederated_whenSearchQueryIncludeDomain_thenSearchQueryIsModified() = runTest {
        val (arrangement, federatedSearchParser) = Arrangement().arrange {
            withIsFederated(result = true.right())
        }

        val searchQuery = " search Query @domain.co"
        val result = federatedSearchParser(searchQuery, true)

        assertEquals(" search Query ", result.searchTerm)
        assertEquals("domain.co", result.domain)

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.sessionRepository.isFederated(eq(selfUserId))
        }
    }

    @Test
    fun givenSearchQuery_whenTheParserIsCalledMultibletime_thenIsFederatedIsExecutedOnlyOnce() = runTest {
        val (arrangement, federatedSearchParser) = Arrangement().arrange {
            withIsFederated(result = true.right())
        }

        val searchQuery = " search Query @domain.co"
        federatedSearchParser(searchQuery, true)
        federatedSearchParser(searchQuery, true)
        federatedSearchParser(searchQuery, true)

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.sessionRepository.isFederated(eq(selfUserId))
        }
    }

    @Test
    fun givenUserIsNotFederated_whenSearchQueryIncludeDomainButRemoteDomainForbidden_thenSearchQueryIsNotModified() = runTest {
        val (arrangement, federatedSearchParser) = Arrangement().arrange {
            withIsFederated(result = true.right())
        }

        val searchQuery = " search Query @domain.co"
        val result = federatedSearchParser(searchQuery, false)

        assertEquals(" search Query @domain.co", result.searchTerm)
        assertEquals(selfUserId.domain, result.domain)

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.sessionRepository.isFederated(eq(selfUserId))
        }
    }

    private companion object {
        val selfUserId: UserId = UserId("selfUser", "selfDomain")
    }
    private class Arrangement {
        val sessionRepository = mock<SessionRepository>(mode = MockMode.autoUnit)

        private val federatedSearchParser = FederatedSearchParser(sessionRepository, selfUserId)

        suspend fun arrange(block: suspend Arrangement.() -> Unit) = let {
            block()
            this to federatedSearchParser
        }

        suspend fun withIsFederated(result: Either<StorageFailure, Boolean>) {
            everySuspend { sessionRepository.isFederated(eq(selfUserId)) } returns result
        }
    }
}
