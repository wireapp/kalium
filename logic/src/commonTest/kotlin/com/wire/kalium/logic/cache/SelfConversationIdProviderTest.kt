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

package com.wire.kalium.logic.cache

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.common.functional.Either
import dev.mokkery.answering.returns
import dev.mokkery.everySuspend
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class SelfConversationIdProviderTest {

    @Test
    fun givenMLSClientHasBeenRegistered_thenMLSAndProteusSelfConversationAreReturned() = runTest {
        val (arrangement, selfConversationIdProvider) = Arrangement()
            .withHasRegisteredMLSClient((Either.Right(true)))
            .withMLSSelfConversationId(Either.Right(Arrangement.MLS_SELF_CONVERSATION_ID))
            .withProteusSelfConversationId(Either.Right(Arrangement.PROTEUS_SELF_CONVERSATION_ID))
            .arrange()

        selfConversationIdProvider().also {
            assertIs<Either.Right<List<ConversationId>>>(it)
            assertEquals(listOf(Arrangement.PROTEUS_SELF_CONVERSATION_ID, Arrangement.MLS_SELF_CONVERSATION_ID), it.value)
        }

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.proteusSelfConversationIdProvider()
        }

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.mlsSelfConversationIdProvider.invoke()
        }
    }

    @Test
    fun givenMLSClientHasNotBeenRegistered_thenProteusSelfConversationIsReturned() = runTest {
        val (arrangement, selfConversationIdProvider) = Arrangement()
            .withHasRegisteredMLSClient((Either.Right(false)))
            .withProteusSelfConversationId(Either.Right(Arrangement.PROTEUS_SELF_CONVERSATION_ID))
            .arrange()

        selfConversationIdProvider().also {
            assertIs<Either.Right<List<ConversationId>>>(it)
            assertEquals(listOf(Arrangement.PROTEUS_SELF_CONVERSATION_ID), it.value)
        }

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.proteusSelfConversationIdProvider.invoke()
        }
    }

    @Test
    fun givenFailure_thenErrorIsPropagated() = runTest {
        val expected = Either.Left(StorageFailure.DataNotFound)
        val (arrangement, selfConversationIdProvider) = Arrangement()
            .withHasRegisteredMLSClient((Either.Right(false)))
            .withProteusSelfConversationId(expected)
            .arrange()

        selfConversationIdProvider().also {
            assertIs<Either.Left<StorageFailure>>(it)
            assertEquals(expected.value, it.value)
        }

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.proteusSelfConversationIdProvider.invoke()
        }
    }

    private class Arrangement {

        val clientRepository = mock<ClientRepository>()
        val proteusSelfConversationIdProvider = mock<ProteusSelfConversationIdProvider>()
        val mlsSelfConversationIdProvider = mock<MLSSelfConversationIdProvider>()

        val selfConversationIdProvider: SelfConversationIdProvider = SelfConversationIdProviderImpl(
            clientRepository,
            mlsSelfConversationIdProvider,
            proteusSelfConversationIdProvider
        )

        suspend fun withHasRegisteredMLSClient(result: Either<CoreFailure, Boolean>): Arrangement = apply {
            everySuspend {
                clientRepository.hasRegisteredMLSClient()
            } returns result
        }

        suspend fun withProteusSelfConversationId(result: Either<StorageFailure, ConversationId>): Arrangement = apply {
            everySuspend {
                proteusSelfConversationIdProvider.invoke()
            } returns result
        }

        suspend fun withMLSSelfConversationId(result: Either<StorageFailure, ConversationId>): Arrangement = apply {
            everySuspend {
                mlsSelfConversationIdProvider.invoke()
            } returns result
        }

        fun arrange() = this to selfConversationIdProvider

        companion object {
            val MLS_SELF_CONVERSATION_ID = ConversationId("mls_self", "conv_domain")
            val PROTEUS_SELF_CONVERSATION_ID = ConversationId("proteus_self", "conv_domain")
        }
    }
}
