package com.wire.kalium.backend.models

import java.util.UUID
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonInclude

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
class SystemMessage {
    var id: UUID? = null
    var type: String? = null
    var time: String? = null
    var from: UUID? = null
    var conversation: Conversation? = null
    var convId: UUID? = null
    var users: MutableList<UUID?>? = null
}
