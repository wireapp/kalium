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

package com.wire.kalium.logic.data.conversation

import com.wire.kalium.logic.data.id.IdMapper
import com.wire.kalium.network.api.base.authenticated.conversation.ReceiptMode
import com.wire.kalium.persistence.dao.conversation.ConversationEntity
import io.mockative.Mock
import io.mockative.mock
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class ReceiptModeMapperTest {

    @Mock
    val idMapper = mock(IdMapper::class)

    private lateinit var receiptModeMapper: ReceiptModeMapper

    @BeforeTest
    fun setUp() {
        receiptModeMapper = ReceiptModeMapperImpl(idMapper)
    }

    @Test
    fun givenAConversationReceiptModeEnabled_whenMappingToDaoModel_thenReturnConversationEntityReceiptModeEnabled() {
        // given
        val conversationReceiptMode = Conversation.ReceiptMode.ENABLED

        val expectedResult = ConversationEntity.ReceiptMode.ENABLED

        // when
        val result = receiptModeMapper.toDaoModel(conversationReceiptMode)

        // then
        assertEquals(expectedResult, result)
    }

    @Test
    fun givenAConversationReceiptModeDisabled_whenMappingToDaoModel_thenReturnConversationEntityReceiptModeDisabled() {
        // given
        val conversationReceiptMode = Conversation.ReceiptMode.DISABLED

        val expectedResult = ConversationEntity.ReceiptMode.DISABLED

        // when
        val result = receiptModeMapper.toDaoModel(conversationReceiptMode)

        // then
        assertEquals(expectedResult, result)
    }

    @Test
    fun givenAnApiReceiptModeEnabled_whenMappingFromApiToDaoModel_thenReturnConversationEntityReceiptModeEnabled() {
        // given
        val conversationReceiptMode = ReceiptMode.ENABLED

        val expectedResult = ConversationEntity.ReceiptMode.ENABLED

        // when
        val result = receiptModeMapper.fromApiToDaoModel(conversationReceiptMode)

        // then
        assertEquals(expectedResult, result)
    }

    @Test
    fun givenAnApiReceiptModeDisabled_whenMappingFromApiToDaoModel_thenReturnConversationEntityReceiptModeDisabled() {
        // given
        val conversationReceiptMode = ReceiptMode.DISABLED

        val expectedResult = ConversationEntity.ReceiptMode.DISABLED

        // when
        val result = receiptModeMapper.fromApiToDaoModel(conversationReceiptMode)

        // then
        assertEquals(expectedResult, result)
    }

    @Test
    fun givenAnApiReceiptModeEnabled_whenMappingFromApiToModel_thenReturnConversationReceiptModeEnabled() {
        // given
        val conversationReceiptMode = ReceiptMode.ENABLED

        val expectedResult = Conversation.ReceiptMode.ENABLED

        // when
        val result = receiptModeMapper.fromApiToModel(conversationReceiptMode)

        // then
        assertEquals(expectedResult, result)
    }

    @Test
    fun givenAnApiReceiptModeDisabled_whenMappingFromApiToModel_thenReturnConversationReceiptModeDisabled() {
        // given
        val conversationReceiptMode = ReceiptMode.DISABLED

        val expectedResult = Conversation.ReceiptMode.DISABLED

        // when
        val result = receiptModeMapper.fromApiToModel(conversationReceiptMode)

        // then
        assertEquals(expectedResult, result)
    }

    @Test
    fun givenAConversationEntityReceiptModeEnabled_whenMappingFromEntityToModel_thenReturnConversationReceiptModeEnabled() {
        // given
        val conversationReceiptMode = ConversationEntity.ReceiptMode.ENABLED

        val expectedResult = Conversation.ReceiptMode.ENABLED

        // when
        val result = receiptModeMapper.fromEntityToModel(conversationReceiptMode)

        // then
        assertEquals(expectedResult, result)
    }

    @Test
    fun givenAConversationEntityReceiptModeDisabled_whenMappingFromEntityToModel_thenReturnConversationReceiptModeDisabled() {
        // given
        val conversationReceiptMode = ConversationEntity.ReceiptMode.DISABLED

        val expectedResult = Conversation.ReceiptMode.DISABLED

        // when
        val result = receiptModeMapper.fromEntityToModel(conversationReceiptMode)

        // then
        assertEquals(expectedResult, result)
    }

    @Test
    fun givenAConversationReceiptModeEnabled_whenMappingFromModelToApi_thenReturnApiReceiptModeEnabled() {
        // given
        val conversationReceiptMode = Conversation.ReceiptMode.ENABLED

        val expectedResult = ReceiptMode.ENABLED

        // when
        val result = receiptModeMapper.fromModelToApi(conversationReceiptMode)

        // then
        assertEquals(expectedResult, result)
    }

    @Test
    fun givenAConversationReceiptModeDisabled_whenMappingFromModelToApi_thenReturnApiReceiptModeDisabled() {
        // given
        val conversationReceiptMode = Conversation.ReceiptMode.DISABLED

        val expectedResult = ReceiptMode.DISABLED

        // when
        val result = receiptModeMapper.fromModelToApi(conversationReceiptMode)

        // then
        assertEquals(expectedResult, result)
    }
}
