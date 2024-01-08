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

package com.wire.kalium.logic.framework

import com.wire.kalium.logic.data.client.Client
import com.wire.kalium.logic.data.client.ClientType
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.user.UserId
import kotlinx.datetime.Instant

object TestClient {
    val CLIENT_ID = ClientId("test")

    val CLIENT = Client(
        CLIENT_ID,
        ClientType.Permanent,
        Instant.DISTANT_PAST,
        Instant.DISTANT_PAST,
        deviceType = null,
        model = null,
        label = "label",
        isVerified = false,
        isValid = true,
        mlsPublicKeys = null,
        isMLSCapable = false
    )

    val SELF_USER_ID = UserId("self-user-id", "domain")
    val CONVERSATION_ID = ConversationId("conversation-id", "domain")
    val USER_ID = UserId("client-id", "domain")
}
