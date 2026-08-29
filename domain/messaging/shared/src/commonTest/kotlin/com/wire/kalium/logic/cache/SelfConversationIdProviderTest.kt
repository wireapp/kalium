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

import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.data.client.MLSClientRegistrationStatusProvider
import com.wire.kalium.logic.data.id.ConversationId
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class SelfConversationIdProviderTest {

    @Test
    fun givenRegisteredMLSClient_whenResolving_thenProteusIsRequestedFirstAndBothIdsAreOrdered() = runTest {
        val calls = mutableListOf<String>()
        val provider = provider(
            registration = {
                calls += "registration"
                Either.Right(true)
            },
            mls = {
                calls += "mls"
                Either.Right(mlsConversationId)
            },
            proteus = {
                calls += "proteus"
                Either.Right(proteusConversationId)
            },
        )

        assertEquals(Either.Right(listOf(proteusConversationId, mlsConversationId)), provider())
        assertEquals(listOf("proteus", "registration", "mls"), calls)
    }

    @Test
    fun givenUnregisteredMLSClient_whenResolving_thenOnlyProteusIdIsReturned() = runTest {
        val calls = mutableListOf<String>()
        val provider = provider(
            registration = {
                calls += "registration"
                Either.Right(false)
            },
            mls = {
                calls += "mls"
                Either.Right(mlsConversationId)
            },
            proteus = {
                calls += "proteus"
                Either.Right(proteusConversationId)
            },
        )

        assertEquals(Either.Right(listOf(proteusConversationId)), provider())
        assertEquals(listOf("proteus", "registration"), calls)
    }

    @Test
    fun givenRegistrationStatusFailure_whenResolving_thenFailureIsTreatedAsFalseAfterProteusLookup() = runTest {
        val calls = mutableListOf<String>()
        val provider = provider(
            registration = {
                calls += "registration"
                Either.Left(StorageFailure.DataNotFound)
            },
            mls = {
                calls += "mls"
                Either.Right(mlsConversationId)
            },
            proteus = {
                calls += "proteus"
                Either.Right(proteusConversationId)
            },
        )

        assertEquals(Either.Right(listOf(proteusConversationId)), provider())
        assertEquals(listOf("proteus", "registration"), calls)
    }

    @Test
    fun givenProtocolLookupFailure_whenResolving_thenFailureIsPropagated() = runTest {
        val provider = provider(
            registration = { Either.Right(false) },
            mls = { Either.Right(mlsConversationId) },
            proteus = { Either.Left(StorageFailure.DataNotFound) },
        )

        assertEquals(Either.Left(StorageFailure.DataNotFound), provider())
    }

    @Test
    fun givenProteusFailureAndRegisteredMLS_whenResolving_thenRemainingLookupsKeepTheirExistingOrder() = runTest {
        val calls = mutableListOf<String>()
        val provider = provider(
            registration = {
                calls += "registration"
                Either.Right(true)
            },
            mls = {
                calls += "mls"
                Either.Right(mlsConversationId)
            },
            proteus = {
                calls += "proteus"
                Either.Left(StorageFailure.DataNotFound)
            },
        )

        assertEquals(Either.Left(StorageFailure.DataNotFound), provider())
        assertEquals(listOf("proteus", "registration", "mls"), calls)
    }

    @Test
    fun givenMLSLookupFailure_whenResolving_thenFailureIsPropagated() = runTest {
        val provider = provider(
            registration = { Either.Right(true) },
            mls = { Either.Left(StorageFailure.DataNotFound) },
            proteus = { Either.Right(proteusConversationId) },
        )

        assertEquals(Either.Left(StorageFailure.DataNotFound), provider())
    }

    @Test
    fun givenRepeatedResolution_whenInvoking_thenProviderCallOrderIsPreservedEveryTime() = runTest {
        val calls = mutableListOf<String>()
        val provider = provider(
            registration = {
                calls += "registration"
                Either.Right(true)
            },
            mls = {
                calls += "mls"
                Either.Right(mlsConversationId)
            },
            proteus = {
                calls += "proteus"
                Either.Right(proteusConversationId)
            },
        )

        provider()
        provider()

        assertEquals(
            listOf("proteus", "registration", "mls", "proteus", "registration", "mls"),
            calls,
        )
    }

    private fun provider(
        registration: suspend () -> Either<StorageFailure, Boolean>,
        mls: suspend () -> Either<StorageFailure, ConversationId>,
        proteus: suspend () -> Either<StorageFailure, ConversationId>,
    ): SelfConversationIdProvider = SelfConversationIdProviderImpl(
        mlsClientRegistrationStatusProvider = MLSClientRegistrationStatusProvider { registration() },
        mlsSelfConversationIdProvider = MLSSelfConversationIdProvider { mls() },
        proteusSelfConversationIdProvider = ProteusSelfConversationIdProvider { proteus() },
    )

    private companion object {
        val mlsConversationId = ConversationId("mls-self", "wire.example")
        val proteusConversationId = ConversationId("proteus-self", "wire.example")
    }
}
