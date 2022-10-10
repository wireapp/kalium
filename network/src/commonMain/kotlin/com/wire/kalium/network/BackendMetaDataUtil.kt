package com.wire.kalium.network

import com.wire.kalium.network.api.base.unbound.versioning.VersionInfoDTO
import com.wire.kalium.network.tools.ApiVersionDTO
import com.wire.kalium.network.tools.ServerConfigDTO

val SupportedApiVersions = setOf(0, 1)
val DevelopmentApiVersions = setOf(2)

interface BackendMetaDataUtil {
    fun calculateApiVersion(
        versionInfoDTO: VersionInfoDTO,
        supportedApiVersions: Set<Int> = SupportedApiVersions,
        developmentApiVersions: Set<Int> = DevelopmentApiVersions,
        developmentApiEnabled: Boolean
    ): ServerConfigDTO.MetaData

}

object BackendMetaDataUtilImpl : BackendMetaDataUtil {
    override fun calculateApiVersion(
        versionInfoDTO: VersionInfoDTO,
        supportedApiVersions: Set<Int>,
        developmentApiVersions: Set<Int>,
        developmentApiEnabled: Boolean
    ): ServerConfigDTO.MetaData {

        val allSupportedApiVersions = if (developmentApiEnabled) supportedApiVersions + developmentApiVersions else supportedApiVersions
        val apiVersion = commonApiVersion(versionInfoDTO, allSupportedApiVersions, developmentApiEnabled)?.let { maxCommonVersion ->
            ApiVersionDTO.Valid(maxCommonVersion)
        } ?: run {
            handleNoCommonVersion(versionInfoDTO.supported, allSupportedApiVersions)
        }

        return ServerConfigDTO.MetaData(
            versionInfoDTO.federation,
            apiVersion,
            versionInfoDTO.domain
        )
    }

    private fun commonApiVersion(serverVersion: VersionInfoDTO, supportedApiVersions: Set<Int>, developmentAPIEnabled: Boolean): Int? {
        val serverSupportedApiVersions = if (developmentAPIEnabled && serverVersion.developmentSupported != null) {
            serverVersion.supported + serverVersion.developmentSupported
        } else {
            serverVersion.supported
        }
        return serverSupportedApiVersions.intersect(supportedApiVersions).maxOrNull()
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
