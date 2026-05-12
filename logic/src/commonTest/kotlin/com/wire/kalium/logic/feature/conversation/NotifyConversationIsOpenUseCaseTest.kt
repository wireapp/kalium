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

import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.logic.data.conversation.ConversationDetails
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.sync.SlowSyncRepository
import com.wire.kalium.logic.data.sync.SlowSyncStatus
import com.wire.kalium.logic.feature.conversation.mls.OneOnOneResolver
import com.wire.kalium.logic.feature.message.ephemeral.DeleteEphemeralMessagesAfterEndDateUseCase
import com.wire.kalium.logic.framework.TestConversationDetails
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.logger.kaliumLogger
import com.wire.kalium.logic.util.arrangement.provider.CryptoTransactionProviderArrangement
import com.wire.kalium.logic.util.arrangement.provider.CryptoTransactionProviderArrangementImpl
import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.matcher.eq
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
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

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.conversationRepository.observeConversationDetailsById(eq(details.conversation.id))
        }
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

        verifySuspend(VerifyMode.not) {
            arrangement.oneOnOneResolver.resolveOneOnOneConversationWithUser(any(), any(), any())
        }
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

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.oneOnOneResolver.resolveOneOnOneConversationWithUser(
                any(), eq(details.otherUser), any()
            )
        }
    }

    private class Arrangement(
        private val configure: suspend Arrangement.() -> Unit
    ) : CryptoTransactionProviderArrangement by CryptoTransactionProviderArrangementImpl() {
        val oneOnOneResolver = mock<OneOnOneResolver>(mode = MockMode.autoUnit)
        val conversationRepository = mock<ConversationRepository>(mode = MockMode.autoUnit)
        private val deleteEphemeralMessageEndDate = mock<DeleteEphemeralMessagesAfterEndDateUseCase>(mode = MockMode.autoUnit)
        private val slowSyncRepository = mock<SlowSyncRepository>(mode = MockMode.autoUnit)

        suspend fun withDeleteEphemeralMessageEndDateSuccess() {
            everySuspend {
                deleteEphemeralMessageEndDate.invoke()
            } returns Unit
        }

        suspend fun withObserveConversationDetailsByIdReturning(vararg results: Either<StorageFailure, ConversationDetails>) {
            everySuspend {
                conversationRepository.observeConversationDetailsById(any())
            } returns flowOf(*results)
        }

        suspend fun withResolveOneOnOneConversationWithUserReturning(result: Either<com.wire.kalium.common.error.CoreFailure, com.wire.kalium.logic.data.id.ConversationId>) {
            everySuspend {
                oneOnOneResolver.resolveOneOnOneConversationWithUser(any(), any(), any())
            } returns result
        }

        init {
            every {
                slowSyncRepository.slowSyncStatus
            } returns MutableStateFlow(SlowSyncStatus.Complete)
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
