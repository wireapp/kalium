package com.wire.kalium.persistence.client

import com.wire.kalium.persistence.kmm_settings.KaliumPreferences
import kotlinx.serialization.Serializable

interface TokenStorage {
    fun saveToken(notificationTokenEntity: NotificationTokenEntity)
    fun getToken(): NotificationTokenEntity?
}

@Serializable
data class NotificationTokenEntity(val token: String, val transport: String)


class TokenStorageImpl(private val kaliumPreferences: KaliumPreferences) : TokenStorage {

    override fun saveToken(notificationTokenEntity: NotificationTokenEntity) {
        kaliumPreferences.putSerializable(NOTIFICATION_TOKEN, notificationTokenEntity, NotificationTokenEntity.serializer())
    }

    override fun getToken(): NotificationTokenEntity? =
        kaliumPreferences.getSerializable(NOTIFICATION_TOKEN, NotificationTokenEntity.serializer())

    private companion object {
        const val NOTIFICATION_TOKEN = "notification_token"
    }

}
