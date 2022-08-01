package com.wire.kalium.testservice.models

import com.fasterxml.jackson.annotation.JsonProperty

class CustomBackend(private val name: String, private val rest: String, private val ws: String) {

    @JsonProperty
    fun getName(): String {
        return name
    }

    @JsonProperty
    fun getRest(): String {
        return rest
    }

    @JsonProperty
    fun getWs(): String {
        return ws
    }

}
