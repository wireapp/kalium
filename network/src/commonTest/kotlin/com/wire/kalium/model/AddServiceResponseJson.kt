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
package com.wire.kalium.model

import com.wire.kalium.api.json.ValidJsonProvider
import com.wire.kalium.network.api.base.authenticated.conversation.ConversationMemberDTO
import com.wire.kalium.network.api.base.authenticated.conversation.ConversationMembers
import com.wire.kalium.network.api.base.authenticated.conversation.ServiceReferenceDTO
import com.wire.kalium.network.api.base.authenticated.notification.EventContentDTO
import com.wire.kalium.network.api.base.model.AddServiceResponse
import com.wire.kalium.network.api.base.model.ConversationId
import com.wire.kalium.network.api.base.model.UserId
import com.wire.kalium.util.serialization.toJsonElement
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

object AddServiceResponseJson {

    private val jsonProvider = { serializable: AddServiceResponse ->
        buildJsonObject {
            putJsonObject("event") {
                putJsonObject("qualified_conversation") {
                    put("id", serializable.event.qualifiedConversation.value.toJsonElement())
                    put("domain", serializable.event.qualifiedConversation.domain.toJsonElement())
                }
                putJsonObject("qualified_from") {
                    put("id", serializable.event.qualifiedFrom.value.toJsonElement())
                    put("domain", serializable.event.qualifiedFrom.domain.toJsonElement())
                }
                put("time", serializable.event.time.toJsonElement())
                putJsonObject("data") {
                    putJsonArray("user_ids") {
                        serializable.event.members.userIds.forEach {
                            add(it)
                        }
                    }
                    putJsonArray("users") {
                        serializable.event.members.users.forEach { other ->
                            addJsonObject {
                                putJsonObject("qualified_id") {
                                    put("id", other.id.value.toJsonElement())
                                    put("domain", other.id.domain.toJsonElement())
                                }
                                put("conversation_role", other.conversationRole.toJsonElement())
                                other.service?.let {
                                    putJsonObject("service") {
                                        put("id", it.id.toJsonElement())
                                        put("provider", it.provider.toJsonElement())
                                    }
                                }
                            }
                        }
                    }
                }
                put("from", serializable.event.from.toJsonElement())
            }
        }.toString()
    }

    val valid = ValidJsonProvider(
        AddServiceResponse(
            event = EventContentDTO.Conversation.MemberJoinDTO(
                qualifiedConversation = ConversationId(
                    value = "value",
                    domain = "domain"
                ),
                qualifiedFrom = UserId(
                    value = "value2",
                    domain = "domain2"
                ),
                time = "some_time",
                members = ConversationMembers(
                    userIds = listOf("value3@domain3"),
                    users = listOf(
                        ConversationMemberDTO.Other(
                            id = UserId(
                                value = "value3",
                                domain = "domain3"
                            ),
                            conversationRole = "role",
                            service = ServiceReferenceDTO(
                                id = "serviceId",
                                provider = "providerId"
                            )
                        )
                    )
                ),
                from = "from"
            )
        ),
        jsonProvider
    )
}
