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

@file:Suppress("TooManyFunctions")
package com.wire.kalium.logic.data.id

import com.wire.kalium.cryptography.CryptoClientId
import com.wire.kalium.cryptography.CryptoQualifiedClientId
import com.wire.kalium.cryptography.CryptoQualifiedID
import com.wire.kalium.cryptography.MLSGroupId
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.network.api.model.UserAssetDTO
import com.wire.kalium.persistence.dao.QualifiedIDEntity
import com.wire.kalium.util.InternalKaliumApi
import com.wire.kalium.network.api.model.SubconversationId as NetworkSubConversationId

@InternalKaliumApi
public typealias NetworkQualifiedId = com.wire.kalium.network.api.model.QualifiedID

@InternalKaliumApi
public typealias PersistenceQualifiedId = QualifiedIDEntity

// QualifiedID
@InternalKaliumApi
public fun QualifiedID.toApi(): NetworkQualifiedId = NetworkQualifiedId(value, domain)

@InternalKaliumApi
public fun QualifiedID.toDao(): PersistenceQualifiedId = PersistenceQualifiedId(value, domain)

@InternalKaliumApi
public fun QualifiedID.toCrypto(): CryptoQualifiedID = CryptoQualifiedID(value, domain)

@InternalKaliumApi
public fun QualifiedIDEntity.toModel(): QualifiedID = QualifiedID(value, domain)

@InternalKaliumApi
public fun QualifiedIDEntity.toApi(): NetworkQualifiedId = NetworkQualifiedId(value, domain)

@InternalKaliumApi
public fun NetworkQualifiedId.toModel(): QualifiedID = QualifiedID(value, domain)

@InternalKaliumApi
public fun NetworkQualifiedId.toDao(): PersistenceQualifiedId = PersistenceQualifiedId(value, domain)

@InternalKaliumApi
public fun CryptoQualifiedID.toModel(): QualifiedID = QualifiedID(value, domain)

@InternalKaliumApi
public fun CryptoClientId.toModel(): ClientId = ClientId(value)

@InternalKaliumApi
public fun UserAssetDTO.toDao(domain: String): QualifiedIDEntity = PersistenceQualifiedId(key, domain)

@InternalKaliumApi
public fun UserAssetDTO.toModel(domain: String): QualifiedID = QualifiedID(key, domain)

@InternalKaliumApi
public fun SubconversationId.toApi(): String = value

@InternalKaliumApi
public fun GroupID.toCrypto(): MLSGroupId = value

@InternalKaliumApi
public fun CryptoQualifiedClientId.toModel(): QualifiedClientID = QualifiedClientID(ClientId(value), userId.toModel())

@InternalKaliumApi
public fun NetworkSubConversationId.toModel(): SubconversationId = SubconversationId(this)
