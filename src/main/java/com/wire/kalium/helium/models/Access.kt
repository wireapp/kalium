package com.wire.kalium.helium.models

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import java.util.*

@JsonIgnoreProperties(ignoreUnknown = true)
class Access {
    @set:JsonIgnore
    @JsonIgnore
    var cookie: Cookie? = null

    @JsonProperty("user")
    var userId: UUID? = null

    @JsonProperty("access_token")
    var accessToken: String? = null

    @JsonProperty("expires_in")
    var expiresIn = 0

    @JsonProperty("token_type")
    var tokenType: String? = null

    @JsonIgnore
    fun hasCookie(): Boolean {
        return cookie != null
    }
}
