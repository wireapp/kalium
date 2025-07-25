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
package com.wire.kalium.logic.feature.conversation

import com.wire.kalium.logic.data.sync.SlowSyncRepository
import com.wire.kalium.logic.data.sync.SlowSyncStatus
import com.wire.kalium.logic.feature.message.ephemeral.DeleteEphemeralMessagesAfterEndDateUseCase
import com.wire.kalium.logic.framework.TestConversationDetails
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.logger.kaliumLogger
import com.wire.kalium.logic.util.arrangement.mls.OneOnOneResolverArrangement
import com.wire.kalium.logic.util.arrangement.mls.OneOnOneResolverArrangementImpl
import com.wire.kalium.logic.util.arrangement.provider.CryptoTransactionProviderArrangement
import com.wire.kalium.logic.util.arrangement.provider.CryptoTransactionProviderArrangementImpl
import com.wire.kalium.logic.util.arrangement.repository.ConversationRepositoryArrangement
import com.wire.kalium.logic.util.arrangement.repository.ConversationRepositoryArrangementImpl
import io.mockative.any
import io.mockative.coEvery
import io.mockative.coVerify
import io.mockative.eq
import io.mockative.every
import io.mockative.mock
import io.mockative.once
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class NotifyConversationIsOpenUseCaseTest {

    @Test
    fun givenConversationId_whenInvoking_thenShouldGetDetailsFromRepository() = runTest {
        val details = TestConversationDetails.CONVERSATION_GROUP
        val (arrangement, notifyConversationIsOpenUseCase) = arrange {
            withObserveConversationDetailsByIdReturning(
                Either.Right(details)
            )

            withDeleteEphemeralMessageEndDateSuccess()
        }
        notifyConversationIsOpenUseCase.invoke(details.conversation.id)

        coVerify {
            arrangement.conversationRepository.observeConversationDetailsById(eq(details.conversation.id))
        }.wasInvoked(exactly = once)
    }

    @Test
    fun givenGroupConversationId_whenInvoking_thenShouldNotUseResolver() = runTest {
        val details = TestConversationDetails.CONVERSATION_GROUP
        val (arrangement, notifyConversationIsOpenUseCase) = arrange {
            withObserveConversationDetailsByIdReturning(
                Either.Right(details)
            )

            withDeleteEphemeralMessageEndDateSuccess()
        }
        notifyConversationIsOpenUseCase.invoke(details.conversation.id)

        coVerify {
            arrangement.oneOnOneResolver.resolveOneOnOneConversationWithUser(any(), any(), any())
        }.wasNotInvoked()
    }

    @Test
    fun givenOneOnOneConversationId_whenInvoking_thenShouldResolveProtocolForUser() = runTest {
        val details = TestConversationDetails.CONVERSATION_ONE_ONE
        val (arrangement, notifyConversationIsOpenUseCase) = arrange {
            withObserveConversationDetailsByIdReturning(
                Either.Right(details)
            )
            withResolveOneOnOneConversationWithUserReturning(
                Either.Right(details.conversation.id)
            )

            withDeleteEphemeralMessageEndDateSuccess()
        }
        notifyConversationIsOpenUseCase.invoke(details.conversation.id)

        coVerify {
            arrangement.oneOnOneResolver.resolveOneOnOneConversationWithUser(
                any(), eq(details.otherUser), any()
            )
        }.wasInvoked(exactly = once)
    }

    private class Arrangement(
        private val configure: suspend Arrangement.() -> Unit
    ) : OneOnOneResolverArrangement by OneOnOneResolverArrangementImpl(),
        ConversationRepositoryArrangement by ConversationRepositoryArrangementImpl(),
        CryptoTransactionProviderArrangement by CryptoTransactionProviderArrangementImpl() {
        private val deleteEphemeralMessageEndDate = mock(DeleteEphemeralMessagesAfterEndDateUseCase::class)
        private val slowSyncRepository = mock(SlowSyncRepository::class)

        suspend fun withDeleteEphemeralMessageEndDateSuccess() {
            coEvery {
                deleteEphemeralMessageEndDate.invoke()
            }.returns(Unit)
        }

        init {
            every {
                slowSyncRepository.slowSyncStatus
            }.returns(MutableStateFlow(SlowSyncStatus.Complete))
        }

        suspend fun arrange(): Pair<Arrangement, NotifyConversationIsOpenUseCase> = run {
            withTransactionReturning(Either.Right(Unit))
            configure()
            this@Arrangement to NotifyConversationIsOpenUseCaseImpl(
                oneOnOneResolver = oneOnOneResolver,
                conversationRepository = conversationRepository,
                kaliumLogger = kaliumLogger,
                deleteEphemeralMessageEndDate = deleteEphemeralMessageEndDate,
                slowSyncRepository = slowSyncRepository,
                transactionProvider = cryptoTransactionProvider
            )
        }
    }

    private companion object {
        suspend fun arrange(configure: suspend Arrangement.() -> Unit) = Arrangement(configure).arrange()
    }
}
