package com.wire.kalium.api.message

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

sealed class SendMessageResponse

@Serializable
data class MissingDevicesResponse(
        @SerialName("missing") val missing: HashMap<String, List<String>>,
        @SerialName("redundant") val redundant: HashMap<String, List<String>>,
        @SerialName("deleted") val deleted: HashMap<String, List<String>>
) : SendMessageResponse()

object MessageSent : SendMessageResponse()
