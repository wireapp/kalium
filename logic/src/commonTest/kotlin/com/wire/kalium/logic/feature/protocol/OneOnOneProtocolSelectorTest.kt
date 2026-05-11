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

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.logic.configuration.UserConfigRepository
import com.wire.kalium.logic.data.user.OtherUser
import com.wire.kalium.logic.data.user.SelfUser
import com.wire.kalium.logic.data.user.SupportedProtocol
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.left
import com.wire.kalium.common.functional.right
import com.wire.kalium.logic.util.shouldFail
import com.wire.kalium.logic.util.shouldSucceed
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
import kotlin.test.assertIs

class OneOnOneProtocolSelectorTest {

    @Test
    fun givenSelfUserIsNull_thenShouldReturnFailure() = runTest {
        val failure = StorageFailure.DataNotFound
        val (_, oneOnOneProtocolSelector) = arrange {
            withUserByIdReturning(Either.Right(TestUser.OTHER))
            withSelfUserReturning(StorageFailure.DataNotFound.left())
            withGetDefaultProtocolReturning(failure.left())
        }

        oneOnOneProtocolSelector.getProtocolForUser(TestUser.USER_ID)
            .shouldFail {
                assertIs<StorageFailure.DataNotFound>(it)
            }
    }

    @Test
    fun givenFailureToFindOtherUser_thenShouldPropagateFailure() = runTest {
        val failure = StorageFailure.DataNotFound
        val (_, oneOnOneProtocolSelector) = arrange {
            withSelfUserReturning(TestUser.SELF.right())
            withUserByIdReturning(failure.left())
            withGetDefaultProtocolReturning(failure.left())
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
            withSelfUserReturning(TestUser.SELF.right())
            withUserByIdReturning(Either.Left(failure))
            withGetDefaultProtocolReturning(failure.left())
        }
        val otherUserId = TestUser.USER_ID
        oneOnOneProtocolSelector.getProtocolForUser(TestUser.USER_ID)

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.userRepository.userById(eq(otherUserId))
        }
    }

    @Test
    fun givenBothUsersSupportProteusAndMLS_thenShouldPreferMLS() = runTest {
        val failure = StorageFailure.DataNotFound
        val supportedProtocols = setOf(SupportedProtocol.MLS, SupportedProtocol.PROTEUS)
        val (arrangement, oneOnOneProtocolSelector) = arrange {
            withSelfUserReturning(TestUser.SELF.copy(supportedProtocols = supportedProtocols).right())
            withUserByIdReturning(Either.Right(TestUser.OTHER.copy(supportedProtocols = supportedProtocols)))
            withGetDefaultProtocolReturning(failure.left())
        }

        oneOnOneProtocolSelector.getProtocolForUser(TestUser.USER_ID)
            .shouldSucceed {
                assertEquals(SupportedProtocol.MLS, it)
            }
    }

    @Test
    fun givenBothUsersSupportProteusAndOnlyOneSupportsMLS_thenShouldPreferProteus() = runTest {
        val bothProtocols = setOf(SupportedProtocol.MLS, SupportedProtocol.PROTEUS)
        val failure = StorageFailure.DataNotFound
        val (_, oneOnOneProtocolSelector) = arrange {
            withSelfUserReturning(TestUser.SELF.copy(supportedProtocols = bothProtocols).right())
            withUserByIdReturning(Either.Right(TestUser.OTHER.copy(supportedProtocols = setOf(SupportedProtocol.PROTEUS))))
            withGetDefaultProtocolReturning(failure.left())
        }

        oneOnOneProtocolSelector.getProtocolForUser(TestUser.USER_ID)
            .shouldSucceed {
                assertEquals(SupportedProtocol.PROTEUS, it)
            }
    }

    @Test
    fun givenBothUsersSupportMLS_thenShouldPreferMLS() = runTest {
        val failure = StorageFailure.DataNotFound
        val mlsSet = setOf(SupportedProtocol.MLS)
        val (_, oneOnOneProtocolSelector) = arrange {
            withSelfUserReturning(TestUser.SELF.copy(supportedProtocols = mlsSet).right())
            withUserByIdReturning(Either.Right(TestUser.OTHER.copy(supportedProtocols = mlsSet)))
            withGetDefaultProtocolReturning(failure.left())
        }

        oneOnOneProtocolSelector.getProtocolForUser(TestUser.USER_ID)
            .shouldSucceed {
                assertEquals(SupportedProtocol.MLS, it)
            }
    }

    @Test
    fun givenUsersHaveNoProtocolInCommon_thenShouldReturnNoCommonProtocol() = runTest {
        val failure = StorageFailure.DataNotFound
        val (_, oneOnOneProtocolSelector) = arrange {
            withSelfUserReturning(TestUser.SELF.copy(supportedProtocols = setOf(SupportedProtocol.MLS)).right())
            withUserByIdReturning(Either.Right(TestUser.OTHER.copy(supportedProtocols = setOf(SupportedProtocol.PROTEUS))))
            withGetDefaultProtocolReturning(failure.left())
        }

        oneOnOneProtocolSelector.getProtocolForUser(TestUser.USER_ID)
            .shouldFail {
                assertIs<CoreFailure.NoCommonProtocolFound.OtherNeedToUpdate>(it)
            }
    }

    @Test
    fun givenUsersHaveNoProtocolInCommon_thenShouldReturnNoCommonProtocol_2() = runTest {
        val failure = StorageFailure.DataNotFound
        val (_, oneOnOneProtocolSelector) = arrange {
            withSelfUserReturning(TestUser.SELF.copy(supportedProtocols = setOf(SupportedProtocol.PROTEUS)).right())
            withUserByIdReturning(Either.Right(TestUser.OTHER.copy(supportedProtocols = setOf(SupportedProtocol.MLS))))
            withGetDefaultProtocolReturning(failure.left())
        }

        oneOnOneProtocolSelector.getProtocolForUser(TestUser.USER_ID)
            .shouldFail {
                assertIs<CoreFailure.NoCommonProtocolFound.SelfNeedToUpdate>(it)
            }
    }

    @Test
    fun givenUsersHaveProtocolInCommonIncludingDefaultProtocol_thenShouldReturnDefaultProtocolAsCommonProtocol() = runTest {
        val (_, oneOnOneProtocolSelector) = arrange {
            withSelfUserReturning(TestUser.SELF.copy(supportedProtocols = setOf(SupportedProtocol.MLS, SupportedProtocol.PROTEUS)).right())
            withUserByIdReturning(Either.Right(TestUser.OTHER.copy(supportedProtocols = setOf(SupportedProtocol.MLS))))
            withGetDefaultProtocolReturning(SupportedProtocol.MLS.right())
        }

        oneOnOneProtocolSelector.getProtocolForUser(TestUser.USER_ID)
            .shouldSucceed() {
                assertEquals(SupportedProtocol.MLS, it)
            }
    }

    private class Arrangement(private val configure: suspend Arrangement.() -> Unit) {
        val userRepository = mock<UserRepository>(mode = MockMode.autoUnit)
        val userConfigRepository = mock<UserConfigRepository>(mode = MockMode.autoUnit)

        suspend fun arrange(): Pair<Arrangement, OneOnOneProtocolSelector> = run {
            configure()
            this@Arrangement to OneOnOneProtocolSelectorImpl(userRepository, userConfigRepository)
        }

        suspend fun withSelfUserReturning(result: Either<StorageFailure, SelfUser>) {
            everySuspend { userRepository.getSelfUser() } returns result
        }

        suspend fun withUserByIdReturning(result: Either<CoreFailure, OtherUser>) {
            everySuspend { userRepository.userById(eq(TestUser.USER_ID)) } returns result
        }

        suspend fun withGetDefaultProtocolReturning(result: Either<StorageFailure, SupportedProtocol>) {
            everySuspend { userConfigRepository.getDefaultProtocol() } returns result
        }
    }

    private companion object {
        suspend fun arrange(configure: suspend Arrangement.() -> Unit) = Arrangement(configure).arrange()
    }
}
