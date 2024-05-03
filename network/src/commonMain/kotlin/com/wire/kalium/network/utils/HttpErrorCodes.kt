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

package com.wire.kalium.network.utils

import io.ktor.http.HttpStatusCode

private const val NOT_FOUND_ERROR_CODE = 404

enum class HttpErrorCodes(val code: Int) {
    NOT_FOUND(NOT_FOUND_ERROR_CODE)
}

/**
 * Custom [HttpStatusCode] to handle when one or more federated remote servers are unreachable.
 */
@Suppress("MagicNumber")
internal val HttpStatusCode.Companion.UnreachableRemoteBackends: HttpStatusCode
    get() = HttpStatusCode(533, "Unreachable remote backends")
