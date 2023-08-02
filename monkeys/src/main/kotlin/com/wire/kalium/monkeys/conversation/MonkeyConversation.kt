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
package com.wire.kalium.monkeys.conversation

import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.monkeys.importer.UserCount
import com.wire.kalium.monkeys.pool.ConversationPool
import com.wire.kalium.monkeys.pool.resolveUserCount

/**
 * This is a shallow wrapper over the conversation (it contains only details), since the operations need to be done on the
 * user scope, they're done inside the [Monkey] class.
 */
class MonkeyConversation(
    val creator: Monkey,
    val conversation: Conversation,
    val isDestroyable: Boolean = true
) {
    private var participants: MutableList<Monkey> = mutableListOf(creator)

    /**
     * Return a random [Monkey] from the group. The group will always have at least its creator in it.
     */
    fun randomMonkey(): Monkey {
        return this.participants.random()
    }

    /**
     * Return a [count] number of random [Monkey] from the conversation.
     * It returns only logged-in users, if there are none an empty list will be returned
     */
    fun randomMonkeys(userCount: UserCount): List<Monkey> {
        val count = resolveUserCount(userCount, this.participants.count().toUInt())
        val tempList = participants.toMutableList()
        tempList.shuffle()
        return tempList.filter { it.isSessionActive() }.take(count.toInt())
    }

    suspend fun addMonkeys(monkeys: List<Monkey>) {
        this.participants.addAll(monkeys)
        this.creator.addMonkeysToConversation(conversation.id, monkeys)
    }

    suspend fun removeMonkey(monkey: Monkey) {
        this.participants.remove(monkey)
        this.creator.removeMonkeyFromConversation(conversation.id, monkey)
    }

    suspend fun destroy() {
        this.creator.destroyConversation(this.conversation.id)
        ConversationPool.conversationDestroyed(this.conversation.id)
    }
}
