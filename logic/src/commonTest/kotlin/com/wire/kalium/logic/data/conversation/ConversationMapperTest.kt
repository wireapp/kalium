package com.wire.kalium.logic.data.conversation

import com.wire.kalium.logic.data.id.IdMapper
import com.wire.kalium.logic.data.id.QualifiedID
import com.wire.kalium.network.api.conversation.ConversationResponse
import io.mockative.Mock
import io.mockative.any
import io.mockative.classOf
import io.mockative.given
import io.mockative.mock
import kotlin.test.BeforeTest
import kotlin.test.Test

class ConversationMapperTest {

    @Mock
    val idMapper = mock(classOf<IdMapper>())

    @Mock
    val memberMapper = mock(classOf<MemberMapper>())

    private lateinit var conversationMapper: ConversationMapper

    @BeforeTest
    fun setup() {
        conversationMapper = ConversationMapperImpl(idMapper, memberMapper)
    }

    @Test
    fun givenAConversationResponse_whenMappingFromConversationResponse_thenShouldCallIdMapperToMapIds() {

        val conversationId = QualifiedID("value", "domain")
        given(conversationMapper)
            .function(idMapper::fromApiModel)
            .whenInvokedWith(any())
            .then { conversationId }

        conversationMapper.fromApiModel()
    }
}
