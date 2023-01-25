/*
 * Wire
 * Copyright (C) 2023 Wire Swiss GmbH
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

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.functional.Either
import io.mockative.Mock
import io.mockative.classOf
import io.mockative.given
import io.mockative.mock
import io.mockative.once
import io.mockative.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

@OptIn(ExperimentalCoroutinesApi::class)
class SelfConversationIdProviderTest {

    @Ignore
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

        verify(arrangement.proteusSelfConversationIdProvider)
            .suspendFunction(arrangement.proteusSelfConversationIdProvider::invoke)
            .wasInvoked(exactly = once)

        verify(arrangement.mlsSelfConversationIdProvider)
            .suspendFunction(arrangement.mlsSelfConversationIdProvider::invoke)
            .wasInvoked(exactly = once)
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

        verify(arrangement.proteusSelfConversationIdProvider)
            .suspendFunction(arrangement.proteusSelfConversationIdProvider::invoke)
            .wasInvoked(exactly = once)
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

        verify(arrangement.proteusSelfConversationIdProvider)
            .suspendFunction(arrangement.proteusSelfConversationIdProvider::invoke)
            .wasInvoked(exactly = once)
    }

    private class Arrangement {

        @Mock
        val clientRepository = mock(classOf<ClientRepository>())

        @Mock
        val proteusSelfConversationIdProvider = mock(classOf<ProteusSelfConversationIdProvider>())

        @Mock
        val mlsSelfConversationIdProvider = mock(classOf<MLSSelfConversationIdProvider>())

        val selfConversationIdProvider: SelfConversationIdProvider = SelfConversationIdProviderImpl(
            clientRepository,
            mlsSelfConversationIdProvider,
            proteusSelfConversationIdProvider
        )

        fun withHasRegisteredMLSClient(result: Either<CoreFailure, Boolean>): Arrangement = apply {
            given(clientRepository)
                .suspendFunction(clientRepository::hasRegisteredMLSClient)
                .whenInvoked()
                .thenReturn(result)
        }

        fun withProteusSelfConversationId(result: Either<StorageFailure, ConversationId>): Arrangement = apply {
            given(proteusSelfConversationIdProvider)
                .suspendFunction(proteusSelfConversationIdProvider::invoke)
                .whenInvoked()
                .thenReturn(result)
        }

        fun withMLSSelfConversationId(result: Either<StorageFailure, ConversationId>): Arrangement = apply {
            given(mlsSelfConversationIdProvider)
                .suspendFunction(mlsSelfConversationIdProvider::invoke)
                .whenInvoked()
                .thenReturn(result)
        }

        fun arrange() = this to selfConversationIdProvider

        companion object {
            val MLS_SELF_CONVERSATION_ID = ConversationId("mls_self", "conv_domain")
            val PROTEUS_SELF_CONVERSATION_ID = ConversationId("proteus_self", "conv_domain")
        }
    }
}
