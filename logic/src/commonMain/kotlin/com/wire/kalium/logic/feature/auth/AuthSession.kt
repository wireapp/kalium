package com.wire.kalium.logic.feature.auth

import com.wire.kalium.logic.configuration.server.ServerConfig
import com.wire.kalium.logic.data.logout.LogoutReason
import com.wire.kalium.logic.data.user.SsoId
import com.wire.kalium.logic.data.user.UserId
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

sealed class AccountInfo {
    abstract val userId: UserId

    data class Valid(override val userId: UserId) : AccountInfo()
    data class Invalid(
        override val userId: UserId,
        val logoutReason: LogoutReason
    ) : AccountInfo()

    @OptIn(ExperimentalContracts::class)
    fun isValid(): Boolean {
        contract {
            returns(true) implies (this@AccountInfo is Valid)
            returns(false) implies (this@AccountInfo is Invalid)
        }
        return this is Valid
    }
}

data class Account(
    val info: AccountInfo,
    val serverConfig: ServerConfig,
    val ssoId: SsoId?
)

data class AuthTokens(
    val userId: UserId,
    val accessToken: String,
    val refreshToken: String,
    val tokenType: String
)
