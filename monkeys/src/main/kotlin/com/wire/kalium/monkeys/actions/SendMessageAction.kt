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
package com.wire.kalium.monkeys.actions

import com.wire.kalium.logic.CoreLogic
import com.wire.kalium.monkeys.conversation.MonkeyConversation
import com.wire.kalium.monkeys.importer.ActionType
import com.wire.kalium.monkeys.importer.UserCount
import com.wire.kalium.monkeys.logger
import com.wire.kalium.monkeys.pool.ConversationPool
import com.wire.kalium.monkeys.pool.MonkeyPool

private const val ONE_2_1: String = "One21"
private val EMOJI: List<String> = listOf(
    "👀", "🦭", "😵‍💫", "👨‍🍳",
    "🍌", "🍆", "👨‍🌾", "🏄‍",
    "🥶", "🤤", "🙈", "🙊",
    "🐒", "🙉", "🦍", "🐵"
)

class SendMessageAction(val config: ActionType.SendMessage) : Action() {

    override suspend fun execute(coreLogic: CoreLogic) {
        repeat(this.config.count.toInt()) { i ->
            if (this.config.targets.isNotEmpty()) {
                this.config.targets.forEach { target ->
                    if (target == ONE_2_1) {
                        val monkeys = MonkeyPool.randomLoggedInMonkeys(this.config.userCount)
                        monkeys.forEach { monkey ->
                            val targetMonkey = monkey.randomPeer()
                            monkey.sendDirectMessageTo(targetMonkey, randomMessage(targetMonkey.user.email, i))
                        }
                    } else {
                        ConversationPool.getFromPrefixed(target).forEach { conv ->
                            conv.sendMessage(this.config.userCount, i)
                        }
                    }
                }
            } else {
                val conversations = ConversationPool.randomConversations(this.config.countGroups)
                conversations.forEach {
                    it.sendMessage(this.config.userCount, i)
                }
            }
        }
    }
}

private suspend fun MonkeyConversation.sendMessage(userCount: UserCount, i: Int) {
    val monkeys = this.randomMonkeys(userCount)
    if (monkeys.isEmpty()) {
        logger.d("No monkey is logged in in the picked conversation")
    }
    monkeys.forEach { monkey ->
        val message = randomMessage(this.conversation.name ?: "fellow stranger", i)
        monkey.sendMessageTo(this.conversation.id, message)
    }
}

private fun randomMessage(target: String, i: Int): String {
    return "Hello $target. Give me $i banana(s). ${EMOJI.random()}"
}
