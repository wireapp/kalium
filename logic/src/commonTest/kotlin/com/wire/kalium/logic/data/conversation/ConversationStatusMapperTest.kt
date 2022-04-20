package com.wire.kalium.logic.data.conversation

import com.wire.kalium.logic.data.id.IdMapper
import com.wire.kalium.network.api.conversation.MutedStatus
import com.wire.kalium.persistence.dao.ConversationEntity
import io.mockative.Mock
import io.mockative.classOf
import io.mockative.mock
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class ConversationStatusMapperTest {
    @Mock
    val idMapper = mock(classOf<IdMapper>())

    private lateinit var conversationStatusMapper: ConversationStatusMapper

    @BeforeTest
    fun setup() {
        conversationStatusMapper = ConversationStatusMapperImpl()
    }

    @Test
    fun givenAConversationModel_whenMappingToApiModel_thenTheMappingStatusesShouldBeOk() {
        val result = conversationStatusMapper.toApiModel(MutedConversationStatus.OnlyMentionsAllowed, 1649708697237L)

        assertEquals(MutedStatus.ONLY_MENTIONS_ALLOWED, result.otrMutedStatus)
        assertEquals("2022-04-11T20:24:57.237Z", result.otrMutedRef)
    }

    @Test
    fun givenAConversationModel_whenMappingToDaoModel_thenTheMappingStatusesShouldBeOk() {
        val result = conversationStatusMapper.toDaoModel(MutedConversationStatus.OnlyMentionsAllowed)

        assertEquals(ConversationEntity.MutedStatus.ONLY_MENTIONS_ALLOWED, result)
    }

}
