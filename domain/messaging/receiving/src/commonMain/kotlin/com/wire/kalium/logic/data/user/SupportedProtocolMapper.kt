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

package com.wire.kalium.logic.data.user

import com.wire.kalium.network.api.model.SupportedProtocolDTO
import com.wire.kalium.persistence.dao.SupportedProtocolEntity

fun SupportedProtocol.toApi() = when (this) {
    SupportedProtocol.MLS -> SupportedProtocolDTO.MLS
    SupportedProtocol.PROTEUS -> SupportedProtocolDTO.PROTEUS
}

fun SupportedProtocol.toDao() = when (this) {
    SupportedProtocol.MLS -> SupportedProtocolEntity.MLS
    SupportedProtocol.PROTEUS -> SupportedProtocolEntity.PROTEUS
}

fun SupportedProtocolDTO.toModel() = when (this) {
    SupportedProtocolDTO.MLS -> SupportedProtocol.MLS
    SupportedProtocolDTO.PROTEUS -> SupportedProtocol.PROTEUS
}

fun SupportedProtocolDTO.toDao() = when (this) {
    SupportedProtocolDTO.MLS -> SupportedProtocolEntity.MLS
    SupportedProtocolDTO.PROTEUS -> SupportedProtocolEntity.PROTEUS
}

fun SupportedProtocolEntity.toModel() = when (this) {
    SupportedProtocolEntity.MLS -> SupportedProtocol.MLS
    SupportedProtocolEntity.PROTEUS -> SupportedProtocol.PROTEUS
}

fun List<SupportedProtocolDTO>.toDao() = this.map { it.toDao() }.toSet()
fun List<SupportedProtocolDTO>.toModel() = this.map { it.toModel() }.toSet()
fun Set<SupportedProtocol>.toDao() = this.map { it.toDao() }.toSet()
fun Set<SupportedProtocolEntity>.toModel() = this.map { it.toModel() }.toSet()
