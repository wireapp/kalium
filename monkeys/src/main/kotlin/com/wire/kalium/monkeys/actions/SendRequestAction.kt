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
import com.wire.kalium.monkeys.model.ActionType
import com.wire.kalium.monkeys.model.Event
import com.wire.kalium.monkeys.model.EventType
import com.wire.kalium.monkeys.pool.ConversationPool
import com.wire.kalium.monkeys.pool.MonkeyPool
import kotlinx.coroutines.delay

open class SendRequestAction(val config: ActionType.SendRequest, sender: suspend (Event) -> Unit) : Action(sender) {
    override suspend fun execute(coreLogic: CoreLogic, monkeyPool: MonkeyPool, conversationPool: ConversationPool) {
        val monkeys = monkeyPool.randomLoggedInMonkeysFromTeam(this.config.originTeam, this.config.userCount)
        monkeys.forEach { origin ->
            val targets = monkeyPool.randomLoggedInMonkeysFromTeam(this.config.targetTeam, this.config.targetUserCount)
            targets.forEach {
                origin.sendRequest(it)
                this.sender(Event(origin.internalId, EventType.SendRequest(it.internalId)))
            }
            delay(this.config.delayResponse.toLong())
            if (this.config.shouldAccept) {
                targets.forEach {
                    it.acceptRequest(origin)
                    this.sender(Event(it.internalId, EventType.RequestResponse(origin.internalId, this.config.shouldAccept)))
                }
            } else {
                targets.forEach {
                    it.rejectRequest(origin)
                    this.sender(Event(it.internalId, EventType.RequestResponse(origin.internalId, this.config.shouldAccept)))
                }
            }
        }
    }
}
