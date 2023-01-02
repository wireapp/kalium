package com.wire.kalium.logic.data.id

import com.wire.kalium.network.api.base.model.UserAssetDTO
import com.wire.kalium.persistence.dao.QualifiedIDEntity

internal typealias NetworkQualifiedId = com.wire.kalium.network.api.base.model.QualifiedID
internal typealias PersistenceQualifiedId = QualifiedIDEntity

// QualifiedID
internal fun QualifiedID.toApi(): NetworkQualifiedId = NetworkQualifiedId(value, domain)
internal fun QualifiedID.toDao(): PersistenceQualifiedId = PersistenceQualifiedId(value, domain)

internal fun QualifiedIDEntity.toModel(): QualifiedID = QualifiedID(value, domain)
internal fun QualifiedIDEntity.toApi(): NetworkQualifiedId = NetworkQualifiedId(value, domain)

internal fun NetworkQualifiedId.toModel(): QualifiedID = QualifiedID(value, domain)
internal fun NetworkQualifiedId.toDao(): PersistenceQualifiedId = PersistenceQualifiedId(value, domain)

internal fun UserAssetDTO.toDao(domain: String): QualifiedIDEntity = PersistenceQualifiedId(key, domain)
internal fun UserAssetDTO.toModel(domain: String): QualifiedID = QualifiedID(key, domain)
