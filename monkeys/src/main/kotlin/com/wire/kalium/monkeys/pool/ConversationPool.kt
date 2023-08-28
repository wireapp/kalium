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

import com.wire.kalium.logic.CoreLogic
import com.wire.kalium.logic.data.conversation.ConversationOptions
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.monkeys.conversation.Monkey
import com.wire.kalium.monkeys.conversation.MonkeyConversation
import com.wire.kalium.monkeys.importer.UserCount
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random
import kotlin.random.nextUInt

object ConversationPool {
    private val pool: ConcurrentHashMap<ConversationId, MonkeyConversation> = ConcurrentHashMap()

    // since this is created in the setup, there's no need to be thread safe
    private val prefixedConversations: MutableMap<String, MutableList<ConversationId>> = mutableMapOf()

    fun get(conversationId: ConversationId): MonkeyConversation? {
        return this.pool[conversationId]
    }

    fun conversationDestroyed(id: ConversationId) {
        this.pool.remove(id)
    }

    private fun addToPool(monkeyConversation: MonkeyConversation) {
        this.pool[monkeyConversation.conversation.id] = monkeyConversation
    }

    fun conversationCreator(conversationId: ConversationId): Monkey? {
        return this.pool[conversationId]?.creator
    }

    fun randomConversations(count: UInt): List<MonkeyConversation> {
        return (1u..count).map { pool.values.random() }
    }

    fun randomDynamicConversations(count: Int): List<MonkeyConversation> {
        val conversations = this.pool.values.filter { it.isDestroyable }.shuffled()
        return conversations.take(count)
    }

    suspend fun destroyRandomConversation() {
        val conversation = this.pool.values.filter { it.isDestroyable }.random()
        conversation.creator.destroyConversation(conversation.conversation.id)
        this.pool.remove(conversation.conversation.id)
    }

    private suspend fun createDynamicConversation(
        creator: Monkey,
        userCount: UserCount,
        protocol: ConversationOptions.Protocol
    ) {
        val name = "By monkey ${creator.user.email} - $protocol - ${Random.nextUInt()}"
        val monkeyList = creator.randomPeers(userCount)
        val conversation = creator.createConversation(name, monkeyList, protocol)
        this.addToPool(conversation)
    }

    suspend fun createDynamicConversation(
        userCount: UserCount,
        protocol: ConversationOptions.Protocol
    ) {
        val creator = MonkeyPool.randomMonkeys(UserCount.single())[0]
        this.createDynamicConversation(creator, userCount, protocol)
    }

    suspend fun createDynamicConversation(
        team: String,
        userCount: UserCount,
        protocol: ConversationOptions.Protocol
    ) {
        val creator = MonkeyPool.randomMonkeysFromTeam(team, UserCount.single())[0]
        this.createDynamicConversation(creator, userCount, protocol)
    }

    // Should be called on the setup free from concurrent access as it is not thread safe
    suspend fun createPrefixedConversations(
        coreLogic: CoreLogic,
        prefix: String,
        count: UInt,
        userCount: UserCount,
        protocol: ConversationOptions.Protocol
    ) {
        repeat(count.toInt()) { groupIndex ->
            val creator = MonkeyPool.randomMonkeys(UserCount.single())[0]
            val name = "Prefixed $prefix by monkey ${creator.user.email} - $protocol - $groupIndex"
            val conversation = creator.makeReadyThen(coreLogic) {
                val participants = creator.randomPeers(userCount)
                createConversation(name, participants, protocol, false)
            }
            this.addToPool(conversation)
            this.prefixedConversations.getOrPut(prefix) { mutableListOf() }.add(conversation.conversation.id)
        }
    }

    fun getFromPrefixed(target: String): List<MonkeyConversation> {
        return this.prefixedConversations[target]?.map {
            this.pool[it] ?: error("Inconsistent state. Conversation $it is in the prefixed pool but not on the general")
        } ?: error("Conversation from target $target not found in the pool")
    }

    fun size(): UInt = this.pool.size.toUInt()
}
