/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
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

package com.wire.kalium.logic.feature.user

import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.data.auth.AccountInfo
import com.wire.kalium.logic.data.session.SessionRepository
import com.wire.kalium.logic.data.team.Team
import com.wire.kalium.logic.data.user.SelfUser
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.feature.UserSessionScope
import com.wire.kalium.logic.feature.UserSessionScopeProvider
import com.wire.kalium.logic.framework.TestUser
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.mock
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ObserveValidAccountsAdmissionTest {
    @Test
    fun unavailableQualifiedAccountDoesNotBlockOtherAndEmptyListCancelsLatestObservation() = runTest {
        val self = TestUser.SELF
        val unavailable = self.id.copy(domain = "other.example")
        val accounts = MutableStateFlow<Either<StorageFailure, List<AccountInfo>>>(
            Either.Right(listOf(AccountInfo.Valid(unavailable), AccountInfo.Valid(self.id)))
        )
        val repository = mock<SessionRepository>()
        every { repository.allValidSessionsFlow() } returns accounts
        var stopped = false
        val provider = object : UserSessionScopeProvider by mock<UserSessionScopeProvider>() {
            @Suppress("UNCHECKED_CAST") // This fixture supplies only the use case's self-user/team result.
            override fun <T : Any> observeValidSession(
                userId: UserId,
                sessionRepository: SessionRepository,
                observe: suspend UserSessionScope.() -> Flow<T>,
            ): Flow<T?> {
                val source: Flow<Pair<SelfUser, Team?>?> = if (userId == unavailable) flow {
                    try { emit(null); awaitCancellation() } finally { stopped = true }
                } else flowOf(self to null)
                return source.map { it as T? }
            }
        }
        val output = Channel<List<Pair<SelfUser, Team?>>>(Channel.UNLIMITED)
        val useCase = ObserveValidAccountsUseCaseImpl(repository, provider, StandardTestDispatcher(testScheduler))
        val collector = launch { useCase().collect { output.send(it) } }
        try {
            assertEquals(listOf(self to null), output.receive())
            accounts.value = Either.Right(emptyList())
            assertEquals(emptyList(), output.receive())
            assertTrue(stopped, "Outer latest switch did not cancel the unavailable account collection")
        } finally { collector.cancelAndJoin() }
    }
}
