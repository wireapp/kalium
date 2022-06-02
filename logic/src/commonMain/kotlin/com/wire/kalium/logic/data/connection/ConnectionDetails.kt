package com.wire.kalium.logic.data.connection

import com.wire.kalium.logic.data.conversation.ConversationDetails
import com.wire.kalium.logic.data.user.Connection

data class ConnectionDetails(val connection: Connection, val conversation: ConversationDetails)
