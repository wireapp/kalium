package com.wire.kalium.testservice.models

import com.fasterxml.jackson.annotation.JsonProperty

class Saying(private val id: Long, private val content: String?) {

    @JsonProperty
    fun getId(): Long {
        return id
    }

    @JsonProperty
    fun getContent(): String? {
        return content
    }
}
