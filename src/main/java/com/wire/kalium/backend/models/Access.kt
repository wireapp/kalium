package com.wire.kalium.backend.models

import java.util.*

data class Access(
        // FIXME: cookie can change ?
        var cookie: Cookie,
        val userId: UUID,
        val access_token: String,
        val expires_in: Int,
        val token_type: String,
)
