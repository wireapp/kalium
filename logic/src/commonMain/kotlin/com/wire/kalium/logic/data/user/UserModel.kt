package com.wire.kalium.logic.data.user

import com.wire.kalium.logic.data.id.QualifiedID
import com.wire.kalium.network.api.user.self.ImageSize

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
    val picture: List<UserAsset>
) : User()

data class UserAsset(
    val key: String,
    val size: ImageSize,

//    val Id         = asText(_.id)('_id, "PRIMARY KEY")
//        val Token      = asTextOpt(_.token)('token)
//        val Domain     = asTextOpt(_.domain)('domain)
//        val Name       = text(_.name)('name)
//        val Encryption = asText(_.encryption)('encryption)
//        val Mime       = asText(_.mime)('mime)
//        val Sha        = asBlob(_.sha)('sha)
//        val Size       = long(_.size)('size)
//        val Source     = asTextOpt(_.localSource)('source)
//        val Preview    = asTextOpt(_.preview)('preview)
//        val Details    = asText(_.details)('details)
)
