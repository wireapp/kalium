package com.wire.kalium.logic.network_config

import com.wire.kalium.network.tools.BackendConfig

sealed class BackendType {
    object Production : BackendType()
    object Staging : BackendType()
    data class Custom(val networkConfig: NetworkConfig) : BackendType()
}

class BackEndTypeMapper {
    private val staging: BackendConfig by lazy {
        BackendConfig(
            """"https://staging-nginz-https.zinfra.io"""",
            """"https://wire-account-staging.zinfra.io"""",
            """"https://staging-nginz-ssl.zinfra.io/await?client=""""
        )
    }

    private val production: BackendConfig by lazy {
        BackendConfig(
            """"https://prod-nginz-https.wire.com"""",
            """"https://account.wire.com"""",
            """"https://prod-nginz-ssl.wire.com/await?client=""""
        )
    }

    fun toBackendConfig(backEndType: BackendType): BackendConfig = when (backEndType) {
        BackendType.Production -> production
        BackendType.Staging -> staging
        is BackendType.Custom -> with(backEndType.networkConfig) {
            BackendConfig(apiBaseUrl = apiBaseUrl, accountsUrl = accountsUrl, webSocketUrl = webSocketUrl)
        }
    }
}


data class NetworkConfig(
    val apiBaseUrl: String,
    val accountsUrl: String,
    val webSocketUrl: String
)
