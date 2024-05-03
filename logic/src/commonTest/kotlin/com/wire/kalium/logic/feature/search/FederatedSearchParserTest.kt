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

import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.functional.right
import com.wire.kalium.logic.util.arrangement.repository.SessionRepositoryArrangement
import com.wire.kalium.logic.util.arrangement.repository.SessionRepositoryArrangementImpl
import io.mockative.any
import io.mockative.coVerify
import io.mockative.eq
import io.mockative.fake.valueOf
import io.mockative.matchers.AnyMatcher
import io.mockative.once
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class FederatedSearchParserTest {

    @Test
    fun givenUserIsNotFederated_whenParsingSearchQuery_thenSearchQueryIsNotModified() = runTest {
        val (arrangement, federatedSearchParser) = Arrangement().arrange {
            withIsFederated(result = false.right(), userId = AnyMatcher(valueOf()))
        }

        val searchQuery = "searchQuery"
        val result = federatedSearchParser(searchQuery)

        assertEquals(searchQuery, result.searchTerm)
        assertEquals(selfUserId.domain, result.domain)

        coVerify {
            arrangement.sessionRepository.isFederated(eq(selfUserId))
        }.wasInvoked(exactly = once)
    }

    @Test
    fun givenUserIsFederated_whenSearchQueryIncludeNoDomain_thenSearchQueryIsNotModified() = runTest {
        val (arrangement, federatedSearchParser) = Arrangement().arrange {
            withIsFederated(result = true.right(), userId = AnyMatcher(valueOf()))
        }

        val searchQuery = "search Query"
        val result = federatedSearchParser(searchQuery)

        assertEquals(searchQuery, result.searchTerm)
        assertEquals(selfUserId.domain, result.domain)

        coVerify {
            arrangement.sessionRepository.isFederated(eq(selfUserId))
        }.wasInvoked(exactly = once)
    }

    @Test
    fun givenUserIsFederated_whenSearchQueryIncludeDomain_thenSearchQueryIsModified() = runTest {
        val (arrangement, federatedSearchParser) = Arrangement().arrange {
            withIsFederated(result = true.right(), userId = AnyMatcher(valueOf()))
        }

        val searchQuery = " search Query @domain.co"
        val result = federatedSearchParser(searchQuery)

        assertEquals(" search Query ", result.searchTerm)
        assertEquals("domain.co", result.domain)

        coVerify {
            arrangement.sessionRepository.isFederated(eq(selfUserId))
        }.wasInvoked(exactly = once)
    }

    @Test
    fun givenSearchQuery_whenTheParserIsCalledMultibletime_thenIsFederatedIsExecutedOnlyOnce() = runTest {
        val (arrangement, federatedSearchParser) = Arrangement().arrange {
            withIsFederated(result = true.right(), userId = AnyMatcher(valueOf()))
        }

        val searchQuery = " search Query @domain.co"
        federatedSearchParser(searchQuery)
        federatedSearchParser(searchQuery)
        federatedSearchParser(searchQuery)

        coVerify {
            arrangement.sessionRepository.isFederated(eq(selfUserId))
        }.wasInvoked(exactly = once)
    }

    private companion object {
        val selfUserId: UserId = UserId("selfUser", "selfDomain")
    }
    private class Arrangement: SessionRepositoryArrangement by SessionRepositoryArrangementImpl() {

        private val federatedSearchParser = FederatedSearchParser(sessionRepository, selfUserId)

        fun arrange(block: suspend Arrangement.() -> Unit) = let {
            runBlocking { block() }
            this to federatedSearchParser
        }

    }
}
