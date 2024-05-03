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
package com.wire.kalium.logic.feature.protocol

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.data.user.SupportedProtocol
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.util.arrangement.repository.UserRepositoryArrangement
import com.wire.kalium.logic.util.arrangement.repository.UserRepositoryArrangementImpl
import com.wire.kalium.logic.util.shouldFail
import com.wire.kalium.logic.util.shouldSucceed
import io.mockative.coVerify
import io.mockative.eq
import io.mockative.once
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class OneOnOneProtocolSelectorTest {

    @Test
    fun givenSelfUserIsNull_thenShouldReturnFailure() = runTest {
        val (_, oneOnOneProtocolSelector) = arrange {
            withUserByIdReturning(Either.Right(TestUser.OTHER))
            withSelfUserReturning(null)
        }

        oneOnOneProtocolSelector.getProtocolForUser(TestUser.USER_ID)
            .shouldFail {
                assertIs<CoreFailure.Unknown>(it)
                assertIs<NullPointerException>(it.rootCause)
            }
    }

    @Test
    fun givenFailureToFindOtherUser_thenShouldPropagateFailure() = runTest {
        val failure = StorageFailure.DataNotFound
        val (_, oneOnOneProtocolSelector) = arrange {
            withSelfUserReturning(TestUser.SELF)
            withUserByIdReturning(Either.Left(failure))
        }

        oneOnOneProtocolSelector.getProtocolForUser(TestUser.USER_ID)
            .shouldFail {
                assertEquals(failure, it)
            }
    }

    @Test
    fun givenOtherUserId_thenShouldCallRepoWithCorrectUserId() = runTest {
        val failure = StorageFailure.DataNotFound
        val (arrangement, oneOnOneProtocolSelector) = arrange {
            withSelfUserReturning(TestUser.SELF)
            withUserByIdReturning(Either.Left(failure))
        }
        val otherUserId = TestUser.USER_ID
        oneOnOneProtocolSelector.getProtocolForUser(TestUser.USER_ID)

        coVerify {
            arrangement.userRepository.userById(eq(otherUserId))
        }.wasInvoked(exactly = once)
    }

    @Test
    fun givenBothUsersSupportProteusAndMLS_thenShouldPreferMLS() = runTest {
        val supportedProtocols = setOf(SupportedProtocol.MLS, SupportedProtocol.PROTEUS)
        val (arrangement, oneOnOneProtocolSelector) = arrange {
            withSelfUserReturning(TestUser.SELF.copy(supportedProtocols = supportedProtocols))
            withUserByIdReturning(Either.Right(TestUser.OTHER.copy(supportedProtocols = supportedProtocols)))
        }

        oneOnOneProtocolSelector.getProtocolForUser(TestUser.USER_ID)
            .shouldSucceed {
                assertEquals(SupportedProtocol.MLS, it)
            }
    }

    @Test
    fun givenBothUsersSupportProteusAndOnlyOneSupportsMLS_thenShouldPreferProteus() = runTest {
        val bothProtocols = setOf(SupportedProtocol.MLS, SupportedProtocol.PROTEUS)
        val (_, oneOnOneProtocolSelector) = arrange {
            withSelfUserReturning(TestUser.SELF.copy(supportedProtocols = bothProtocols))
            withUserByIdReturning(Either.Right(TestUser.OTHER.copy(supportedProtocols = setOf(SupportedProtocol.PROTEUS))))
        }

        oneOnOneProtocolSelector.getProtocolForUser(TestUser.USER_ID)
            .shouldSucceed {
                assertEquals(SupportedProtocol.PROTEUS, it)
            }
    }

    @Test
    fun givenBothUsersSupportMLS_thenShouldPreferMLS() = runTest {
        val mlsSet = setOf(SupportedProtocol.MLS)
        val (_, oneOnOneProtocolSelector) = arrange {
            withSelfUserReturning(TestUser.SELF.copy(supportedProtocols = mlsSet))
            withUserByIdReturning(Either.Right(TestUser.OTHER.copy(supportedProtocols = mlsSet)))
        }

        oneOnOneProtocolSelector.getProtocolForUser(TestUser.USER_ID)
            .shouldSucceed {
                assertEquals(SupportedProtocol.MLS, it)
            }
    }

    @Test
    fun givenUsersHaveNoProtocolInCommon_thenShouldReturnNoCommonProtocol() = runTest {
        val (_, oneOnOneProtocolSelector) = arrange {
            withSelfUserReturning(TestUser.SELF.copy(supportedProtocols = setOf(SupportedProtocol.MLS)))
            withUserByIdReturning(Either.Right(TestUser.OTHER.copy(supportedProtocols = setOf(SupportedProtocol.PROTEUS))))
        }

        oneOnOneProtocolSelector.getProtocolForUser(TestUser.USER_ID)
            .shouldFail {
                assertIs<CoreFailure.NoCommonProtocolFound>(it)
            }
    }

    private class Arrangement(private val configure: suspend Arrangement.() -> Unit) :
        UserRepositoryArrangement by UserRepositoryArrangementImpl() {
        fun arrange(): Pair<Arrangement, OneOnOneProtocolSelector> = run {
            runBlocking { configure() }
            this@Arrangement to OneOnOneProtocolSelectorImpl(userRepository)
        }
    }

    private companion object {
        fun arrange(configure: suspend Arrangement.() -> Unit) = Arrangement(configure).arrange()
    }

}
