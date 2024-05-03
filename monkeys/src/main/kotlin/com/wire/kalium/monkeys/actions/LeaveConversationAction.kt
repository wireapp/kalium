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
package com.wire.kalium.monkeys.actions

import com.wire.kalium.logic.CoreLogic
import com.wire.kalium.monkeys.conversation.Monkey
import com.wire.kalium.monkeys.conversation.MonkeyConversation
import com.wire.kalium.monkeys.model.ActionType
import com.wire.kalium.monkeys.model.Event
import com.wire.kalium.monkeys.model.EventType
import com.wire.kalium.monkeys.pool.ConversationPool
import com.wire.kalium.monkeys.pool.MonkeyPool

open class LeaveConversationAction(val config: ActionType.LeaveConversation, sender: suspend (Event) -> Unit) : Action(sender) {
    override suspend fun execute(coreLogic: CoreLogic, monkeyPool: MonkeyPool, conversationPool: ConversationPool) {
        val targets = leavers(monkeyPool, conversationPool)
        targets.forEach { (conv, leavers) ->
            // conversation admin should never leave the group
            leavers.filter { it.monkeyType.userData() != conv.creator.monkeyType.userData() }.forEach {
                it.leaveConversation(conv.conversationId, conv.creator.monkeyType.userId())
                this.sender(Event(it.internalId, EventType.LeaveConversation(conv.conversationId)))
            }
        }
    }

    open suspend fun leavers(monkeyPool: MonkeyPool, conversationPool: ConversationPool): List<Pair<MonkeyConversation, List<Monkey>>> {
        return conversationPool.randomDynamicConversations(this.config.countGroups.toInt()).map {
            val leavers = it.randomMonkeys(this.config.userCount)
            Pair(it, leavers)
        }
    }
}
