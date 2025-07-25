/*
 * Wire
 * Copyright (C) 2025 Wire Swiss GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see http://www.gnu.org/licenses/.
 */
package com.wire.kalium.network

import com.wire.kalium.network.api.unbound.configuration.ApiVersionDTO
import com.wire.kalium.network.api.unbound.configuration.ServerConfigDTO
import com.wire.kalium.network.api.unbound.versioning.VersionInfoDTO
import io.mockative.Mockable

// They are not truly constants as set is not a primitive type, yet are treated as one in this context
@Suppress("MagicNumber")
val SupportedApiVersions: Set<Int> = setOf(0, 1, 2, 4, 5, 6, 7, 8, 9)

// They are not truly constants as set is not a primitive type, yet are treated as one in this context
@Suppress("MagicNumber")
val DevelopmentApiVersions: Set<Int> = setOf(10)

// You can use scripts/generate_new_api_version.sh or gradle task network:generateNewApiVersion to
// bump API version and generate all needed classes

@Mockable
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

        val allSupportedApiVersions =
            if (developmentApiEnabled) supportedApiVersions + developmentApiVersions else supportedApiVersions
        val apiVersion = commonApiVersion(
            versionInfoDTO,
            allSupportedApiVersions,
            developmentApiEnabled
        )?.let { maxCommonVersion ->
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

    private fun commonApiVersion(
        serverVersion: VersionInfoDTO,
        supportedApiVersions: Set<Int>,
        developmentAPIEnabled: Boolean
    ): Int? {
        val serverSupportedApiVersions: List<Int> =
            if (developmentAPIEnabled && serverVersion.developmentSupported != null) {
                serverVersion.supported + serverVersion.developmentSupported!!
            } else {
                serverVersion.supported
            }
        return serverSupportedApiVersions.intersect(supportedApiVersions).maxOrNull()
    }

    private fun handleNoCommonVersion(
        serverVersion: List<Int>,
        appVersion: Set<Int>
    ): ApiVersionDTO.Invalid {
        return serverVersion.maxOrNull()?.let { maxBEVersion ->
            appVersion.maxOrNull()?.let { maxAppVersion ->
                if (maxBEVersion > maxAppVersion) ApiVersionDTO.Invalid.New
                else ApiVersionDTO.Invalid.Unknown
            }
        } ?: run {
            kaliumLogger.w("empty app/server config list")
            kaliumLogger.w("remove me")
            ApiVersionDTO.Invalid.Unknown
        }
    }
}
