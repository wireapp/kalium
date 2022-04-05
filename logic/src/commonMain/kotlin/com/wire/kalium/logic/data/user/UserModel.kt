package com.wire.kalium.logic.data.user

import com.wire.kalium.logic.data.id.QualifiedID
import com.wire.kalium.logic.data.id.VALUE_DOMAIN_SEPARATOR

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

fun String.toUserId(): UserId {
    if (contains(VALUE_DOMAIN_SEPARATOR)) {
        split(VALUE_DOMAIN_SEPARATOR).also {
            return UserId(value = it.first(), domain = it.last())
        }
    } else return UserId(value = this, domain = "")
}
