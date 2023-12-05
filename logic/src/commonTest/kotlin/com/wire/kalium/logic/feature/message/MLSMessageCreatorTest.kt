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

package com.wire.kalium.logic.feature.message

import com.wire.kalium.cryptography.MLSClient
import com.wire.kalium.logic.data.client.MLSClientProvider
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.conversation.LegalHoldStatusMapper
import com.wire.kalium.logic.data.id.GroupID
import com.wire.kalium.logic.data.message.PlainMessageBlob
import com.wire.kalium.logic.data.message.ProtoContentMapper
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.logic.framework.TestMessage
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.util.shouldSucceed
import io.mockative.Mock
import io.mockative.anything
import io.mockative.classOf
import io.mockative.eq
import io.mockative.given
import io.mockative.mock
import io.mockative.once
import io.mockative.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test

class MLSMessageCreatorTest {

    @Mock
    private val mlsClientProvider = mock(MLSClientProvider::class)

    @Mock
    private val protoContentMapper = mock(ProtoContentMapper::class)

    @Mock
    private val conversationRepository = mock(ConversationRepository::class)

    @Mock
    private val legalHoldStatusMapper = mock(LegalHoldStatusMapper::class)


    private lateinit var mlsMessageCreator: MLSMessageCreator

    @BeforeTest
    fun setup() {
        mlsMessageCreator = MLSMessageCreatorImpl(
            conversationRepository,
            legalHoldStatusMapper,
            mlsClientProvider,
            SELF_USER_ID,
            protoContentMapper
        )
    }

    @Test
    fun givenMessage_whenCreatingMLSMessage_thenMLSClientShouldBeUsedToEncryptProtobufContent() = runTest {
        val encryptedData = byteArrayOf()
        given(mlsClientProvider)
            .suspendFunction(mlsClientProvider::getMLSClient)
            .whenInvokedWith(anything())
            .then { Either.Right(MLS_CLIENT) }

        given(conversationRepository)
            .suspendFunction(conversationRepository::observeLegalHoldForConversation)
            .whenInvokedWith(anything())
            .then { flowOf(Either.Right(Conversation.LegalHoldStatus.DISABLED)) }

        given(legalHoldStatusMapper)
            .function(legalHoldStatusMapper::mapLegalHoldConversationStatus)
            .whenInvokedWith(anything(), anything())
            .thenReturn(Conversation.LegalHoldStatus.DISABLED)

        given(MLS_CLIENT)
            .suspendFunction(MLS_CLIENT::encryptMessage)
            .whenInvokedWith(anything(), anything())
            .thenReturn(encryptedData)

        val plainData = byteArrayOf(0x42, 0x73)
        given(protoContentMapper)
            .function(protoContentMapper::encodeToProtobuf)
            .whenInvokedWith(anything())
            .thenReturn(PlainMessageBlob(plainData))

        mlsMessageCreator.createOutgoingMLSMessage(GROUP_ID, TestMessage.TEXT_MESSAGE).shouldSucceed {}

        verify(MLS_CLIENT)
            .function(MLS_CLIENT::encryptMessage)
            .with(eq(CRYPTO_GROUP_ID), eq(plainData))
            .wasInvoked(once)

        verify(conversationRepository)
            .suspendFunction(conversationRepository::observeLegalHoldForConversation)
            .with(anything())
            .wasInvoked(once)

        verify(legalHoldStatusMapper)
            .function(legalHoldStatusMapper::mapLegalHoldConversationStatus)
            .with(anything(), anything())
            .wasInvoked(once)
    }

    private companion object {
        val SELF_USER_ID = UserId("user-id", "domain")
        val GROUP_ID = GroupID("groupId")
        val CRYPTO_GROUP_ID = MapperProvider.idMapper().toCryptoModel(GroupID("groupId"))
        val MLS_CLIENT = mock(classOf<MLSClient>())
    }

}
