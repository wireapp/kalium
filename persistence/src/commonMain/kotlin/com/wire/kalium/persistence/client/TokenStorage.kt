package com.wire.kalium.persistence.client

import com.wire.kalium.persistence.kmm_settings.KaliumPreferences
import kotlinx.serialization.Serializable

interface TokenStorage {
    /**
     * to save the token that generated from the service so it can be used to register this token in the server later
     * ex: firebase token
     * the transport here is the type of the token ("GCM,APNS")
     */
    fun saveToken(token: String, transport: String)

    /**
     * get the saved token with it's type
     */
    fun getToken(): NotificationTokenEntity?
}

@Serializable
data class NotificationTokenEntity(val token: String, val transport: String)


class TokenStorageImpl(private val kaliumPreferences: KaliumPreferences) : TokenStorage {

    override fun saveToken(token: String, transport: String) {
        kaliumPreferences.putSerializable(
            NOTIFICATION_TOKEN,
            NotificationTokenEntity(token, transport),
            NotificationTokenEntity.serializer()
        )
    }

    override fun getToken(): NotificationTokenEntity? =
        kaliumPreferences.getSerializable(NOTIFICATION_TOKEN, NotificationTokenEntity.serializer())

    private companion object {
        const val NOTIFICATION_TOKEN = "notification_token"
    }

}
