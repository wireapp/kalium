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

package com.wire.kalium.network.api.unbound.configuration

import kotlin.native.ObjCName

@ObjCName("ServerConfig")
public data class ServerConfigDTO(
    val id: String,
    val links: Links,
    @ObjCName("metadata")
    val metaData: MetaData
) {
    @ObjCName("ServerConfigLinks")
    public data class Links(
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

    @ObjCName("ServerConfigMetadata")
    public data class MetaData(
        val federation: Boolean,
        val commonApiVersion: ApiVersionDTO,
        val domain: String?
    )

    @ObjCName("ServerConfigApiProxy")
    public data class ApiProxy(
        val needsAuthentication: Boolean,
        val host: String,
        val port: Int
    )
}

public fun isProxyRequired(serverConfigDTOApiProxy: ServerConfigDTO.ApiProxy?): Boolean {
    return serverConfigDTOApiProxy != null
}

@ObjCName("ApiVersion")
public sealed class ApiVersionDTO(open val version: Int) {

    @ObjCName("ApiVersionInvalid")
    public sealed class Invalid(override val version: Int) : ApiVersionDTO(version) {
        @ObjCName("ApiVersionNew")
        public data object New : Invalid(NEW_API_VERSION_NUMBER)
        @ObjCName("ApiVersionUnknown")
        public data object Unknown : Invalid(UNKNOWN_API_VERSION_NUMBER)
    }

    @ObjCName("ApiVersionValid")
    public data class Valid(override val version: Int) : ApiVersionDTO(version)

    public companion object {
        @ObjCName("create")
        public fun fromInt(value: Int): ApiVersionDTO {
            return if (value >= MINIMUM_VALID_API_VERSION) Valid(value)
            else if (value == NEW_API_VERSION_NUMBER) Invalid.New
            else Invalid.Unknown
        }

        public const val NEW_API_VERSION_NUMBER: Int = -1
        public const val UNKNOWN_API_VERSION_NUMBER: Int = -2
        public const val MINIMUM_VALID_API_VERSION: Int = 0
    }
}
