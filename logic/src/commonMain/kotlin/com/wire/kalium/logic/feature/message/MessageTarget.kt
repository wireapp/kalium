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

import com.wire.kalium.logic.data.conversation.Recipient
import com.wire.kalium.logic.data.user.UserId

sealed class MessageTarget(open val ignoreSome: List<UserId>? = null) {
    class Client(
        val recipients: List<Recipient>,
        override val ignoreSome: List<UserId>? = null
    ) : MessageTarget(ignoreSome)

    class Conversation(
        override val ignoreSome: List<UserId>? = null
    ) : MessageTarget(ignoreSome)
}

fun MessageTarget.resetToInitialIntent(): MessageTarget {
    return when (this) {
        is MessageTarget.Client -> MessageTarget.Client(recipients, null)
        is MessageTarget.Conversation -> MessageTarget.Conversation(null)
    }
}
