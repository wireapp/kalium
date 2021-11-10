package com.wire.kalium.backend.models

import java.util.UUID
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonInclude

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
/**
 * TODO: Remove Jackson, remove lateinits, replace vars with vals
 */
class SystemMessage {
    var id: UUID? = null
    var type: String? = null
    var time: String? = null
    var from: UUID? = null
    lateinit var conversation: Conversation
    var convId: UUID? = null
    lateinit var users: MutableList<UUID>
}
