package com.wire.kalium.network.api.notification

import com.wire.kalium.network.api.ConversationId
import com.wire.kalium.network.api.UserId
import com.wire.kalium.network.api.conversation.ConversationMembers
import com.wire.kalium.network.api.conversation.ConversationResponse
import com.wire.kalium.network.api.conversation.ConversationUsers
import com.wire.kalium.network.api.conversation.model.ConversationAccessInfoDTO
import com.wire.kalium.network.api.featureConfigs.FeatureConfigData
import com.wire.kalium.network.api.featureConfigs.FeatureFlagStatusDTO
import com.wire.kalium.network.api.notification.conversation.MessageEventData
import com.wire.kalium.network.api.notification.user.NewClientEventData
import com.wire.kalium.network.api.notification.user.RemoveClientEventData
import com.wire.kalium.network.api.user.connection.ConnectionDTO
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.element
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.encoding.decodeStructure
import kotlinx.serialization.encoding.encodeStructure
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonTransformingSerializer
import kotlinx.serialization.json.jsonObject

@Serializable
data class EventResponse(
    @Serializable
    @SerialName("id") val id: String,
    @SerialName("payload") val payload: List<EventContentDTO>?,
    @SerialName("transient") val transient: Boolean = false
)

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
        @SerialName("conversation.access-update")
        data class AccessUpdate(
            @SerialName("qualified_conversation") val qualifiedConversation: ConversationId,
            @SerialName("data") val data: ConversationAccessInfoDTO,
            @SerialName("qualified_from") val qualifiedFrom: UserId,
        ) : Conversation()

        @Serializable
        @SerialName("conversation.create")
        data class NewConversationDTO(
            @SerialName("qualified_conversation") val qualifiedConversation: ConversationId,
            @SerialName("qualified_from") val qualifiedFrom: UserId,
            val time: String,
            @SerialName("data") val data: ConversationResponse,
        ) : Conversation()

        @Serializable
        @SerialName("conversation.otr-message-add")
        data class NewMessageDTO(
            @SerialName("qualified_conversation") val qualifiedConversation: ConversationId,
            @SerialName("qualified_from") val qualifiedFrom: UserId,
            val time: String,
            @SerialName("data") val data: MessageEventData,
        ) : Conversation()

        @Serializable
        @SerialName("conversation.member-join")
        data class MemberJoinDTO(
            @SerialName("qualified_conversation") val qualifiedConversation: ConversationId,
            @SerialName("qualified_from") val qualifiedFrom: UserId,
            val time: String,
            @SerialName("data") val members: ConversationMembers,
            @Deprecated("use qualifiedFrom", replaceWith = ReplaceWith("this.qualifiedFrom")) @SerialName("from") val from: String
        ) : Conversation()

        @Serializable
        @SerialName("conversation.member-leave")
        data class MemberLeaveDTO(
            @SerialName("qualified_conversation") val qualifiedConversation: ConversationId,
            @SerialName("qualified_from") val qualifiedFrom: UserId,
            val time: String,
            // TODO: rename members to something else since the name is confusing (it's only userIDs)
            @SerialName("data") val members: ConversationUsers,
            @SerialName("from") val from: String
        ) : Conversation()

        @Serializable
        @SerialName("conversation.mls-welcome")
        data class MLSWelcomeDTO(
            @SerialName("qualified_conversation") val qualifiedConversation: ConversationId,
            @SerialName("qualified_from") val qualifiedFrom: UserId,
            @SerialName("data") val message: String,
            @SerialName("from") val from: String
        ) : Conversation()

        @Serializable
        @SerialName("conversation.mls-message-add")
        data class NewMLSMessageDTO(
            @SerialName("qualified_conversation") val qualifiedConversation: ConversationId,
            @SerialName("qualified_from") val qualifiedFrom: UserId,
            val time: String,
            @SerialName("data") val message: String,
        ) : Conversation()

        @Serializable
        @SerialName("conversation.delete")
        data class DeletedConversationDTO(
            @SerialName("qualified_conversation") val qualifiedConversation: ConversationId,
            @SerialName("qualified_from") val qualifiedFrom: UserId,
            val time: String
        ) : Conversation()
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
    sealed class User : EventContentDTO() {

        @Serializable
        @SerialName("user.client-add")
        data class NewClientDTO(
            @SerialName("client") val client: NewClientEventData,
        ) : User()

        @Serializable
        @SerialName("user.client-remove")
        data class ClientRemoveDTO(
            @SerialName("client") val client: RemoveClientEventData,
        ) : User()

        @Serializable
        @SerialName("user.connection")
        data class NewConnectionDTO(
            @SerialName("connection") val connection: ConnectionDTO,
        ) : User()

        @Serializable
        @SerialName("user.delete")
        data class UserDeleteDTO(
            @SerialName("id") val id: String,
        ) : User()
    }

    @Serializable
    @SerialName("unknown")
    object Unknown : EventContentDTO()
}
