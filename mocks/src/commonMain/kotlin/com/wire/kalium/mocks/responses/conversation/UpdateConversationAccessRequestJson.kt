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

package com.wire.kalium.mocks.responses.conversation

import com.wire.kalium.mocks.responses.ValidJsonProvider
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
