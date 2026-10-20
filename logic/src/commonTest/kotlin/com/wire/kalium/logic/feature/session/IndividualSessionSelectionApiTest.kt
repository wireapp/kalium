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

package com.wire.kalium.logic.feature.session

import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.data.auth.AccountInfo
import com.wire.kalium.logic.data.session.SessionRepository
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.di.KaliumSessionSelectionApi
import com.wire.kalium.logic.di.createSessionSelectionApi
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.everySuspend
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.createGraph
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest

@DependencyGraph
internal interface IndividualSessionSelectionTestGraph {
    val sessionFactory: SessionGraph.Factory
}

class IndividualSessionSelectionApiTest {
    @Test
    fun freshGettersKeepOneRepositoryCaptureAndObserveItsLiveState() = runTest {
        val factory = createGraph<IndividualSessionSelectionTestGraph>().sessionFactory
        val firstRepository = mock<SessionRepository>()
        val secondRepository = mock<SessionRepository>()
        val firstAccount = AccountInfo.Valid(UserId("first", "domain"))
        val changedAccount = AccountInfo.Valid(UserId("changed", "domain"))
        val secondAccount = AccountInfo.Valid(UserId("second", "domain"))
        val current = MutableStateFlow<Either<StorageFailure, AccountInfo>>(Either.Right(firstAccount))
        val all = MutableStateFlow<Either<StorageFailure, List<AccountInfo>>>(Either.Right(listOf(firstAccount)))
        everySuspend { firstRepository.currentSession() } returns Either.Right(firstAccount)
        everySuspend { secondRepository.currentSession() } returns Either.Right(secondAccount)
        every { firstRepository.currentSessionFlow() } returns current
        every { firstRepository.allValidSessionsFlow() } returns all
        everySuspend { firstRepository.allValidSessions() } returns Either.Right(listOf(firstAccount))
        everySuspend { firstRepository.allSessions() } returns Either.Right(listOf(firstAccount))
        everySuspend { firstRepository.updateCurrentSession(changedAccount.userId) } returns Either.Right(Unit)
        val first: KaliumSessionSelectionApi = createSessionSelectionApi(factory, firstRepository)
        val second: KaliumSessionSelectionApi = createSessionSelectionApi(factory, secondRepository)
        assertNotSame(first.allSessions, first.allSessions)
        assertNotSame(first.allSessionsFlow, first.allSessionsFlow)
        assertNotSame(first.currentSession, first.currentSession)
        assertNotSame(first.currentSessionFlow, first.currentSessionFlow)
        assertNotSame(first.updateCurrentSession, first.updateCurrentSession)
        assertNotSame(first.currentSession, second.currentSession)
        repeat(2) {
            assertSame(firstAccount, assertIs<CurrentSessionResult.Success>(first.currentSession()).accountInfo)
            assertEquals(listOf(firstAccount), assertIs<GetAllSessionsResult.Success>(first.allSessions()).sessions)
        }
        assertSame(secondAccount, assertIs<CurrentSessionResult.Success>(second.currentSession()).accountInfo)
        val observe = first.currentSessionFlow
        assertSame(firstAccount, assertIs<CurrentSessionResult.Success>(observe().first()).accountInfo)
        current.value = Either.Right(changedAccount)
        all.value = Either.Right(listOf(changedAccount))
        assertSame(changedAccount, assertIs<CurrentSessionResult.Success>(observe().first()).accountInfo)
        assertEquals(listOf(changedAccount), assertIs<GetAllSessionsResult.Success>(first.allSessionsFlow().first()).sessions)
        assertIs<UpdateCurrentSessionUseCase.Result.Success>(first.updateCurrentSession(changedAccount.userId))
        verifySuspend(VerifyMode.exactly(2)) { firstRepository.currentSession() }
        verifySuspend(VerifyMode.exactly(1)) { secondRepository.currentSession() }
        verifySuspend(VerifyMode.exactly(1)) { firstRepository.updateCurrentSession(changedAccount.userId) }
    }
}
