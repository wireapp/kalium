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
package com.wire.kalium.monkeys.pool

import com.wire.kalium.logic.data.conversation.ConversationOptions
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.monkeys.conversation.Monkey
import com.wire.kalium.monkeys.conversation.MonkeyConversation
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random
import kotlin.random.nextUInt

object ConversationPool {
    private val pool: ConcurrentHashMap<ConversationId, MonkeyConversation> = ConcurrentHashMap()

    private fun addToPool(monkeyConversation: MonkeyConversation) {
        this.pool[monkeyConversation.conversation.id] = monkeyConversation
    }

    fun conversationCreator(conversationId: ConversationId): Monkey? {
        return this.pool[conversationId]?.creator
    }

    suspend fun destroyRandomConversation() {
        val conversation = this.pool.values.filter { it.isDestroyable }.random()
        conversation.creator.destroyConversation(conversation.conversation.id)
        this.pool.remove(conversation.conversation.id)
    }

    suspend fun createRandomConversation(creator: Monkey, monkeyPool: MonkeyPool, userCount: UInt, protocol: ConversationOptions.Protocol) {
        val name = "By monkey ${creator.user.email} - $protocol - ${Random.nextUInt()}"
        val monkeyList = monkeyPool.randomMonkeys(userCount)
        val conversation = creator.createConversation(name, monkeyList, protocol)
        this.addToPool(conversation)
    }

    suspend fun createRandomConversation(domain: String, monkeyPool: MonkeyPool, userCount: UInt, protocol: ConversationOptions.Protocol) {
        val creator = monkeyPool.randomMonkeysFromDomain(domain, 1u)[0]
        this.createRandomConversation(creator, monkeyPool, userCount, protocol)
    }
}
