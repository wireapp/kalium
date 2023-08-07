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

/**
 * This is a shallow wrapper over the conversation (it contains only details), since the operations need to be done on the
 * user scope, they're done inside the [Monkey] class.
 */
class MonkeyConversation(
    val creator: Monkey,
    val conversation: Conversation,
    val isDestroyable: Boolean = true
) {
    var participants: MutableList<Monkey> = mutableListOf(creator)

    /**
     * Return a random [Monkey] from the group. The group will always have at least its creator in it.
     */
    fun randomMonkey(): Monkey {
        return this.participants.random()
    }

    /**
     * Return a [count] number of random [Monkey] from the conversation.
     * Does not check for duplication, so if the group has few participants, it is likely to return duplicate Monkeys
     */
    fun randomMonkeys(count: UInt): List<Monkey> {
        return (1u..count).map { this.randomMonkey() }
    }

    suspend fun addMonkeys(monkeys: List<Monkey>) {
        this.participants.addAll(monkeys)
        this.creator.addMonkeysToConversation(conversation.id, monkeys)
    }
}
