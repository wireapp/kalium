package com.wire.kalium.network

import com.wire.kalium.network.api.versioning.VersionInfoDTO
import com.wire.kalium.network.tools.ApiVersionDTO
import com.wire.kalium.network.tools.WireServerDTO

val SupportedApiVersions = setOf(0)

interface BackendMetaDataUtil {
    fun calculateApiVersion(versionInfoDTO: VersionInfoDTO, appVersion: Set<Int> = SupportedApiVersions): WireServerDTO.MetaData

}

object BackendMetaDataUtilImpl : BackendMetaDataUtil {
    override fun calculateApiVersion(versionInfoDTO: VersionInfoDTO, appVersion: Set<Int>): WireServerDTO.MetaData {
        val apiVersion = commonApiVersion(versionInfoDTO.supported, appVersion)?.let { maxCommonVersion ->
            ApiVersionDTO.Valid(maxCommonVersion)
        } ?: run {
            handleNoCommonVersion(versionInfoDTO.supported, appVersion)
        }

        return WireServerDTO.MetaData(
            versionInfoDTO.federation,
            apiVersion,
            versionInfoDTO.domain
        )
    }

    private fun commonApiVersion(serverVersion: List<Int>, appVersion: Set<Int>): Int? = serverVersion.intersect(appVersion).maxOrNull()

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
