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
package com.wire.kalium.monkeys.importer

import com.wire.kalium.logic.data.conversation.ConversationOptions
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TestDataJsonModel(
    @SerialName("testCases") val testCases: List<TestCase>,
    @SerialName("backends") val backends: List<BackendDataJsonModel>
)

@Serializable
data class TestCase(
    @SerialName("name") val name: String,
    @SerialName("conversationDistribution") val conversationDistribution: Map<String, GroupConfig>,
    @SerialName("setup") val setup: List<Action> = listOf(),
    @SerialName("actions") val actions: List<Action>
)

@Serializable
sealed class UserCount {
    @Serializable
    @SerialName("PERCENTAGE")
    data class Percentage(@SerialName("value") val value: UInt) : UserCount()

    @Serializable
    @SerialName("FIXED_COUNT")
    data class FixedCount(@SerialName("value") val value: UInt) : UserCount()
}

@Serializable
data class GroupConfig(
    @SerialName("userCount") val userCount: UserCount,
    @SerialName("protocol") val protocol: ConversationOptions.Protocol = ConversationOptions.Protocol.MLS,
    @SerialName("groupCount") val groupCount: UInt = 1u
)

@Serializable
data class Action(
    @SerialName("description") val description: String,
    @SerialName("type") val type: ActionType,
    @SerialName("count") val count: UserCount,
    @SerialName("repeatDuration") val repeatDuration: UInt
)

@Serializable
sealed class ActionType {
    @Serializable
    data class Login(@SerialName("duration") val duration: UInt = 0u) : ActionType()

    @Serializable
    data class Reconnect(@SerialName("durationOffline") val durationOffline: UInt) : ActionType()

    @Serializable
    data class SendMessage(
        @SerialName("count") val count: UInt,
        @SerialName("targets") val targets: List<String> = listOf()
    ) : ActionType()

    @Serializable
    data class CreateConversation(@SerialName("userCount") val userCount: UserCount) : ActionType()

    @Serializable
    data class LeaveConversation(@SerialName("userCount") val userCount: UserCount) : ActionType()

    @Serializable
    data class DestroyConversation(@SerialName("count") val count: UInt) : ActionType()

    @Serializable
    data class SendRequest(@SerialName("userCount") val userCount: UInt) : ActionType()
}

@Serializable
data class BackendDataJsonModel(
    @SerialName("api") val api: String,
    @SerialName("accounts") val accounts: String,
    @SerialName("webSocket") val webSocket: String,
    @SerialName("blackList") val blackList: String,
    @SerialName("teams") val teams: String,
    @SerialName("website") val website: String,
    @SerialName("title") val title: String,
    @SerialName("passwordForUsers") val passwordForUsers: String,
    @SerialName("domain") val domain: String,
    @SerialName("users") val users: List<UserAccountDataJsonModel>
)

@Serializable
data class UserAccountDataJsonModel(
    @SerialName("email") val email: String,
    @SerialName("id") val unqualifiedId: String
)
