/*
 * Wire
 * Copyright (C) 2023 Wire Swiss GmbH
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

package com.wire.kalium.logic.util

import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

internal fun Boolean.toInt() = if (this) 1 else 0

fun String.fileExtension(): String? {
    val splitElements = split(".")
    val extension: String = when {
        splitElements.size <= 1 -> this
        splitElements.size == 2 -> splitElements[1]
        else -> splitElements.subList(1, splitElements.size).joinToString(".")
    }
    return extension.ifBlank { null }
}

@OptIn(ExperimentalContracts::class)
fun Int?.isGreaterThan(other: Int?): Boolean {
    contract {
        returns(true) implies (this@isGreaterThan != null)
        returns(true) implies (other != null)
    }
    return this is Int && other is Int && this > other
}
