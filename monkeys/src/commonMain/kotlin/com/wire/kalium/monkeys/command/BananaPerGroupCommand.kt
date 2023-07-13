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
package com.wire.kalium.monkeys.command

import com.wire.kalium.logic.feature.conversation.GetConversationsUseCase
import com.wire.kalium.monkeys.MonkeyCommand
import com.wire.kalium.monkeys.MonkeyConversation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlin.time.Duration.Companion.milliseconds

/**
 * Sends messages to each provided conversation.
 * The amount of messages is defined by [amountOfMessages].
 */
class BananaPerGroupCommand(private val amountOfMessages: Int) : MonkeyCommand {

    override suspend fun invoke(
        monkeyConversations: List<MonkeyConversation>
    ) = repeat(amountOfMessages) { currentMessage ->
        for (monkeyTalk in monkeyConversations) {
            val userGroup = monkeyTalk.allMonkeys
            val randomUser = userGroup.random()
            val userScope = randomUser.operationScope
            val conversationResult = randomUser.operationScope.conversations.getConversations()
            if (conversationResult !is GetConversationsUseCase.Result.Success) {
                error("Failure to get conversations for ${randomUser.user}; $conversationResult")
            }
            val firstConversation = conversationResult.convFlow.first().firstOrNull {
                it.id == monkeyTalk.conversation.id
            }

            if (firstConversation != null) {
                println("Requesting banana=$currentMessage from ${randomUser.user.email}")
                userScope.messages.sendTextMessage(
                    firstConversation.id,
                    "give me $currentMessage bananas! ${emoji.random()}",
                )
            }
            delay(50.milliseconds)
        }
    }

    private companion object {
        private val emoji = listOf(
            "👀", "🦭", "😵‍💫", "👨‍🍳",
            "🍌", "🍆", "👨‍🌾", "🏄‍",
            "🥶", "🤤", "🙈", "🙊",
            "🐒", "🙉", "🦍", "🐵"
        )
    }
}
