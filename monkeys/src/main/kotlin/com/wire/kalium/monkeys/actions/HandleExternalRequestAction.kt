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
import com.wire.kalium.monkeys.model.ActionType
import com.wire.kalium.monkeys.pool.ConversationPool
import com.wire.kalium.monkeys.pool.MonkeyPool

private val DIRECT_MESSAGES = arrayOf(
    """
        Hey there,

        I hope you're doing well. I've got a bit of a craving for bananas, and I was wondering if you might be able to share a few with me? 
        It would mean a lot. 😊

        Thanks a bunch,
        A friendly monkey 🍌🐵
    """.trimIndent(), """
        Yo,

        I'm in need of some bananas, my friend. Can you hook me up? I'd appreciate it big time.

        Respect,
        A neutral monkey 🍌
    """.trimIndent(), """
        Listen up,

        I ain't messin' around. I want them bananas, and I want 'em now. You better deliver or there'll be consequences.

        No games,
        An evil monkey 🍌👿💀
    """.trimIndent()
)

class HandleExternalRequestAction(val config: ActionType.HandleExternalRequest) : Action({}) {
    override suspend fun execute(coreLogic: CoreLogic, monkeyPool: MonkeyPool, conversationPool: ConversationPool) {
        val monkeys = monkeyPool.randomMonkeysWithConnectionRequests(config.userCount)
        monkeys.forEach { (monkey, pendingConnections) ->
            if (config.shouldAccept) {
                val otherUser = Monkey.external(pendingConnections.random())
                monkey.acceptRequest(otherUser)
                monkey.sendDirectMessageTo(otherUser, config.greetMessage.ifBlank { DIRECT_MESSAGES.random() })
            } else {
                monkey.rejectRequest(Monkey.external(pendingConnections.random()))
            }
        }
    }
}
