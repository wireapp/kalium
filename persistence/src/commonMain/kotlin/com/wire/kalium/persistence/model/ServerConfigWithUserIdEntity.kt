package com.wire.kalium.persistence.model

import com.wire.kalium.persistence.dao.QualifiedIDEntity

data class ServerConfigWithUserIdEntity(
    val serverConfig: ServerConfigEntity,
    val userId: QualifiedIDEntity
)
