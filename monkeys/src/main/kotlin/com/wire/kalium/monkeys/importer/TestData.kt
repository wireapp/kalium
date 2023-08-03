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
data class TestData(
    @SerialName("testCases") val testCases: List<TestCase>,
    @SerialName("backends") val backends: List<BackendConfig>
)

@Serializable
data class TestCase(
    @SerialName("name") val name: String,
    @SerialName("conversationDistribution") val conversationDistribution: Map<String, GroupConfig> = mapOf(),
    @SerialName("setup") val setup: List<ActionConfig> = listOf(),
    @SerialName("actions") val actions: List<ActionConfig>
)

@Serializable
sealed class UserCount {
    @Serializable
    @SerialName("PERCENTAGE")
    data class Percentage(@SerialName("value") val value: UInt) : UserCount()

    @Serializable
    @SerialName("FIXED_COUNT")
    data class FixedCount(@SerialName("value") val value: UInt) : UserCount()

    companion object {
        fun single() = FixedCount(1u)
        fun fixed(value: UInt) = FixedCount(value)
    }
}

@Serializable
data class GroupConfig(
    @SerialName("userCount") val userCount: UserCount,
    @SerialName("protocol") val protocol: ConversationOptions.Protocol = ConversationOptions.Protocol.MLS,
    @SerialName("groupCount") val groupCount: UInt = 1u
)

@Serializable
data class ActionConfig(
    @SerialName("description") val description: String,
    @SerialName("config") val type: ActionType,
    @SerialName("count") val count: UInt = 1u,
    @SerialName("repeatInterval") val repeatInterval: ULong = 0u
)

@Serializable
sealed class ActionType {
    @Serializable
    @SerialName("LOGIN")
    data class Login(
        @SerialName("userCount") val userCount: UserCount,
        @SerialName("duration") val duration: UInt = 0u
    ) : ActionType()

    @Serializable
    @SerialName("RECONNECT")
    data class Reconnect(
        @SerialName("userCount") val userCount: UserCount,
        @SerialName("durationOffline") val durationOffline: UInt
    ) : ActionType()

    @Serializable
    @SerialName("SEND_MESSAGE")
    data class SendMessage(
        @SerialName("userCount") val userCount: UserCount = UserCount.single(),
        @SerialName("count") val count: UInt,
        @SerialName("countGroups") val countGroups: UInt = 1u,
        @SerialName("targets") val targets: List<String> = listOf()
    ) : ActionType()

    @Serializable
    @SerialName("CREATE_CONVERSATION")
    data class CreateConversation(
        @SerialName("userCount") val userCount: UserCount,
        @SerialName("protocol") val protocol: ConversationOptions.Protocol = ConversationOptions.Protocol.MLS,
        @SerialName("domainOwner") val domain: String?
    ) : ActionType()

    @Serializable
    @SerialName("ADD_USERS_TO_CONVERSATION")
    data class AddUsersToConversation(
        @SerialName("userCount") val userCount: UserCount,
    ) : ActionType()

    @Serializable
    @SerialName("LEAVE_CONVERSATION")
    data class LeaveConversation(@SerialName("userCount") val userCount: UserCount) : ActionType()

    @Serializable
    @SerialName("DESTROY_CONVERSATION")
    data class DestroyConversation(@SerialName("count") val count: UInt) : ActionType()

    @Serializable
    @SerialName("SEND_REQUEST")
    data class SendRequest(
        @SerialName("userCount") val userCount: UserCount,
        @SerialName("targetUserCount") val targetUserCount: UserCount,
        @SerialName("originDomain") val originDomain: String,
        @SerialName("targetDomain") val targetDomain: String,
        @SerialName("delayResponse") val delayResponse: ULong = 0u,
        @SerialName("shouldAccept") val shouldAccept: Boolean = true
    ) : ActionType()
}

@Serializable
data class BackendConfig(
    @SerialName("api") val api: String,
    @SerialName("accounts") val accounts: String,
    @SerialName("webSocket") val webSocket: String,
    @SerialName("blackList") val blackList: String,
    @SerialName("teams") val teams: String,
    @SerialName("website") val website: String,
    @SerialName("title") val title: String,
    @SerialName("passwordForUsers") val passwordForUsers: String,
    @SerialName("domain") val domain: String,
    @SerialName("users") val users: List<UserAccount>
)

@Serializable
data class UserAccount(
    @SerialName("email") val email: String,
    @SerialName("id") val unqualifiedId: String
)
