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
package com.wire.kalium.monkeys.conversation

import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.monkeys.MetricsCollector
import com.wire.kalium.monkeys.model.UserCount
import com.wire.kalium.monkeys.pool.resolveUserCount
import io.micrometer.core.instrument.Tag

/**
 * This is a shallow wrapper over the conversation (it contains only details), since the operations need to be done on the
 * user scope, they're done inside the [Monkey] class.
 */
class MonkeyConversation(
    val creator: Monkey,
    val conversationId: ConversationId,
    val isDestroyable: Boolean = true,
    monkeyList: List<Monkey>
) {
    private var participants: MutableSet<Monkey>

    init {
        this.participants = mutableSetOf(creator).also { it.addAll(monkeyList) }
        MetricsCollector.gaugeCollection("g_conversationMembers", listOf(Tag.of("id", conversationId.toString())), this.participants)
    }

    /**
     * Return a [count] number of random [Monkey] from the conversation.
     * It returns only logged-in users, if there are none an empty list will be returned
     */
    suspend fun randomMonkeys(userCount: UserCount): List<Monkey> {
        val count = resolveUserCount(userCount, this.participants.count().toUInt())
        val tempList = participants.toMutableList()
        tempList.shuffle()
        return tempList.filter { it.isSessionActive() }.take(count.toInt())
    }

    suspend fun addMonkeys(monkeys: List<Monkey>) {
        this.participants.addAll(monkeys)
        this.creator.addMonkeysToConversation(conversationId, monkeys)
    }

    suspend fun removeMonkey(monkey: Monkey) {
        this.participants.remove(monkey)
        this.creator.removeMonkeyFromConversation(conversationId, monkey)
    }

    suspend fun destroy(callback: (ConversationId) -> Unit) {
        this.creator.destroyConversation(this.conversationId, this.creator.monkeyType.userId())
        callback(this.conversationId)
    }

    fun membersIds(): List<UserId> {
        return this.participants.map { it.monkeyType.userId() }
    }

    fun members(): Set<Monkey> {
        return this.participants
    }
}
