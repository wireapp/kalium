package com.wire.kalium.model.conversation

import com.wire.kalium.api.json.ValidJsonProvider
import com.wire.kalium.network.api.base.authenticated.conversation.UpdateConversationAccessRequest
import com.wire.kalium.network.api.base.model.ConversationAccessDTO
import com.wire.kalium.network.api.base.model.ConversationAccessRoleDTO

object UpdateConversationAccessRequestJson {

    private val updateConversationAccessRequest = UpdateConversationAccessRequest(
        setOf(ConversationAccessDTO.PRIVATE),
        setOf(ConversationAccessRoleDTO.TEAM_MEMBER)
    )

    val v0 = ValidJsonProvider(
        updateConversationAccessRequest
    ) {
        """
        |{
        |   "access": [
        |       "${it.access.first()}"
        |   ],
        |   "access_role_v2": [
        |       "${it.accessRole.first()}"
        |   ]
        |}
        """.trimMargin()
    }

    val v3 = ValidJsonProvider(
        updateConversationAccessRequest
    ) {
        """
        |{
        |   "access": [
        |       "${it.access.first()}"
        |   ],
        |   "access_role": [
        |       "${it.accessRole.first()}"
        |   ]
        |}
        """.trimMargin()
    }
}
