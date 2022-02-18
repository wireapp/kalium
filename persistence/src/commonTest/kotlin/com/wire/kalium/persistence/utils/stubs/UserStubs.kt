package com.wire.kalium.persistence.utils.stubs

import com.wire.kalium.persistence.dao.QualifiedID
import com.wire.kalium.persistence.dao.UserEntity

fun newUserEntity(id: String = "test") =
    UserEntity(
        id = QualifiedID(id, "wire.com"),
        name = "user$id",
        handle = "handle$id",
        email = "email$id",
        phone = "phone$id",
        accentId = 1,
        team = "team"
    )

fun newUserEntity(qualifiedID: QualifiedID, id: String = "test") =
    UserEntity(
        id = qualifiedID,
        name = "user$id",
        handle = "handle$id",
        email = "email$id",
        phone = "phone$id",
        accentId = 1,
        team = "team"
    )
