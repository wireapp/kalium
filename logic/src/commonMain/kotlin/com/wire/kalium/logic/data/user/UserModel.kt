package com.wire.kalium.logic.data.user

import com.wire.kalium.logic.data.id.QualifiedID

typealias UserId = QualifiedID

abstract class User {
    abstract val id: UserId
}

data class SelfUser(
    override val id: UserId,
    val name: String?,
    val handle: String?,
    val email: String?,
    val phone: String?,
    val accentId: Int,
    val team: String?,
    val previewPicture: UserAssetId?,
    val completePicture: UserAssetId?
) : User()

typealias UserAssetId = String
