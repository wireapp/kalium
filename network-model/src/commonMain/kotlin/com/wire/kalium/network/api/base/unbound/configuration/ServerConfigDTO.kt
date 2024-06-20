/*
 * Wire
 * Copyright (C) 2024 Wire Swiss GmbH
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

package com.wire.kalium.network.api.base.unbound.configuration

data class ServerConfigDTO(
    val id: String,
    val links: Links,
    val metaData: MetaData
) {
    data class Links(
        val api: String,
        val accounts: String,
        val webSocket: String,
        val blackList: String,
        val teams: String,
        val website: String,
        val title: String,
        val isOnPremises: Boolean,
        val apiProxy: ApiProxy?
    )

    data class MetaData(
        val federation: Boolean,
        val commonApiVersion: ApiVersionDTO,
        val domain: String?
    )

    data class ApiProxy(
        val needsAuthentication: Boolean,
        val host: String,
        val port: Int
    )
}

fun isProxyRequired(serverConfigDTOApiProxy: ServerConfigDTO.ApiProxy?): Boolean {
    return serverConfigDTOApiProxy != null
}

sealed class ApiVersionDTO(open val version: Int) {

    sealed class Invalid(override val version: Int) : ApiVersionDTO(version) {
        data object New : Invalid(NEW_API_VERSION_NUMBER)
        data object Unknown : Invalid(UNKNOWN_API_VERSION_NUMBER)
    }

    data class Valid(override val version: Int) : ApiVersionDTO(version)

    companion object {
        fun fromInt(value: Int): ApiVersionDTO {
            return if (value >= MINIMUM_VALID_API_VERSION) Valid(value)
            else if (value == NEW_API_VERSION_NUMBER) Invalid.New
            else Invalid.Unknown
        }

        const val NEW_API_VERSION_NUMBER = -1
        const val UNKNOWN_API_VERSION_NUMBER = -2
        const val MINIMUM_VALID_API_VERSION = 0
    }
}
