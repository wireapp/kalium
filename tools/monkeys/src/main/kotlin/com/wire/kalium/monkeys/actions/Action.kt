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
package com.wire.kalium.monkeys.actions

import com.wire.kalium.logic.CoreLogic
import com.wire.kalium.monkeys.actions.replay.AddUserToConversationEventAction
import com.wire.kalium.monkeys.actions.replay.CreateConversationEventAction
import com.wire.kalium.monkeys.actions.replay.DestroyConversationEventAction
import com.wire.kalium.monkeys.actions.replay.LeaveConversationEventAction
import com.wire.kalium.monkeys.actions.replay.LoginEventAction
import com.wire.kalium.monkeys.actions.replay.LogoutEventAction
import com.wire.kalium.monkeys.actions.replay.RequestResponseEventAction
import com.wire.kalium.monkeys.actions.replay.SendDirectMessageEventAction
import com.wire.kalium.monkeys.actions.replay.SendMessageEventAction
import com.wire.kalium.monkeys.actions.replay.SendRequestEventAction
import com.wire.kalium.monkeys.model.ActionConfig
import com.wire.kalium.monkeys.model.ActionType
import com.wire.kalium.monkeys.model.Event
import com.wire.kalium.monkeys.model.EventType
import com.wire.kalium.monkeys.model.MonkeyId
import com.wire.kalium.monkeys.pool.ConversationPool
import com.wire.kalium.monkeys.pool.MonkeyPool
import kotlinx.coroutines.channels.SendChannel

abstract class Action(val sender: suspend (Event) -> Unit) {
    companion object {
        fun fromConfig(config: ActionConfig, channel: SendChannel<Event>): Action {
            val sender: suspend (Event) -> Unit = {
                channel.send(it)
            }
            return when (config.type) {
                is ActionType.Login -> LoginAction(config.type, sender)
                is ActionType.CreateConversation -> CreateConversationAction(config.type, sender)
                is ActionType.AddUsersToConversation -> AddUserToConversationAction(config.type, sender)
                is ActionType.DestroyConversation -> DestroyConversationAction(config.type, sender)
                is ActionType.LeaveConversation -> LeaveConversationAction(config.type, sender)
                is ActionType.Reconnect -> ReconnectAction(config.type, sender)
                is ActionType.SendMessage -> SendMessageAction(config.type, sender)
                is ActionType.SendRequest -> SendRequestAction(config.type, sender)
                is ActionType.HandleExternalRequest -> HandleExternalRequestAction(config.type)
                is ActionType.SendExternalRequest -> SendExternalRequestAction(config.type)
            }
        }

        fun eventFromConfig(monkey: MonkeyId, config: EventType): Action {
            return when (config) {
                is EventType.AddUsersToConversation -> AddUserToConversationEventAction(config)
                is EventType.CreateConversation -> CreateConversationEventAction(config)
                is EventType.DestroyConversation -> DestroyConversationEventAction(config)
                is EventType.LeaveConversation -> LeaveConversationEventAction(monkey, config)
                is EventType.Login -> LoginEventAction(monkey)
                is EventType.Logout -> LogoutEventAction(monkey)
                is EventType.RequestResponse -> RequestResponseEventAction(monkey, config)
                is EventType.SendDirectMessage -> SendDirectMessageEventAction(monkey, config)
                is EventType.SendMessage -> SendMessageEventAction(monkey, config)
                is EventType.SendRequest -> SendRequestEventAction(monkey, config)
            }
        }
    }

    abstract suspend fun execute(coreLogic: CoreLogic, monkeyPool: MonkeyPool, conversationPool: ConversationPool)
}
