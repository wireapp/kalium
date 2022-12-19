package com.wire.kalium.logic.data.conversation

import com.wire.kalium.logic.data.id.IdMapper
import com.wire.kalium.network.api.base.authenticated.conversation.ReceiptMode
import com.wire.kalium.persistence.dao.ConversationEntity
import io.mockative.Mock
import io.mockative.classOf
import io.mockative.mock
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class ReceiptModeMapperTest {

    @Mock
    val idMapper = mock(classOf<IdMapper>())

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
    fun givenAConversationReceiptModeNull_whenMappingToDaoModel_thenReturnConversationEntityReceiptModeDisabled() {
        // given
        val conversationReceiptMode = null

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
    fun givenAnApiReceiptModeNull_whenMappingFromApiToDaoModel_thenReturnConversationEntityReceiptModeDisabled() {
        // given
        val conversationReceiptMode = null

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
    fun givenAnApiReceiptModeNull_whenMappingFromApiToModel_thenReturnConversationReceiptModeDisabled() {
        // given
        val conversationReceiptMode = null

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
    fun givenAConversationEntityReceiptModeNull_whenMappingFromEntityToModel_thenReturnConversationReceiptModeDisabled() {
        // given
        val conversationReceiptMode = null

        val expectedResult = Conversation.ReceiptMode.DISABLED

        // when
        val result = receiptModeMapper.fromEntityToModel(conversationReceiptMode)

        // then
        assertEquals(expectedResult, result)
    }
}
