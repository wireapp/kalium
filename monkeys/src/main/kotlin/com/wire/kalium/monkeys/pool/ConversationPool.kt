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
package com.wire.kalium.monkeys.pool

import com.wire.kalium.logic.CoreLogic
import com.wire.kalium.logic.data.conversation.ConversationOptions
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.monkeys.MetricsCollector
import com.wire.kalium.monkeys.conversation.Monkey
import com.wire.kalium.monkeys.conversation.MonkeyConversation
import com.wire.kalium.monkeys.logger
import com.wire.kalium.monkeys.model.ConversationDef
import com.wire.kalium.monkeys.model.UserCount
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random
import kotlin.random.nextUInt

@Suppress("TooManyFunctions")
class ConversationPool(private val delayPool: Long) {
    private val pool: ConcurrentHashMap<ConversationId, MonkeyConversation> = ConcurrentHashMap()

    // pool from conversations from an old run
    private val oldPool: ConcurrentHashMap<ConversationId, ConversationId> = ConcurrentHashMap()

    // since this is created in the setup, there's no need to be thread safe
    private val prefixedConversations: MutableMap<String, MutableList<ConversationId>> = mutableMapOf()

    init {
        MetricsCollector.gaugeMap("g_conversations", listOf(), this.pool)
    }

    fun get(conversationId: ConversationId): MonkeyConversation? {
        return this.pool[conversationId]
    }

    fun conversationDestroyed(id: ConversationId) {
        this.pool.remove(id)
    }

    private suspend fun addToPool(monkeyConversation: MonkeyConversation) = coroutineScope {
        launch {
            delay(delayPool)
            pool[monkeyConversation.conversationId] = monkeyConversation
        }
    }

    fun conversationCreator(conversationId: ConversationId): Monkey? {
        return this.pool[conversationId]?.creator
    }

    fun randomConversations(count: UInt): List<MonkeyConversation> = try {
        (1u..count).map { pool.values.random() }
    } catch (_: NoSuchElementException) {
        logger.w("No conversation is available yet")
        listOf()
    }

    fun randomDynamicConversations(count: Int): List<MonkeyConversation> {
        val conversations = this.pool.values.filter { it.isDestroyable }.shuffled()
        return conversations.take(count)
    }

    suspend fun destroyRandomConversation() {
        val conversation = this.pool.values.filter { it.isDestroyable }.random()
        conversation.creator.destroyConversation(conversation.conversationId, conversation.creator.monkeyType.userId())
        this.pool.remove(conversation.conversationId)
    }

    private suspend fun createDynamicConversation(
        creator: Monkey,
        protocol: ConversationOptions.Protocol,
        monkeyList: List<Monkey>
    ): ConversationDef {
        val name = "By monkey ${creator.monkeyType.userId()} - $protocol - ${Random.nextUInt()}"
        val conversation = creator.createConversation(name, monkeyList, protocol)
        this.addToPool(conversation)
        return ConversationDef(conversation.conversationId, creator.internalId, monkeyList.map { it.internalId }, protocol)
    }

    suspend fun createDynamicConversation(
        userCount: UserCount,
        protocol: ConversationOptions.Protocol,
        monkeyPool: MonkeyPool
    ): ConversationDef {
        val creator = monkeyPool.randomLoggedInMonkeys(UserCount.single())[0]
        val members = creator.randomPeers(userCount, monkeyPool)
        return this.createDynamicConversation(creator, protocol, members)
    }

    suspend fun createDynamicConversation(
        team: String,
        userCount: UserCount,
        protocol: ConversationOptions.Protocol,
        monkeyPool: MonkeyPool
    ): ConversationDef {
        val creator = monkeyPool.randomLoggedInMonkeysFromTeam(team, UserCount.single()).first()
        val members = creator.randomPeers(userCount, monkeyPool)
        return this.createDynamicConversation(creator, protocol, members)
    }

    suspend fun createDynamicConversation(conversationDef: ConversationDef, monkeyPool: MonkeyPool): ConversationDef {
        val creator = monkeyPool.getFromTeam(conversationDef.owner.team, conversationDef.owner.index)
        val members = conversationDef.initialMembers.map { monkeyPool.getFromTeam(it.team, it.index) }
        return this.createDynamicConversation(creator, conversationDef.protocol, members)
    }

    fun getFromOldId(conversationId: ConversationId): MonkeyConversation {
        val newId = this.oldPool[conversationId] ?: error("Old conversation not found")
        return this.pool[newId] ?: error("New conversation not found")
    }

    // Should be called on the setup free from concurrent access as it is not thread safe
    @Suppress("LongParameterList")
    suspend fun createPrefixedConversations(
        coreLogic: CoreLogic,
        prefix: String,
        count: UInt,
        userCount: UserCount,
        protocol: ConversationOptions.Protocol,
        monkeyPool: MonkeyPool,
        preset: ConversationDef? = null
    ): List<ConversationDef> {
        return (1..count.toInt()).map { groupIndex ->
            val creator = if (preset != null) {
                monkeyPool.getFromTeam(preset.owner.team, preset.owner.index)
            } else {
                monkeyPool.randomMonkeys(UserCount.single())[0]
            }
            val name = "Prefixed $prefix by monkey ${creator.monkeyType.userId()} - $protocol - $groupIndex"
            val conversation = creator.createPrefixedConversation(name, protocol, userCount, coreLogic, monkeyPool, preset)
            this.addToPool(conversation)
            if (preset != null) {
                this.oldPool[preset.id] = conversation.conversationId
            }
            this.prefixedConversations.getOrPut(prefix) { mutableListOf() }.add(conversation.conversationId)
            ConversationDef(
                conversation.conversationId, creator.internalId, conversation.members().map { it.internalId }, protocol
            )
        }
    }

    fun getFromPrefixed(target: String): List<MonkeyConversation> {
        return this.prefixedConversations[target]?.map {
            this.pool[it] ?: error("Inconsistent state. Conversation $it is in the prefixed pool but not on the general")
        } ?: error("Conversation from target $target not found in the pool")
    }
}
