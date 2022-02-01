package com.wire.kalium.logic.data.backend_config

import com.wire.kalium.logic.configuration.BuildType
import com.wire.kalium.network.tools.BackendConfig


class BackendTypeMapper {
    private val staging: BackendConfig
        get() = BackendConfig(
            """"https://staging-nginz-https.zinfra.io"""",
            """"https://wire-account-staging.zinfra.io"""",
            """"https://staging-nginz-ssl.zinfra.io"""
        )

    private val production: BackendConfig
        get() =
            BackendConfig(
                """"https://prod-nginz-https.wire.com"""",
                """"https://account.wire.com"""",
                """"https://prod-nginz-ssl.wire.com""""
            )

    fun toBackendConfig(buildType: BuildType): BackendConfig = when (buildType) {
        BuildType.Release -> production
        BuildType.Debug -> staging
        is BuildType.Custom -> with(buildType.networkConfig) {
            BackendConfig(apiBaseUrl = apiBaseUrl, accountsUrl = accountsUrl, webSocketUrl = webSocketUrl)
        }
    }
}
