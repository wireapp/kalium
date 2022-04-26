package com.wire.kalium.persistence.client

import com.wire.kalium.persistence.kmm_settings.KaliumPreferences

interface TokenStorage {
    var registeredFCMToken: String?
}

class TokenStorageImpl(private val kaliumPreferences: KaliumPreferences) : TokenStorage {
    override var registeredFCMToken: String?
        get() = kaliumPreferences.getString(FCM_TOKEN)
        set(value) = kaliumPreferences.putString(FCM_TOKEN, value)

    private companion object {
        const val FCM_TOKEN = "fcm_token"
    }
}
