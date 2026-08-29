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

package com.wire.kalium.logic.cache

import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.data.client.MLSClientRegistrationStatusProvider
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.persistence.dao.QualifiedIDEntity
import com.wire.kalium.persistence.dao.conversation.ConversationDAO
import com.wire.kalium.persistence.dao.conversation.ConversationEntity
import java.lang.reflect.Proxy
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlinx.coroutines.test.runTest

class DaoBackedSelfConversationIdProviderTest {

    @Test
    fun givenProtocolProviders_whenReading_thenEachSelectsItsProtocolAndMapsTheId() = runTest {
        val calls = mutableListOf<ConversationEntity.Protocol>()
        val dao = conversationDAO { protocol ->
            calls += protocol
            when (protocol) {
                ConversationEntity.Protocol.PROTEUS -> proteusEntity
                ConversationEntity.Protocol.MLS -> mlsEntity
                else -> error("Unexpected self-conversation protocol: $protocol")
            }
        }

        val proteus = ProteusSelfConversationIdProviderImpl(dao)()
        val mls = MLSSelfConversationIdProviderImpl(dao)()

        assertEquals(Either.Right(proteusConversationId), proteus)
        assertEquals(Either.Right(mlsConversationId), mls)
        assertEquals(listOf(ConversationEntity.Protocol.PROTEUS, ConversationEntity.Protocol.MLS), calls)
    }

    @Test
    fun givenMissingDaoValue_whenReading_thenDataNotFoundIsReturned() = runTest {
        val dao = conversationDAO { null }

        assertEquals(Either.Left(StorageFailure.DataNotFound), ProteusSelfConversationIdProviderImpl(dao)())
        assertEquals(Either.Left(StorageFailure.DataNotFound), MLSSelfConversationIdProviderImpl(dao)())
    }

    @Test
    fun givenDaoFailure_whenReading_thenExceptionIsWrapped() = runTest {
        val expected = IllegalStateException("self-conversation read failed")
        val provider = ProteusSelfConversationIdProviderImpl(conversationDAO { throw expected })

        assertEquals(Either.Left(StorageFailure.Generic(expected)), provider())
    }

    @Test
    fun givenDaoCancellation_whenReading_thenCancellationEscapes() = runTest {
        val expected = CancellationException("self-conversation read cancelled")
        val provider = MLSSelfConversationIdProviderImpl(conversationDAO { throw expected })

        val actual = assertFailsWith<CancellationException> { provider() }

        assertSame(expected, actual)
    }

    @Test
    fun givenFirstLookupFailure_whenReadingAgain_thenEachProtocolRetriesAndCachesOnlySuccess() = runTest {
        listOf(ConversationEntity.Protocol.PROTEUS, ConversationEntity.Protocol.MLS).forEach { protocol ->
            var calls = 0
            val dao = conversationDAO {
                calls += 1
                if (calls == 1) throw IllegalStateException("first read failed") else proteusEntity
            }
            val provider: suspend () -> Either<StorageFailure, ConversationId> = when (protocol) {
                ConversationEntity.Protocol.PROTEUS -> {
                    val value = ProteusSelfConversationIdProviderImpl(dao)
                    suspend { value() }
                }
                ConversationEntity.Protocol.MLS -> {
                    val value = MLSSelfConversationIdProviderImpl(dao)
                    suspend { value() }
                }
                else -> error("Unexpected self-conversation protocol: $protocol")
            }

            assertEquals(true, provider() is Either.Left)
            assertEquals(Either.Right(proteusConversationId), provider())
            assertEquals(Either.Right(proteusConversationId), provider())
            assertEquals(2, calls)
        }
    }

    @Test
    fun givenRepeatedAggregateResolution_whenReading_thenDaoOrderAndSuccessOnlyCachesArePreserved() = runTest {
        val calls = mutableListOf<String>()
        val dao = conversationDAO { protocol ->
            calls += protocol.name
            when (protocol) {
                ConversationEntity.Protocol.PROTEUS -> proteusEntity
                ConversationEntity.Protocol.MLS -> mlsEntity
                else -> error("Unexpected self-conversation protocol: $protocol")
            }
        }
        val provider = SelfConversationIdProviderImpl(
            mlsClientRegistrationStatusProvider = MLSClientRegistrationStatusProvider {
                calls += "REGISTRATION"
                Either.Right(true)
            },
            mlsSelfConversationIdProvider = MLSSelfConversationIdProviderImpl(dao),
            proteusSelfConversationIdProvider = ProteusSelfConversationIdProviderImpl(dao),
        )

        assertEquals(Either.Right(listOf(proteusConversationId, mlsConversationId)), provider())
        assertEquals(Either.Right(listOf(proteusConversationId, mlsConversationId)), provider())

        assertEquals(listOf("PROTEUS", "REGISTRATION", "MLS", "REGISTRATION"), calls)
    }

    private fun conversationDAO(
        lookup: (ConversationEntity.Protocol) -> QualifiedIDEntity?,
    ): ConversationDAO = Proxy.newProxyInstance(
        ConversationDAO::class.java.classLoader,
        arrayOf(ConversationDAO::class.java),
    ) { _, method, arguments ->
        when (method.name) {
            "getSelfConversationId" -> lookup(arguments?.get(0) as ConversationEntity.Protocol)
            "toString" -> "ConversationDAOTestProxy"
            else -> error("Unexpected ConversationDAO call: ${method.name}")
        }
    } as ConversationDAO

    private companion object {
        val proteusEntity = QualifiedIDEntity("proteus-self", "wire.example")
        val mlsEntity = QualifiedIDEntity("mls-self", "wire.example")
        val proteusConversationId = ConversationId("proteus-self", "wire.example")
        val mlsConversationId = ConversationId("mls-self", "wire.example")
    }
}
