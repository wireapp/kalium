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

package com.wire.kalium.logger

private const val START_INDEX = 0
private const val END_INDEX_ID = 7
private const val END_INDEX_DOMAIN = 3
private const val END_INDEX_URL_PATH = 3

fun String.obfuscateId(): String {
    return if (this.length >= END_INDEX_ID) {
        this.substring(START_INDEX, END_INDEX_ID) + "***"
    } else {
        this
    }
}

fun String.obfuscateDomain(): String {
    return if (this.length >= END_INDEX_DOMAIN) {
        this.substring(START_INDEX, END_INDEX_DOMAIN) + "***"
    } else {
        this
    }
}

fun String.obfuscateUrlPath(): String {
    return if (this.length >= END_INDEX_URL_PATH) {
        this.substring(START_INDEX, END_INDEX_URL_PATH) + "***"
    } else {
        this
    }
}
