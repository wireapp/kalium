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

import com.wire.kalium.logic.functional.Either
import com.wire.kalium.monkeys.actions.SendMessageAction
import com.wire.kalium.monkeys.conversation.Monkey
import com.wire.kalium.monkeys.conversation.MonkeyConversation
import com.wire.kalium.monkeys.model.ActionType
import com.wire.kalium.monkeys.model.EventType
import com.wire.kalium.monkeys.model.MonkeyId
import com.wire.kalium.monkeys.model.UserCount
import com.wire.kalium.monkeys.pool.ConversationPool
import com.wire.kalium.monkeys.pool.MonkeyPool

class SendMessageEventAction(private val monkeySender: MonkeyId, private val eventConfig: EventType.SendMessage) :
    SendMessageAction(ActionType.SendMessage(
        UserCount.single(), 1u, 1u
    ), {}) {
    override suspend fun sendersTargets(monkeyPool: MonkeyPool, conversationPool: ConversationPool):
            List<Either<List<Pair<Monkey, Monkey>>, List<Pair<MonkeyConversation, List<Monkey>>>>> {
        val sender = monkeyPool.getFromTeam(this.monkeySender.team, this.monkeySender.index)
        val receiver = conversationPool.getFromOldId(this.eventConfig.conversationId)
        return listOf(Either.Right(listOf(Pair(receiver, listOf(sender)))))
    }
}
