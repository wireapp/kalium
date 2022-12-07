package com.wire.kalium.network.api.base.authenticated.conversation.model

import com.wire.kalium.network.api.base.authenticated.notification.EventContentDTO
import com.wire.kalium.network.api.base.model.ConversationAccessDTO
import com.wire.kalium.network.api.base.model.ConversationAccessRoleDTO
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNames
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonTransformingSerializer
import kotlinx.serialization.json.jsonObject

/**
 * Handwritten serializer of the ConversationAccessInfoDTO because we want to extend it with the
 * `JsonCorrectingSerializer`, which is not possible using the plugin generated serializer.
 */
object ConversationAccessInfoDTOSerializer : KSerializer<ConversationAccessInfoDTO> {

    override val descriptor: SerialDescriptor = ConversationAccessInfoDTOSurrogate.serializer().descriptor

    override fun deserialize(decoder: Decoder): ConversationAccessInfoDTO {
        val surrogate = decoder.decodeSerializableValue(ConversationAccessInfoDTOSurrogate.serializer())
        return ConversationAccessInfoDTO(surrogate.access, surrogate.accessRole)
    }

    override fun serialize(encoder: Encoder, value: ConversationAccessInfoDTO) {
        val surrogate = ConversationAccessInfoDTOSurrogate(value.access, value.accessRole)
        encoder.encodeSerializableValue(ConversationAccessInfoDTOSurrogate.serializer(), surrogate)
    }
}

/**
 * Transforms the conversation access info JSON by deleting the `access_role` field if it's a primitive
 * value indicates that it's a legacy field, which interferes with the @JsonNames alternative name parsing.
 */
object JsonCorrectingSerializer :
    JsonTransformingSerializer<ConversationAccessInfoDTO>(ConversationAccessInfoDTOSerializer) {
    override fun transformDeserialize(element: JsonElement): JsonElement {
        return JsonObject(
            element.jsonObject.toMutableMap().apply {
                val foo = this["access_role"]
                if (foo is JsonPrimitive && foo.isString) {
                    this.remove("access_role")
                }
            }
        )
    }
}

/**
 * **Deprecation info**: Since API v3 `access_role_v2` is deprecated and will be replaced by `access_role`, but until all servers have
 * been upgraded to have API v3 has its minimum supported version and all previously stored events have expired we need support both cases.
 *
 * Further info: https://wearezeta.atlassian.net/wiki/spaces/ENGINEERIN/pages/672006169/API+changes+v2+v3
 */
@Serializable(with = JsonCorrectingSerializer::class)
@OptIn(ExperimentalSerializationApi::class)
data class ConversationAccessInfoDTO constructor(
    @SerialName("access")
    val access: Set<ConversationAccessDTO>,
    @SerialName("access_role_v2") @JsonNames("access_role")
    val accessRole: Set<ConversationAccessRoleDTO> = ConversationAccessRoleDTO.DEFAULT_VALUE_WHEN_NULL
)


/**
 * Surrogate class of the ConversationAccessInfoDTO to provide access to the plugin generated
 * serializer and also use with @Serializable(with=).
 *
 * Can be removed once https://github.com/Kotlin/kotlinx.serialization/issues/1169 is resolved
 * or when the API V3 is the minimum supported version, see [ConversationAccessInfoDTO].
 */
@Serializable
@SerialName("ConversationAccessInfoDTO")
@OptIn(ExperimentalSerializationApi::class)
private data class ConversationAccessInfoDTOSurrogate constructor(
    @SerialName("access")
    val access: Set<ConversationAccessDTO>,
    @SerialName("access_role_v2") @JsonNames("access_role")
    val accessRole: Set<ConversationAccessRoleDTO> = ConversationAccessRoleDTO.DEFAULT_VALUE_WHEN_NULL
)

sealed class UpdateConversationAccessResponse {
    object AccessUnchanged : UpdateConversationAccessResponse()
    data class AccessUpdated(val event: EventContentDTO.Conversation.AccessUpdate) : UpdateConversationAccessResponse()
}
