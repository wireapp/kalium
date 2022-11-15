package com.wire.kalium.persistence.client

import com.wire.kalium.persistence.dao.UserIDEntity
import com.wire.kalium.persistence.kmmSettings.KaliumPreferences
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AuthTokenEntity(
    @SerialName("user_id") val userId: UserIDEntity,
    @SerialName("access_token") val accessToken: String,
    @SerialName("refresh_token") val refreshToken: String,
    @SerialName("token_type") val tokenType: String
)

@Serializable
data class ProxyCredentialsEntity(
    @SerialName("username") val username: String,
    @SerialName("password") val password: String,
)

class AuthTokenStorage internal constructor(
    private val kaliumPreferences: KaliumPreferences
) {
    fun addOrReplace(authTokenEntity: AuthTokenEntity, proxyCredentialsEntity: ProxyCredentialsEntity?) {
        kaliumPreferences.putSerializable(
            tokenKey(authTokenEntity.userId),
            authTokenEntity,
            AuthTokenEntity.serializer()
        )

        proxyCredentialsEntity?.let {
            kaliumPreferences.putSerializable(proxyCredentialsKey(authTokenEntity.userId), it, ProxyCredentialsEntity.serializer())
        }
    }

    fun updateToken(
        userId: UserIDEntity,
        accessToken: String,
        tokenType: String,
        refreshToken: String?,
    ): AuthTokenEntity {
        val key = tokenKey(userId)
        val newToken: AuthTokenEntity = (refreshToken?.let {
            AuthTokenEntity(userId, accessToken, refreshToken, tokenType)
        } ?: run {
            kaliumPreferences.getSerializable(key, AuthTokenEntity.serializer())?.copy(
                accessToken = accessToken,
                tokenType = tokenType
            )
        }) ?: throw error("No token found for user")

        kaliumPreferences.putSerializable(
            key,
            newToken,
            AuthTokenEntity.serializer()
        )
        return newToken
    }

    // TODO: make suspendable
    fun getToken(userId: UserIDEntity): AuthTokenEntity? =
        kaliumPreferences.getSerializable(
            tokenKey(userId),
            AuthTokenEntity.serializer()
        )

    fun deleteToken(userId: UserIDEntity) {
        kaliumPreferences.remove(tokenKey(userId))
        kaliumPreferences.remove(proxyCredentialsKey(userId))
    }

    fun proxyCredentials(userId: UserIDEntity): ProxyCredentialsEntity? =
        kaliumPreferences.getSerializable(proxyCredentialsKey(userId), ProxyCredentialsEntity.serializer())

    private fun tokenKey(userId: UserIDEntity): String {
        return "user_tokens_${userId.value}@${userId.domain}"
    }

    private fun proxyCredentialsKey(userId: UserIDEntity): String {
        return "proxy_credentials_${userId.value}@${userId.domain}"
    }
}
