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
    "👀", "🦭", "😵‍💫", "👨‍🍳", "🍌", "🍆", "👨‍🌾", "🏄‍", "🥶", "🤤", "🙈", "🙊", "🐒", "🙉", "🦍", "🐵"
)
private val MESSAGES = listOf(
    """
        Hey there,
        
        I'm really in the mood for some bananas, and I was wondering if you could lend a hand. I'd truly appreciate it. Bananas have a 
        special place in my heart, and your help would brighten my day.
        
        Thanks for thinking it over.
    """,
    """
        Hey,

        I've got a hankering for some bananas. Any chance you could help me out?

        Appreciate it!
    """,
    """
        Hello,

        I'm on the hunt for bananas, and I'm reaching out to see if you or your group could assist. Your help would mean a lot to me.

        Thanks in advance.
    """,
    """
        Hey,

        I've got this banana craving that won't quit. Can you come to the rescue?

        Much appreciated!
    """,
    """
        Hi there,

        I'm in need of some bananas, and I'm wondering if you or your group could lend a hand. Your support would make my day.

        Thanks a bunch!  
    """,
    """
        Hello,

        I'm really feeling the banana vibe right now. Could you or your group be the banana heroes I'm looking for?

        Thanks in advance.
    """,
    """
       Hey,

       Bananas are calling my name. Any chance you can help satisfy my fruity desires?

       Thanks a bunch! 
    """,
    """
       Hi,

       I'm on a mission to find bananas, and I'm hoping you or your group can assist. Your kindness would be greatly appreciated.

       Thanks for considering. 
    """,
    """
       Hey there,

       I've got a strong craving for bananas. Could you lend a hand in satisfying it?

       Many thanks! 
    """,
    """
       Hello,

       I'm in the mood for some bananas. Can you or your group help me out? It would mean a lot.

       Thanks in advance! 
    """,
    """
       Hey,

       I've got this banana hankering that won't quit. Can you be the banana hero I need?

       Much appreciated! 
    """,
)

class SendMessageAction(val config: ActionType.SendMessage) : Action() {
    override suspend fun execute(coreLogic: CoreLogic, monkeyPool: MonkeyPool) {
        repeat(this.config.count.toInt()) { i ->
            if (this.config.targets.isNotEmpty()) {
                this.config.targets.forEach { target ->
                    if (target == ONE_2_1) {
                        val monkeys = monkeyPool.randomLoggedInMonkeys(this.config.userCount)
                        monkeys.forEach { monkey ->
                            val targetMonkey = monkey.randomPeer(monkeyPool)
                            monkey.sendDirectMessageTo(targetMonkey, randomMessage())
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
        val message = randomMessage()
        monkey.sendMessageTo(this.conversation.id, message)
    }
}

private fun randomMessage(): String {
    return """
        ${MESSAGES.random()}
        ${EMOJI.random()}
    """.trimIndent()
}
