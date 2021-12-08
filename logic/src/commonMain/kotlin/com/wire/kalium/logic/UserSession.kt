package com.wire.kalium.logic

import com.wire.kalium.network.api.UserId

data class UserSession(
    val userId: UserId,
    val authToken: String,
    val refreshToken: String,
    val tokenType: String
)

inline fun UserSession.use(action: UserSessionScope.() -> Unit) {

}

fun main() {
    val session = UserSession()

    session.use {

    }
}
