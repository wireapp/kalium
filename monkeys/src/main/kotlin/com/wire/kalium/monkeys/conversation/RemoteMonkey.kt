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

import com.wire.kalium.logic.CoreLogic
import com.wire.kalium.logic.data.conversation.ConversationDetails
import com.wire.kalium.logic.data.conversation.ConversationOptions
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.monkeys.model.MonkeyId
import com.wire.kalium.monkeys.model.UserCount
import com.wire.kalium.monkeys.pool.MonkeyPool

class RemoteMonkey(monkeyType: MonkeyType, internalId: MonkeyId) : Monkey(monkeyType, internalId) {
    override fun isSessionActive(): Boolean {
        TODO("Not yet implemented")
    }

    override suspend fun login(coreLogic: CoreLogic, callback: (Monkey) -> Unit) {
        TODO("Not yet implemented")
    }

    override suspend fun connectedMonkeys(): List<UserId> {
        TODO("Not yet implemented")
    }

    override suspend fun logout(callback: (Monkey) -> Unit) {
        TODO("Not yet implemented")
    }

    override suspend fun randomPeer(monkeyPool: MonkeyPool): Monkey {
        TODO("Not yet implemented")
    }

    override suspend fun randomPeers(userCount: UserCount, monkeyPool: MonkeyPool, filterOut: List<UserId>): List<Monkey> {
        TODO("Not yet implemented")
    }

    override suspend fun sendRequest(anotherMonkey: Monkey) {
        TODO("Not yet implemented")
    }

    override suspend fun acceptRequest(anotherMonkey: Monkey) {
        TODO("Not yet implemented")
    }

    override suspend fun rejectRequest(anotherMonkey: Monkey) {
        TODO("Not yet implemented")
    }

    override suspend fun pendingConnectionRequests(): List<ConversationDetails.Connection> {
        TODO("Not yet implemented")
    }

    override suspend fun <T> makeReadyThen(coreLogic: CoreLogic, monkeyPool: MonkeyPool, func: suspend Monkey.() -> T): T {
        TODO("Not yet implemented")
    }

    override suspend fun createConversation(
        name: String,
        monkeyList: List<Monkey>,
        protocol: ConversationOptions.Protocol,
        isDestroyable: Boolean
    ): MonkeyConversation {
        TODO("Not yet implemented")
    }

    override suspend fun leaveConversation(conversationId: ConversationId) {
        TODO("Not yet implemented")
    }

    override suspend fun destroyConversation(conversationId: ConversationId) {
        TODO("Not yet implemented")
    }

    override suspend fun addMonkeysToConversation(conversationId: ConversationId, monkeys: List<Monkey>) {
        TODO("Not yet implemented")
    }

    override suspend fun removeMonkeyFromConversation(id: ConversationId, monkey: Monkey) {
        TODO("Not yet implemented")
    }

    override suspend fun sendDirectMessageTo(anotherMonkey: Monkey, message: String) {
        TODO("Not yet implemented")
    }

    override suspend fun sendMessageTo(conversationId: ConversationId, message: String) {
        TODO("Not yet implemented")
    }
}
