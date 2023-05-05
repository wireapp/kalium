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
package com.wire.kalium.monkeys

import com.wire.kalium.logic.CoreLogic
import com.wire.kalium.logic.data.conversation.Conversation

fun interface SplitStep {
    suspend operator fun invoke(allUsers: List<UserData>): List<List<UserData>>
}

fun interface SetupStep {
    suspend operator fun invoke(coreLogic: CoreLogic, accountGroups: List<List<UserData>>): List<List<Monkey>>
}

fun interface MonkeyCommand {
    suspend operator fun invoke(monkeyConversations: List<MonkeyConversation>)
}

fun interface ConversationCreation {
    suspend operator fun invoke(monkeyGroups: List<List<Monkey>>): List<MonkeyConversation>
}

data class MonkeyConversation(
    val watchMonkey: Monkey,
    val allMonkeys: List<Monkey>,
    val conversation: Conversation,
)

interface TestSequence {
    val split: SplitStep
    val setup: SetupStep
    val createConversations: ConversationCreation
    val commands: List<MonkeyCommand>
}
