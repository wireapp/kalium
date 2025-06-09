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

package com.wire.kalium.network.api.authenticated.notification

import com.wire.kalium.network.api.authenticated.client.ClientDTO
import com.wire.kalium.network.api.authenticated.client.ClientIdDTO
import com.wire.kalium.network.api.authenticated.connection.ConnectionDTO
import com.wire.kalium.network.api.authenticated.conversation.ConversationMemberRemovedDTO
import com.wire.kalium.network.api.authenticated.conversation.ConversationMembers
import com.wire.kalium.network.api.authenticated.conversation.ConversationNameUpdateEvent
import com.wire.kalium.network.api.authenticated.conversation.ConversationResponse
import com.wire.kalium.network.api.authenticated.conversation.ConversationRoleChange
import com.wire.kalium.network.api.authenticated.conversation.TypingIndicatorStatusDTO
import com.wire.kalium.network.api.authenticated.conversation.channel.ChannelAddPermissionDTO
import com.wire.kalium.network.api.authenticated.conversation.guestroomlink.ConversationInviteLinkResponse
import com.wire.kalium.network.api.authenticated.conversation.messagetimer.ConversationMessageTimerDTO
import com.wire.kalium.network.api.authenticated.conversation.model.ConversationAccessInfoDTO
import com.wire.kalium.network.api.authenticated.conversation.model.ConversationProtocolDTO
import com.wire.kalium.network.api.authenticated.conversation.model.ConversationReceiptModeDTO
import com.wire.kalium.network.api.authenticated.featureConfigs.FeatureConfigData
import com.wire.kalium.network.api.authenticated.featureConfigs.FeatureFlagStatusDTO
import com.wire.kalium.network.api.authenticated.keypackage.LastPreKeyDTO
import com.wire.kalium.network.api.authenticated.notification.conversation.MessageEventData
import com.wire.kalium.network.api.authenticated.notification.team.TeamMemberIdData
import com.wire.kalium.network.api.authenticated.notification.user.RemoveClientEventData
import com.wire.kalium.network.api.authenticated.notification.user.UserUpdateEventData
import com.wire.kalium.network.api.authenticated.properties.LabelListResponseDTO
import com.wire.kalium.network.api.model.ConversationId
import com.wire.kalium.network.api.model.TeamId
import com.wire.kalium.network.api.model.UserId
import com.wire.kalium.network.utils.kaliumUtilLogger
import com.wire.kalium.util.serialization.toJsonElement
import kotlinx.datetime.Instant
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.PolymorphicKind
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.buildSerialDescriptor
import kotlinx.serialization.descriptors.element
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.encoding.decodeStructure
import kotlinx.serialization.encoding.encodeStructure
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonTransformingSerializer
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlin.jvm.JvmInline

@Serializable
data class EventResponse(
    @Serializable
    @SerialName("id") val id: String,
    @SerialName("payload") val payload: List<EventContentDTO>?,
    @SerialName("transient") val transient: Boolean = false
)

/**
 * Handwritten serializer of the FeatureConfigUpdatedDTO because we want to extend it with the
 * `JsonCorrectingSerializer`, which is not possible using the plugin generated serializer.
 */
object FeatureConfigUpdatedDTOSerializer : KSerializer<EventContentDTO.FeatureConfig.FeatureConfigUpdatedDTO> {

    override val descriptor: SerialDescriptor =
        buildClassSerialDescriptor("feature-config.update") {
            element<FeatureConfigData>("data")
        }

    override fun deserialize(decoder: Decoder): EventContentDTO.FeatureConfig.FeatureConfigUpdatedDTO {
        var data: FeatureConfigData = FeatureConfigData.Unknown(FeatureFlagStatusDTO.ENABLED)
        decoder.decodeStructure(descriptor) {
            while (true) {
                when (val index = decodeElementIndex(descriptor)) {
                    0 -> data = decodeSerializableElement(descriptor, 0, FeatureConfigData.serializer())
                    CompositeDecoder.DECODE_DONE -> break
                    else -> error("Unexpected index: $index")
                }
            }
        }
        return EventContentDTO.FeatureConfig.FeatureConfigUpdatedDTO(data)
    }

    override fun serialize(encoder: Encoder, value: EventContentDTO.FeatureConfig.FeatureConfigUpdatedDTO) {
        encoder.encodeStructure(descriptor) {
            encodeSerializableElement(descriptor, 0, FeatureConfigData.serializer(), value.data)
        }
    }
}

/**
 * Transforms the feature config event JSON by moving the `name` field inside the data object so that
 * we can parse the data object using a polymorphic sealed class.
 */
object JsonCorrectingSerializer :
    JsonTransformingSerializer<EventContentDTO.FeatureConfig.FeatureConfigUpdatedDTO>(FeatureConfigUpdatedDTOSerializer) {
    override fun transformDeserialize(element: JsonElement): JsonElement {
        return JsonObject(
            element.jsonObject.toMutableMap().apply {
                this["data"] = JsonObject(
                    get("data")?.jsonObject?.toMutableMap().apply {
                        element.jsonObject["name"]?.let {
                            this?.set("name", it)
                        }
                    } ?: emptyMap()
                )
            }
        )
    }
}

@Serializable
sealed class EventContentDTO {

    @Serializable
    sealed class Conversation : EventContentDTO() {

        @Serializable
        @SerialName("conversation.create")
        data class NewConversationDTO(
            @SerialName("qualified_conversation") val qualifiedConversation: ConversationId,
            @SerialName("qualified_from") val qualifiedFrom: UserId,
            @SerialName("time") val time: Instant,
            @SerialName("data") val data: ConversationResponse,
        ) : Conversation()

        @Serializable
        @SerialName("conversation.delete")
        data class DeletedConversationDTO(
            @SerialName("qualified_conversation") val qualifiedConversation: ConversationId,
            @SerialName("qualified_from") val qualifiedFrom: UserId,
            val time: String
        ) : Conversation()

        @Serializable
        @SerialName("conversation.rename")
        data class ConversationRenameDTO(
            @SerialName("qualified_conversation") val qualifiedConversation: ConversationId,
            @SerialName("qualified_from") val qualifiedFrom: UserId,
            @SerialName("time") val time: Instant,
            @SerialName("data") val updateNameData: ConversationNameUpdateEvent,
        ) : Conversation()

        @Serializable
        @SerialName("conversation.member-join")
        data class MemberJoinDTO(
            @SerialName("qualified_conversation") val qualifiedConversation: ConversationId,
            @SerialName("qualified_from") val qualifiedFrom: UserId,
            @SerialName("time") val time: Instant,
            @SerialName("data") val members: ConversationMembers,
            @Deprecated("use qualifiedFrom", replaceWith = ReplaceWith("this.qualifiedFrom")) @SerialName("from") val from: String
        ) : Conversation()

        @Serializable
        @SerialName("conversation.member-leave")
        data class MemberLeaveDTO(
            @SerialName("qualified_conversation") val qualifiedConversation: ConversationId,
            @SerialName("qualified_from") val qualifiedFrom: UserId,
            @SerialName("time") val time: Instant,
            @SerialName("data") val removedUsers: ConversationMemberRemovedDTO,
            @SerialName("from") val from: String
        ) : Conversation()

        @Serializable
        @SerialName("conversation.member-update")
        data class MemberUpdateDTO(
            @SerialName("qualified_conversation") val qualifiedConversation: ConversationId,
            @SerialName("qualified_from") val qualifiedFrom: UserId,
            @SerialName("time") val time: String,
            @SerialName("from") val from: String,
            @SerialName("data") val roleChange: ConversationRoleChange
        ) : Conversation()

        @Serializable
        @SerialName("conversation.typing")
        data class ConversationTypingDTO(
            @SerialName("qualified_conversation") val qualifiedConversation: ConversationId,
            @SerialName("qualified_from") val qualifiedFrom: UserId,
            @SerialName("time") val time: String,
            @SerialName("from") val from: String,
            @SerialName("data") val status: TypingIndicatorStatusDTO,
        ) : Conversation()

        @Serializable
        @SerialName("conversation.otr-message-add")
        data class NewMessageDTO(
            @SerialName("qualified_conversation") val qualifiedConversation: ConversationId,
            @SerialName("qualified_from") val qualifiedFrom: UserId,
            @SerialName("conversation") val conversation: String? = null,
            @SerialName("time") val time: Instant,
            @SerialName("from") val from: String? = null,
            @SerialName("data") val data: MessageEventData,
        ) : Conversation()

        @Serializable
        @SerialName("conversation.access-update")
        data class AccessUpdate(
            @SerialName("qualified_conversation") val qualifiedConversation: ConversationId,
            @SerialName("data") val data: ConversationAccessInfoDTO,
            @SerialName("qualified_from") val qualifiedFrom: UserId,
        ) : Conversation()

        @Serializable
        @SerialName("conversation.code-update")
        data class CodeUpdated(
            @SerialName("qualified_conversation") val qualifiedConversation: ConversationId,
            @SerialName("data") val data: ConversationInviteLinkResponse,
            @SerialName("qualified_from") val qualifiedFrom: UserId,
        ) : Conversation()

        @Serializable
        @SerialName("conversation.code-delete")
        data class CodeDeleted(
            @SerialName("qualified_conversation") val qualifiedConversation: ConversationId,
            @SerialName("qualified_from") val qualifiedFrom: UserId,
        ) : Conversation()

        @Serializable
        @SerialName("conversation.receipt-mode-update")
        data class ReceiptModeUpdate(
            @SerialName("qualified_conversation") val qualifiedConversation: ConversationId,
            @SerialName("data") val data: ConversationReceiptModeDTO,
            @SerialName("qualified_from") val qualifiedFrom: UserId,
        ) : Conversation()

        @Serializable
        @SerialName("conversation.message-timer-update")
        data class MessageTimerUpdate(
            @SerialName("qualified_conversation") val qualifiedConversation: ConversationId,
            @SerialName("data") val data: ConversationMessageTimerDTO,
            @SerialName("qualified_from") val qualifiedFrom: UserId,
            @SerialName("time") val time: Instant
        ) : Conversation()

        @Serializable
        @SerialName("conversation.mls-message-add")
        data class NewMLSMessageDTO(
            @SerialName("qualified_conversation") val qualifiedConversation: ConversationId,
            @SerialName("qualified_from") val qualifiedFrom: UserId,
            @SerialName("time") val time: Instant,
            @SerialName("data") val message: String,
            @SerialName("subconv") val subconversation: String?,
        ) : Conversation()

        @Serializable
        @SerialName("conversation.decrypted-mls-message")
        data class DecryptedMLSBatchDTO(
            @SerialName("qualified_conversation") val qualifiedConversation: ConversationId,
            @SerialName("data") val decryptedMessages: List<DecryptedMLSMessageDTO>,
            @SerialName("subconv") val subconversation: String?,
        ) : Conversation()

        @Serializable
        data class DecryptedMLSMessageDTO(
            @SerialName("qualified_from") val qualifiedFrom: UserId,
            @SerialName("time") val time: Instant,
            @SerialName("data") val decryptedContent: String,
        )

        @Serializable
        @SerialName("conversation.mls-welcome")
        data class MLSWelcomeDTO(
            @SerialName("qualified_conversation") val qualifiedConversation: ConversationId,
            @SerialName("qualified_from") val qualifiedFrom: UserId,
            @SerialName("data") val message: String,
            @SerialName("from") val from: String
        ) : Conversation()

        @Serializable
        @SerialName("conversation.protocol-update")
        data class ProtocolUpdate(
            @SerialName("qualified_conversation") val qualifiedConversation: ConversationId,
            @SerialName("data") val data: ConversationProtocolDTO,
            @SerialName("qualified_from") val qualifiedFrom: UserId,
        ) : Conversation()

        @Serializable
        @SerialName("conversation.add-permission-update")
        data class ChannelAddPermissionUpdate(
            @SerialName("conversation") val conversation: String,
            @SerialName("qualified_conversation") val qualifiedConversation: ConversationId,
            @SerialName("data") val data: ChannelAddPermissionDTO,
            @SerialName("from") val from: String,
            @SerialName("qualified_from") val qualifiedFrom: UserId,
            @SerialName("time") val time: Instant
        ) : Conversation()
    }

    @Serializable
    sealed class Team : EventContentDTO() {
        @Serializable
        @SerialName("team.member-leave")
        data class MemberLeave(
            @SerialName("data") val teamMember: TeamMemberIdData,
            @SerialName("team") val teamId: TeamId,
            @SerialName("time") val time: Instant,
        ) : Team()
    }

    @Serializable
    sealed class User : EventContentDTO() {

        @Serializable
        @SerialName("user.client-add")
        data class NewClientDTO(
            @SerialName("client") val client: ClientDTO,
        ) : User()

        @Serializable
        @SerialName("user.client-remove")
        data class ClientRemoveDTO(
            @SerialName("client") val client: RemoveClientEventData,
        ) : User()

        @Serializable
        @SerialName("user.update")
        data class UpdateDTO(
            @SerialName("user") val userData: UserUpdateEventData,
        ) : User()

        // TODO user.identity-remove

        @Serializable
        @SerialName("user.connection")
        data class NewConnectionDTO(
            @SerialName("connection") val connection: ConnectionDTO,
        ) : User()

        @Serializable
        @SerialName("user.legalhold-request")
        data class NewLegalHoldRequestDTO(
            @SerialName("client") val clientId: ClientIdDTO,
            @SerialName("last_prekey") val lastPreKey: LastPreKeyDTO,
            @SerialName("id") val id: String,
        ) : User()

        @Serializable
        @SerialName("user.legalhold-enable")
        data class LegalHoldEnabledDTO(
            @SerialName("id") val id: String
        ) : User()

        @Serializable
        @SerialName("user.legalhold-disable")
        data class LegalHoldDisabledDTO(
            @SerialName("id") val id: String
        ) : User()

        // TODO user.push-remove

        @Serializable
        @SerialName("user.delete")
        data class UserDeleteDTO(
            @SerialName("id") val id: String,
            @SerialName("qualified_id") val userId: UserId
        ) : User()
    }

    @Serializable
    sealed class Federation : EventContentDTO() {

        @Serializable
        @SerialName("federation.delete")
        data class FederationDeleteDTO(
            @SerialName("domain") val domain: String
        ) : Federation()

        @Serializable
        @SerialName("federation.connectionRemoved")
        data class FederationConnectionRemovedDTO(
            @SerialName("domains") val domains: List<String>
        ) : Federation()

    }

    @Serializable
    sealed class FeatureConfig : EventContentDTO() {
        @Serializable(with = JsonCorrectingSerializer::class)
        @SerialName("feature-config.update")
        data class FeatureConfigUpdatedDTO(
            @SerialName("data") val data: FeatureConfigData,
        ) : FeatureConfig()
    }

    @Serializable
    sealed class UserProperty : EventContentDTO() {
        @Serializable
        @SerialName("user.properties-set")
        data class PropertiesSetDTO(
            @SerialName("key") val key: String,
            @SerialName("value") val value: FieldKeyValue,
        ) : UserProperty()

        @Serializable
        @SerialName("user.properties-delete")
        data class PropertiesDeleteDTO(
            @SerialName("key") val key: String,
        ) : UserProperty()
    }

    @Serializable(with = FieldKeyValueDeserializer::class)
    sealed interface FieldKeyValue

    @Serializable
    @JvmInline
    value class FieldKeyNumberValue(val value: Int) : FieldKeyValue

    @Serializable
    @JvmInline
    value class FieldUnknownValue(val value: String) : FieldKeyValue

    @Serializable
    @JvmInline
    value class FieldLabelListValue(val value: LabelListResponseDTO) : FieldKeyValue

    @Serializable
    @SerialName("unknown")
    data class Unknown(val type: String) : EventContentDTO()
}

@OptIn(ExperimentalSerializationApi::class, InternalSerializationApi::class)
object FieldKeyValueDeserializer : KSerializer<EventContentDTO.FieldKeyValue> {
    override val descriptor = buildSerialDescriptor("value", PolymorphicKind.SEALED)
    override fun serialize(encoder: Encoder, value: EventContentDTO.FieldKeyValue) {
        when (value) {
            is EventContentDTO.FieldKeyNumberValue -> encoder.encodeInt(value.value)
            is EventContentDTO.FieldLabelListValue -> encoder.encodeSerializableValue(
                EventContentDTO.FieldLabelListValue.serializer(),
                value
            )

            is EventContentDTO.FieldUnknownValue -> throw SerializationException("Not handled yet")
        }
    }

    @Suppress("TooGenericExceptionCaught", "ReturnCount")
    override fun deserialize(decoder: Decoder): EventContentDTO.FieldKeyValue {
        try {
            val input = decoder as? JsonDecoder ?: throw SerializationException("Expected JsonDecoder")
            return when (val element = input.decodeJsonElement()) {
                is JsonPrimitive -> {
                    if (element.isString) {
                        EventContentDTO.FieldUnknownValue(element.content)
                    } else {
                        EventContentDTO.FieldKeyNumberValue(element.int)
                    }
                }

                is JsonObject -> {
                    if (element.containsKey("labels")) {
                        return input.json.decodeFromJsonElement(EventContentDTO.FieldLabelListValue.serializer(), element)
                    }
                    EventContentDTO.FieldUnknownValue(element.toString())
                }

                else -> throw SerializationException("Unexpected JSON element type: ${element::class.simpleName}")
            }
        } catch (exception: Exception) {
            val jsonElement = decoder.toJsonElement().toString()
            kaliumUtilLogger.d("Error deserializing 'user.properties-set', prop: $jsonElement")
            kaliumUtilLogger.w("Error deserializing 'user.properties-set', error: $exception")
            return EventContentDTO.FieldUnknownValue(jsonElement)
        }
    }
}
