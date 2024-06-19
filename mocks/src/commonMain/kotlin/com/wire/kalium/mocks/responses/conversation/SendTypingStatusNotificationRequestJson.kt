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
import com.wire.kalium.network.api.base.authenticated.conversation.TypingIndicatorStatus
import com.wire.kalium.network.api.base.authenticated.conversation.TypingIndicatorStatusDTO

object SendTypingStatusNotificationRequestJson {

    private val jsonProvider = { serializable: TypingIndicatorStatusDTO ->
        """
        |{
        |   "status": "${serializable.status.value}"
        |}
        """.trimMargin()
    }

    fun createValid(typingIndicatorStatus: TypingIndicatorStatus) =
        ValidJsonProvider(TypingIndicatorStatusDTO(typingIndicatorStatus), jsonProvider)

}
