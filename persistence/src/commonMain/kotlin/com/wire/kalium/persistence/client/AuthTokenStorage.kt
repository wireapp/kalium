package com.wire.kalium.persistence.client

import com.wire.kalium.persistence.dao.UserIDEntity
import com.wire.kalium.persistence.kmm_settings.KaliumPreferences
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AuthTokenEntity(
    @SerialName("user_id") val userId: UserIDEntity,
    @SerialName("access_token") val accessToken: String,
    @SerialName("refresh_token") val refreshToken: String,
    @SerialName("token_type") val tokenType: String
)

class AuthTokenStorage(
    private val kaliumPreferences: KaliumPreferences
) {
    fun addOrReplace(authTokenEntity: AuthTokenEntity) {
        kaliumPreferences.putSerializable(
            getTokenKey(authTokenEntity.userId),
            authTokenEntity,
            AuthTokenEntity.serializer()
        )
    }

    fun updateToken(
        userId: UserIDEntity,
        accessToken: String,
        tokenType: String,
        refreshToken: String?,
    ): AuthTokenEntity {
        val key = getTokenKey(userId)
        val newToken: AuthTokenEntity = (refreshToken?.let {
            AuthTokenEntity(userId, accessToken, refreshToken, tokenType)
        } ?: run {
            kaliumPreferences.getSerializable(key, AuthTokenEntity.serializer())?.copy(
                accessToken = accessToken,
                tokenType = tokenType
            )
        }) ?: throw IllegalStateException("No token found for user")

        kaliumPreferences.putSerializable(
            key,
            newToken,
            AuthTokenEntity.serializer()
        )

        return newToken
    }

    fun getToken(userId: UserIDEntity): AuthTokenEntity? {
        return kaliumPreferences.getSerializable(
            getTokenKey(userId),
            AuthTokenEntity.serializer()
        )
    }


    private fun getTokenKey(userId: UserIDEntity): String {
        return "user_tokens_${userId.value}@${userId.domain}"
    }
}
