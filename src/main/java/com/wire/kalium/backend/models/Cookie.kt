package com.wire.kalium.backend.models

data class Cookie (
        var name: String,
        var value: String
)

fun Cookie.toJavaxCookie(): javax.ws.rs.core.Cookie = javax.ws.rs.core.Cookie(this.name, this.value)
