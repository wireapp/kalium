/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
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
import com.wire.kalium.util.InternalKaliumApi

@InternalKaliumApi
public fun SupportedProtocol.toApi(): SupportedProtocolDTO = when (this) {
    SupportedProtocol.MLS -> SupportedProtocolDTO.MLS
    SupportedProtocol.PROTEUS -> SupportedProtocolDTO.PROTEUS
}

@InternalKaliumApi
public fun SupportedProtocol.toDao(): SupportedProtocolEntity = when (this) {
    SupportedProtocol.MLS -> SupportedProtocolEntity.MLS
    SupportedProtocol.PROTEUS -> SupportedProtocolEntity.PROTEUS
}

@InternalKaliumApi
public fun SupportedProtocolDTO.toModel(): SupportedProtocol = when (this) {
    SupportedProtocolDTO.MLS -> SupportedProtocol.MLS
    SupportedProtocolDTO.PROTEUS -> SupportedProtocol.PROTEUS
}

@InternalKaliumApi
public fun SupportedProtocolDTO.toDao(): SupportedProtocolEntity = when (this) {
    SupportedProtocolDTO.MLS -> SupportedProtocolEntity.MLS
    SupportedProtocolDTO.PROTEUS -> SupportedProtocolEntity.PROTEUS
}

@InternalKaliumApi
public fun SupportedProtocolEntity.toModel(): SupportedProtocol = when (this) {
    SupportedProtocolEntity.MLS -> SupportedProtocol.MLS
    SupportedProtocolEntity.PROTEUS -> SupportedProtocol.PROTEUS
}

@InternalKaliumApi
public fun List<SupportedProtocolDTO>.toDao(): Set<SupportedProtocolEntity> = map { it.toDao() }.toSet()

@InternalKaliumApi
public fun List<SupportedProtocolDTO>.toModel(): Set<SupportedProtocol> = map { it.toModel() }.toSet()

@InternalKaliumApi
public fun Set<SupportedProtocol>.toDao(): Set<SupportedProtocolEntity> = map { it.toDao() }.toSet()

@InternalKaliumApi
public fun Set<SupportedProtocolEntity>.toModel(): Set<SupportedProtocol> = map { it.toModel() }.toSet()
