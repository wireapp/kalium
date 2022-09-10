package com.wire.kalium.persistence.client

import com.wire.kalium.persistence.dao.UserIDEntity
import com.wire.kalium.persistence.kmm_settings.KaliumPreferences
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TokenEntity(
    @SerialName("user_id") val userId: UserIDEntity,
    @SerialName("access_token") val accessToken: String,
    @SerialName("refresh_token") val refreshToken: String,
    @SerialName("token_type") val tokenType: String
)

class AuthTokenStorage(
    private val kaliumPreferences: KaliumPreferences
) {
    fun addOrReplace(tokenEntity: TokenEntity) {
        kaliumPreferences.putSerializable(
            getTokenKey(tokenEntity.userId),
            tokenEntity,
            TokenEntity.serializer()
        )
    }

    fun updateToken(
        userId: UserIDEntity,
        accessToken: String,
        tokenType: String,
        refreshToken: String?,
    ): TokenEntity {
        val key = getTokenKey(userId)
        val newToken: TokenEntity = (refreshToken?.let {
            TokenEntity(userId, accessToken, refreshToken, tokenType)
        } ?: run {
            kaliumPreferences.getSerializable(key, TokenEntity.serializer())?.copy(
                accessToken = accessToken,
                tokenType = tokenType
            )
        }) ?: throw IllegalStateException("No token found for user")

        kaliumPreferences.putSerializable(
            key,
            newToken,
            TokenEntity.serializer()
        )

        return newToken
    }

    fun getToken(userId: UserIDEntity): TokenEntity? {
        return kaliumPreferences.getSerializable(
            getTokenKey(userId),
            TokenEntity.serializer()
        )
    }


    private fun getTokenKey(userId: UserIDEntity): String {
        return "user_tokens_${userId.value}@${userId.domain}"
    }
}
