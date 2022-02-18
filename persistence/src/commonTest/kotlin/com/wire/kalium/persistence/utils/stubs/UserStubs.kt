package com.wire.kalium.persistence.utils.stubs

import com.wire.kalium.persistence.dao.QualifiedID
import com.wire.kalium.persistence.dao.User

fun newUserEntity(id: String = "test") =
    User(
        id = QualifiedID(id, "wire.com"),
        name = "user$id",
        handle = "handle$id",
        email = "email$id",
        phone = "phone$id",
        accentId = 1,
        team = "team"
    )

fun newUserEntity(qualifiedID: QualifiedID, id: String = "test") =
    User(
        id = qualifiedID,
        name = "user$id",
        handle = "handle$id",
        email = "email$id",
        phone = "phone$id",
        accentId = 1,
        team = "team"
    )
