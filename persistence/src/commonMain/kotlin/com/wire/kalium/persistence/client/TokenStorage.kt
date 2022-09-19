package com.wire.kalium.persistence.client

import com.wire.kalium.persistence.kmm_settings.KaliumPreferences
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.coroutines.CoroutineContext

interface TokenStorage {
    /**
     * to save the token that generated from the service so it can be used to register this token in the server later
     * ex: firebase token
     * the transport here is the type of the token ("GCM,APNS")
     */
    suspend fun saveToken(token: String, transport: String)

    /**
     * get the saved token with it's type
     */
    suspend fun getToken(): NotificationTokenEntity?
}

@Serializable
data class NotificationTokenEntity(
    @SerialName("token") val token: String,
    @SerialName("transport") val transport: String
)


internal class TokenStorageImpl internal constructor(
    private val kaliumPreferences: KaliumPreferences,
    private val coroutineContext: CoroutineContext
) : TokenStorage {

    override suspend fun saveToken(token: String, transport: String) = withContext(coroutineContext) {
        kaliumPreferences.putSerializable(
            NOTIFICATION_TOKEN,
            NotificationTokenEntity(token, transport),
            NotificationTokenEntity.serializer()
        )
    }

    override suspend fun getToken(): NotificationTokenEntity? = withContext(coroutineContext) {
        kaliumPreferences.getSerializable(NOTIFICATION_TOKEN, NotificationTokenEntity.serializer())
    }

    private companion object {
        const val NOTIFICATION_TOKEN = "notification_token"
    }

}
