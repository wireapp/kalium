package com.wire.kalium.logic.framework

import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.persistence.dao.ConversationEntity
import com.wire.kalium.persistence.dao.QualifiedIDEntity

object TestConversation {
    val ID = ConversationId("valueConvo", "domainConvo")

    val ENTITY_ID = QualifiedIDEntity("valueConversation", "domainConversation")
    val ENTITY = ConversationEntity(ENTITY_ID, "convo name", ConversationEntity.Type.SELF, "teamId")
}
