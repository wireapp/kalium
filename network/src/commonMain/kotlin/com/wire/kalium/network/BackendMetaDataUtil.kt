package com.wire.kalium.network

import com.wire.kalium.network.api.base.unbound.versioning.VersionInfoDTO
import com.wire.kalium.network.tools.ApiVersionDTO
import com.wire.kalium.network.tools.ServerConfigDTO

val SupportedApiVersions = setOf(0, 1, 2)

interface BackendMetaDataUtil {
    fun calculateApiVersion(
        versionInfoDTO: VersionInfoDTO,
        appVersion: Set<Int> = SupportedApiVersions,
        developmentApiEnabled: Boolean
    ): ServerConfigDTO.MetaData

}

object BackendMetaDataUtilImpl : BackendMetaDataUtil {
    override fun calculateApiVersion(
        versionInfoDTO: VersionInfoDTO,
        appVersion: Set<Int>,
        developmentApiEnabled: Boolean
    ): ServerConfigDTO.MetaData {
        val apiVersion = commonApiVersion(versionInfoDTO, appVersion, developmentApiEnabled)?.let { maxCommonVersion ->
            ApiVersionDTO.Valid(maxCommonVersion)
        } ?: run {
            handleNoCommonVersion(versionInfoDTO.supported, appVersion)
        }

        return ServerConfigDTO.MetaData(
            versionInfoDTO.federation,
            apiVersion,
            versionInfoDTO.domain
        )
    }

    private fun commonApiVersion(serverVersion: VersionInfoDTO, appVersion: Set<Int>, developmentAPIEnabled: Boolean): Int? {
        val supported = if (developmentAPIEnabled && serverVersion.developmentSupported != null) {
            serverVersion.supported + serverVersion.developmentSupported
        } else {
            serverVersion.supported
        }
        return supported.intersect(appVersion).maxOrNull()
    }

    private fun handleNoCommonVersion(serverVersion: List<Int>, appVersion: Set<Int>): ApiVersionDTO.Invalid {
        return serverVersion.maxOrNull()?.let { maxBEVersion ->
            appVersion.maxOrNull()?.let { maxAppVersion ->
                if (maxBEVersion > maxAppVersion) ApiVersionDTO.Invalid.New
                else ApiVersionDTO.Invalid.Unknown
            }
        } ?: run {
            kaliumLogger.w("empty app/server config list")
            ApiVersionDTO.Invalid.Unknown
        }
    }
}
