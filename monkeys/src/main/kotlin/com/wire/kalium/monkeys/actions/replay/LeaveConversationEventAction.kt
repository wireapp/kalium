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
package com.wire.kalium.monkeys.actions.replay

import com.wire.kalium.monkeys.actions.LeaveConversationAction
import com.wire.kalium.monkeys.conversation.Monkey
import com.wire.kalium.monkeys.conversation.MonkeyConversation
import com.wire.kalium.monkeys.model.ActionType
import com.wire.kalium.monkeys.model.EventType
import com.wire.kalium.monkeys.model.MonkeyId
import com.wire.kalium.monkeys.model.UserCount
import com.wire.kalium.monkeys.pool.ConversationPool
import com.wire.kalium.monkeys.pool.MonkeyPool

class LeaveConversationEventAction(private val leaver: MonkeyId, private val eventConfig: EventType.LeaveConversation) :
    LeaveConversationAction(ActionType.LeaveConversation(1u, UserCount.single()), {}) {
    override suspend fun leavers(monkeyPool: MonkeyPool, conversationPool: ConversationPool): List<Pair<MonkeyConversation, List<Monkey>>> {
        val conversation = conversationPool.getFromOldId(this.eventConfig.conversationId)
        return listOf(Pair(conversation, listOf(monkeyPool.getFromTeam(this.leaver.team, this.leaver.index))))
    }
}
