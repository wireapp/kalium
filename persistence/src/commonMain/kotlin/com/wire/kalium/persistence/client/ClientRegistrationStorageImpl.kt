package com.wire.kalium.persistence.client

import com.wire.kalium.persistence.kmm_settings.KaliumPreferences

interface ClientRegistrationStorage {
    var registeredClientId: String?
    var registeredFCMToken: String?
}

class ClientRegistrationStorageImpl(private val kaliumPreferences: KaliumPreferences): ClientRegistrationStorage {

    override var registeredClientId: String?
        get() = kaliumPreferences.getString(REGISTERED_CLIENT_ID_KEY)
        set(value) = kaliumPreferences.putString(REGISTERED_CLIENT_ID_KEY, value)

    override var registeredFCMToken: String?
        get() = kaliumPreferences.getString(FCM_TOKEN)
        set(value) = kaliumPreferences.putString(FCM_TOKEN, value)

    private companion object {
        const val REGISTERED_CLIENT_ID_KEY = "registered_client_id"
        const val FCM_TOKEN = "fcm_token"
    }
}
