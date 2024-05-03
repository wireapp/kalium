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

import com.wire.kalium.logic.CoreLogic
import com.wire.kalium.monkeys.actions.Action
import com.wire.kalium.monkeys.model.EventType
import com.wire.kalium.monkeys.model.MonkeyId
import com.wire.kalium.monkeys.pool.ConversationPool
import com.wire.kalium.monkeys.pool.MonkeyPool

class RequestResponseEventAction(private val requester: MonkeyId, private val config: EventType.RequestResponse) : Action({}) {
    override suspend fun execute(coreLogic: CoreLogic, monkeyPool: MonkeyPool, conversationPool: ConversationPool) {
        val sender = monkeyPool.getFromTeam(this.requester.team, this.requester.index)
        val receiver = monkeyPool.getFromTeam(this.config.targetMonkey.team, this.config.targetMonkey.index)
        if (this.config.shouldAccept) {
            sender.acceptRequest(receiver)
        } else {
            sender.rejectRequest(receiver)
        }
    }
}
