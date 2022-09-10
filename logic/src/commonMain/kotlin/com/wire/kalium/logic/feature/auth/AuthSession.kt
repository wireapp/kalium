package com.wire.kalium.logic.feature.auth

import com.wire.kalium.logic.configuration.server.ServerConfig
import com.wire.kalium.logic.data.id.QualifiedID
import com.wire.kalium.logic.data.logout.LogoutReason
import com.wire.kalium.logic.data.user.SsoId
import com.wire.kalium.logic.data.user.UserId
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

sealed class AccountInfo {
    abstract val userId: UserId

    data class Valid(override val userId: UserId) : AccountInfo()
    data class Invalid(
        override val userId: UserId,
        val logoutReason: LogoutReason
    ) : AccountInfo()
}

data class Account (
    val info: AccountInfo,
     val serverConfig: ServerConfig,
     val ssoId: SsoId?
)

data class AuthTokens(
    val accessToken: String,
    val refreshToken: String,
    val tokenType: String,
    val userId: UserId
)
