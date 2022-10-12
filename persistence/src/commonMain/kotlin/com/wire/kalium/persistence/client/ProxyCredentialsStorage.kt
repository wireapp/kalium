package com.wire.kalium.persistence.client

import com.wire.kalium.persistence.kmmSettings.KaliumPreferences
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable


interface ProxyCredentialsStorage {
    /**
     * to save the proxy credentials after the user add and verify correct proxy config at the login
     */
    fun saveProxyCredentials(username: String, password: String)

    /**
     * get the proxy credentials to be used with the authenticated client
     */
    fun getProxyCredentials(): ProxyCredentialsEntity?
}

@Serializable
data class ProxyCredentialsEntity(
    @SerialName("username") val username: String,
    @SerialName("password") val password: String,
)

internal class ProxyCredentialsStorageImpl internal constructor(
    private val kaliumPreferences: KaliumPreferences
) : ProxyCredentialsStorage {

    override fun saveProxyCredentials(username: String, password: String) {
        kaliumPreferences.putSerializable(
            PROXY_CREDENTIALS,
            ProxyCredentialsEntity(username, password),
            ProxyCredentialsEntity.serializer()
        )
    }

    override fun getProxyCredentials(): ProxyCredentialsEntity? =
        kaliumPreferences.getSerializable(PROXY_CREDENTIALS, ProxyCredentialsEntity.serializer())

    private companion object {
        const val PROXY_CREDENTIALS = "proxy_credentials"
    }
}
