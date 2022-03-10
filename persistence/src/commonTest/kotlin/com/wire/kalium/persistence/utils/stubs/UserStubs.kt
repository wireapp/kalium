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
        team = "team",
        null, null
    )

fun newUserEntity(qualifiedID: QualifiedID, id: String = "test") =
    UserEntity(
        id = qualifiedID,
        name = "user$id",
        handle = "handle$id",
        email = "email$id",
        phone = "phone$id",
        accentId = 1,
        team = "team",
        null, null
    )

fun newUserEntity(
    id: String = "test",
    name: String = "testName",
    handle: String = "testHandle",
    email: String = "testEmail@wire.com",
    phone: String = "testPhone",
    accentId: Int = 1,
    team: String = "testTeam",
    previewAssetId: String = "previewAssetId",
    completeAssetId: String = "completeAssetId",
): UserEntity {
    return UserEntity(
        id = QualifiedID(id, "wire.com"),
        name = name,
        handle = handle,
        email = email,
        phone = phone,
        accentId = accentId,
        team = team,
        previewAssetId = previewAssetId,
        completeAssetId = completeAssetId
    )
}
