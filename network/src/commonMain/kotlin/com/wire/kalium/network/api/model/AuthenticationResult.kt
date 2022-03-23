package com.wire.kalium.network.api.model

import com.wire.kalium.network.api.SessionDTO

data class AuthenticationResult (
    val session: SessionDTO,
    val  selfUser: UserDTO
)
