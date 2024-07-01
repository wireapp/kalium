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

internal typealias NetworkQualifiedId = com.wire.kalium.network.api.model.QualifiedID
internal typealias PersistenceQualifiedId = QualifiedIDEntity

// QualifiedID
internal fun QualifiedID.toApi(): NetworkQualifiedId = NetworkQualifiedId(value, domain)
internal fun QualifiedID.toDao(): PersistenceQualifiedId = PersistenceQualifiedId(value, domain)
internal fun QualifiedID.toCrypto(): CryptoQualifiedID = CryptoQualifiedID(value, domain)

internal fun QualifiedIDEntity.toModel(): QualifiedID = QualifiedID(value, domain)
internal fun QualifiedIDEntity.toApi(): NetworkQualifiedId = NetworkQualifiedId(value, domain)

internal fun NetworkQualifiedId.toModel(): QualifiedID = QualifiedID(value, domain)
internal fun NetworkQualifiedId.toDao(): PersistenceQualifiedId = PersistenceQualifiedId(value, domain)

internal fun CryptoQualifiedID.toModel() = QualifiedID(value, domain)

internal fun CryptoClientId.toModel() = ClientId(value)

internal fun UserAssetDTO.toDao(domain: String): QualifiedIDEntity = PersistenceQualifiedId(key, domain)
internal fun UserAssetDTO.toModel(domain: String): QualifiedID = QualifiedID(key, domain)

internal fun SubconversationId.toApi(): String = value

internal fun GroupID.toCrypto(): MLSGroupId = value

internal fun CryptoQualifiedClientId.toModel() = QualifiedClientID(ClientId(value), userId.toModel())
