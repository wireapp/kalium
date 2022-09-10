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
    suspend fun saveToken(tokenEntity: TokenEntity) {
        kaliumPreferences.putSerializable(
            "user_tokens_${tokenEntity.userId.value}@${tokenEntity.userId.domain}",
            tokenEntity,
            TokenEntity.serializer()
        )
    }

    fun getToken(userId: UserIDEntity): TokenEntity? {
        return kaliumPreferences.getSerializable(
            "user_tokens_${userId.value}@${userId.domain}",
            TokenEntity.serializer()
        )
    }
}
