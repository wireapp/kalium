package com.wire.kalium.persistence.client

import com.wire.kalium.persistence.kmmSettings.KaliumPreferences
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

interface TokenStorage {
    /**
     * to save the token that generated from the service so it can be used to register this token in the server later
     * ex: firebase token
     * the transport here is the type of the token ("GCM,APNS")
     */
    fun saveToken(token: String, transport: String, applicationId: String)

    /**
     * get the saved token with it's type
     */
    fun getToken(): NotificationTokenEntity?
}

@Serializable
data class NotificationTokenEntity(
    @SerialName("token") val token: String,
    @SerialName("transport") val transport: String,
    @SerialName("applicationId") val applicationId: String,
)

internal class TokenStorageImpl internal constructor(
    private val kaliumPreferences: KaliumPreferences
) : TokenStorage {

    override fun saveToken(token: String, transport: String, applicationId: String) {
        kaliumPreferences.putSerializable(
            NOTIFICATION_TOKEN,
            NotificationTokenEntity(token, transport, applicationId),
            NotificationTokenEntity.serializer()
        )
    }

    override fun getToken(): NotificationTokenEntity? =
        kaliumPreferences.getSerializable(NOTIFICATION_TOKEN, NotificationTokenEntity.serializer())

    private companion object {
        const val NOTIFICATION_TOKEN = "notification_token"
    }

}
