package com.wire.kalium.persistence.network_config

import com.wire.kalium.persistence.kmm_settings.KaliumPreferences
import kotlinx.serialization.Serializable

interface NetworkConfigDAO {
    fun setNetworkConfig(networkConfig: NetworkConfig)
    fun getNetworkConfig(): NetworkConfig?
    fun configExist(): Boolean
}

class NetworkConfigDAOImpl(private val kaliumPreferences: KaliumPreferences) : NetworkConfigDAO {

    override fun setNetworkConfig(networkConfig: NetworkConfig) = kaliumPreferences.putSerializable(NETWORK_CONFIG_KEY, networkConfig, NetworkConfig.serializer())

    override fun getNetworkConfig(): NetworkConfig? = kaliumPreferences.getSerializable(NETWORK_CONFIG_KEY, NetworkConfig.serializer())

    override fun configExist(): Boolean = kaliumPreferences.hasValue(NETWORK_CONFIG_KEY)

    private companion object {
        const val NETWORK_CONFIG_KEY = "network_config"
    }
}

@Serializable
data class NetworkConfig(
    val apiBaseUrl: String,
    val accountBaseUrl: String,
    val webSocketBaseUrl: String
)
