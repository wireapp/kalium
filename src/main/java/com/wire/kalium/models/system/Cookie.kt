package com.wire.kalium.models.system

import kotlinx.serialization.Serializable

@Serializable
data class Cookie(
        var name: String,
        var value: String
)

fun Cookie.toJavaxCookie(): javax.ws.rs.core.Cookie = javax.ws.rs.core.Cookie(this.name, this.value)
