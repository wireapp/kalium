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

package com.wire.kalium.mocks.responses.conversation

import com.wire.kalium.mocks.responses.ValidJsonProvider
import com.wire.kalium.mocks.responses.samples.QualifiedIDSamples
import com.wire.kalium.network.api.authenticated.conversation.ConvProtocol
import com.wire.kalium.network.api.authenticated.conversation.ConversationMemberDTO
import com.wire.kalium.network.api.authenticated.conversation.ConversationMembersResponse
import com.wire.kalium.network.api.authenticated.conversation.ConversationResponse
import com.wire.kalium.network.api.authenticated.conversation.MutedStatus
import com.wire.kalium.network.api.authenticated.conversation.ReceiptMode
import com.wire.kalium.network.api.authenticated.conversation.ServiceReferenceDTO
import com.wire.kalium.network.api.model.ConversationAccessDTO
import com.wire.kalium.network.api.model.ConversationAccessRoleDTO
import com.wire.kalium.network.api.model.QualifiedID
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

object ConversationResponseJson {

    val conversationResponseSerializer = { it: ConversationResponse ->
        buildConversationResponse(it).toString()
    }

    val conversationResponseSerializerWithDeprecatedAccessRole = { it: ConversationResponse ->
        buildConversationResponse(it, useDeprecatedAccessRole = true).toString()
    }

    private val conversationResponse = ConversationResponse(
        "fdf23116-42a5-472c-8316-e10655f5d11e",
        ConversationMembersResponse(
            ConversationMemberDTO.Self(
                QualifiedIDSamples.one,
                "wire_admin",
                otrMutedRef = "2022-04-11T14:15:48.044Z",
                otrMutedStatus = MutedStatus.fromOrdinal(0)
            ),
            listOf(ConversationMemberDTO.Other(id = QualifiedIDSamples.two, conversationRole = "wire_member"))
        ),
        "group name",
        QualifiedIDSamples.one,
        "groupID",
        0UL,
        ConversationResponse.Type.GROUP,
        null,
        "teamID",
        ConvProtocol.PROTEUS,
        lastEventTime = "2022-03-30T15:36:00.000Z",
        access = setOf(ConversationAccessDTO.INVITE, ConversationAccessDTO.CODE),
        accessRole = setOf(
            ConversationAccessRoleDTO.GUEST,
            ConversationAccessRoleDTO.TEAM_MEMBER,
            ConversationAccessRoleDTO.NON_TEAM_MEMBER
        ),
        mlsCipherSuiteTag = null,
        receiptMode = ReceiptMode.DISABLED
    )

    val v3 = ValidJsonProvider(
        conversationResponse, conversationResponseSerializer
    )

    fun v0(accessRole: Set<ConversationAccessRoleDTO>? = null) = ValidJsonProvider(
        conversationResponse.copy(
            accessRole = accessRole ?: conversationResponse.accessRole
        ),
        conversationResponseSerializerWithDeprecatedAccessRole
    )
}

fun buildConversationResponse(
    conversationResponse: ConversationResponse,
    useDeprecatedAccessRole: Boolean = false
): JsonObject =
    buildJsonObject {
        put("creator", conversationResponse.creator)
        putQualifiedId(conversationResponse.id)
        conversationResponse.groupId?.let { put("group_id", it) }
        putJsonObject("members") {
            putSelfMember(conversationResponse.members.self)
            putJsonArray("others") {
                conversationResponse.members.otherMembers.forEach { otherMember ->
                    addJsonObject {
                        putOtherMember(otherMember)
                    }
                }
            }
        }
        put("type", conversationResponse.type.ordinal)
        put("protocol", conversationResponse.protocol.toString())
        put("last_event_time", conversationResponse.lastEventTime)
        putAccessSet(conversationResponse.access)
        if (useDeprecatedAccessRole) {
            conversationResponse.accessRole?.let { putDeprecatedAccessRoleSet(it) }
        } else {
            conversationResponse.accessRole?.let { putAccessRoleSet(it) }
        }
        conversationResponse.messageTimer?.let { put("message_timer", it) }
        conversationResponse.name?.let { put("name", it) }
        conversationResponse.teamId?.let { put("team", it) }
        conversationResponse.mlsCipherSuiteTag?.let { put("cipher_suite", it) }
    }

fun JsonObjectBuilder.putAccessRoleSet(accessRole: Set<ConversationAccessRoleDTO>) = putJsonArray("access_role") {
    accessRole.forEach { add(it.toString()) }
}

fun JsonObjectBuilder.putDeprecatedAccessRoleSet(accessRole: Set<ConversationAccessRoleDTO>) =
    putJsonArray("access_role_v2") {
        accessRole.forEach { add(it.toString()) }
    }

fun JsonObjectBuilder.putAccessSet(access: Set<ConversationAccessDTO>) = putJsonArray("access") {
    access.forEach { add(it.toString()) }
}

fun JsonObjectBuilder.putQualifiedId(id: QualifiedID) = putJsonObject("qualified_id") {
    put("domain", id.domain)
    put("id", id.value)
}

fun JsonObjectBuilder.putServiceReferenceDTO(key: String, service: ServiceReferenceDTO) {
    with(service) {
        putJsonObject(key) {
            put("id", id)
            put("provider", provider)
        }
    }
}

fun JsonObjectBuilder.putSelfMember(self: ConversationMemberDTO.Self) = putJsonObject("self") {
    with(self) {
        putQualifiedId(id)
        put("conversation_role", conversationRole)
        service?.let { putServiceReferenceDTO("service", it) }
        hidden?.let { put("hidden", it) }
        hiddenRef?.let { put("hidden_ref", it) }
        otrArchived?.let { put("otr_archived", it) }
        otrArchivedRef?.let { put("otr_archived_ref", it) }
        otrMutedRef?.let { put("otr_muted_ref", it) }
        otrMutedStatus?.let { put("otr_muted_status", it.ordinal) }
    }
}

fun JsonObjectBuilder.putOtherMember(member: ConversationMemberDTO.Other) {
    with(member) {
        putQualifiedId(id)
        put("conversation_role", conversationRole)
        service?.let { putServiceReferenceDTO("service", it) }
    }
}
