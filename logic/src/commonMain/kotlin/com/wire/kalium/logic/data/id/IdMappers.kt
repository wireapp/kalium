package com.wire.kalium.logic.data.id

import com.wire.kalium.network.api.base.model.UserAssetDTO
import com.wire.kalium.persistence.dao.QualifiedIDEntity

internal typealias NetworkQualifiedId = com.wire.kalium.network.api.base.model.QualifiedID
internal typealias PersistenceQualifiedId = QualifiedIDEntity

// QualifiedID
fun QualifiedID.toApi(): NetworkQualifiedId = NetworkQualifiedId(value, domain)
fun QualifiedID.toDao(): PersistenceQualifiedId = PersistenceQualifiedId(value, domain)

fun QualifiedIDEntity.toModel(): QualifiedID = QualifiedID(value, domain)
fun QualifiedIDEntity.toApi(): NetworkQualifiedId = NetworkQualifiedId(value, domain)

fun NetworkQualifiedId.toModel(): QualifiedID = QualifiedID(value, domain)
fun NetworkQualifiedId.toDao(): PersistenceQualifiedId = PersistenceQualifiedId(value, domain)

fun UserAssetDTO.toDao(domain: String): QualifiedIDEntity = PersistenceQualifiedId(key, domain)
fun UserAssetDTO.toModel(domain: String): QualifiedID = QualifiedID(key, domain)
