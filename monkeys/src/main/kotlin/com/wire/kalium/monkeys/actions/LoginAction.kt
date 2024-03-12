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
import com.wire.kalium.monkeys.logger
import com.wire.kalium.monkeys.model.ActionType
import com.wire.kalium.monkeys.model.Event
import com.wire.kalium.monkeys.model.EventType
import com.wire.kalium.monkeys.pool.ConversationPool
import com.wire.kalium.monkeys.pool.MonkeyPool
import kotlinx.coroutines.delay

open class LoginAction(val config: ActionType.Login, sender: suspend (Event) -> Unit) : Action(sender) {
    override suspend fun execute(coreLogic: CoreLogic, monkeyPool: MonkeyPool, conversationPool: ConversationPool) {
        val monkeys = monkeys(monkeyPool)
        logger.i("Logging ${monkeys.count()} monkeys in")
        monkeys.forEach {
            it.login(coreLogic, monkeyPool::loggedIn)
            this.sender(Event(it.internalId, EventType.Login))
        }
        if (config.duration > 0u) {
            delay(config.duration.toLong())
            monkeys.forEach {
                it.logout(monkeyPool::loggedOut)
                this.sender(Event(it.internalId, EventType.Logout))
            }
        }
    }

    open fun monkeys(monkeyPool: MonkeyPool): List<Monkey> {
        return monkeyPool.randomLoggedOutMonkeys(this.config.userCount)
    }
}
