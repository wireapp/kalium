package com.wire.kalium.network.api.user.logout

import kotlinx.serialization.Serializable

@Serializable
data class Cookie(
    var name: String,
    var value: String
)
