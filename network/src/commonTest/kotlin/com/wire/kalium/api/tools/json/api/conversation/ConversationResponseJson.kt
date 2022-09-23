package com.wire.kalium.api.tools.json.api.conversation

import com.wire.kalium.api.tools.json.ValidJsonProvider
import com.wire.kalium.api.tools.json.model.QualifiedIDSamples
import com.wire.kalium.network.api.base.model.QualifiedID
import com.wire.kalium.network.api.base.authenticated.conversation.ConvProtocol
import com.wire.kalium.network.api.base.authenticated.conversation.ConversationMemberDTO
import com.wire.kalium.network.api.base.authenticated.conversation.ConversationMembersResponse
import com.wire.kalium.network.api.base.authenticated.conversation.ConversationResponse
import com.wire.kalium.network.api.base.authenticated.conversation.MutedStatus
import com.wire.kalium.network.api.base.authenticated.conversation.ServiceReferenceDTO
import com.wire.kalium.network.api.base.model.ConversationAccessDTO
import com.wire.kalium.network.api.base.model.ConversationAccessRoleDTO
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

object ConversationResponseJson {

    val conversationResponseSerializer = { it: ConversationResponse ->
        buildJsonObject {
            put("creator", it.creator)
            putQualifiedId(it.id)
            it.groupId?.let { put("group_id", it) }
            putJsonObject("members") {
                putSelfMember(it.members.self)
                putJsonArray("others") {
                    it.members.otherMembers.forEach { otherMember ->
                        addJsonObject {
                            putOtherMember(otherMember)
                        }
                    }
                }
            }
            put("type", it.type.ordinal)
            put("protocol", it.protocol.toString())
            put("last_event_time", it.lastEventTime)
            putAccessSet(it.access)
            it.accessRole?.let { putAccessRoleSet(it) }
            it.messageTimer?.let { put("message_timer", it) }
            it.name?.let { put("name", it) }
            it.teamId?.let { put("team", it) }
            it.mlsCipherSuiteTag?.let { put("cipher_suite", it) }
        }.toString()
    }

    val validGroup = ValidJsonProvider(
        ConversationResponse(
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
            mlsCipherSuiteTag = null
        ), conversationResponseSerializer
    )
}

fun JsonObjectBuilder.putAccessRoleSet(accessRole: Set<ConversationAccessRoleDTO>) = putJsonArray("access_role_v2") {
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
