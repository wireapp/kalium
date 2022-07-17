package com.wire.kalium.logic.data.call

import com.wire.kalium.logic.data.id.QualifiedID
import com.wire.kalium.logic.data.user.UserAssetId
import com.wire.kalium.logic.data.user.type.UserType

data class Participant(
    val id: QualifiedID,
    val clientId: String,
    val name: String = "",
    val isMuted: Boolean,
    val isSpeaking: Boolean = false,
    val avatarAssetId: UserAssetId? = null,
    val userType: UserType = UserType.NONE,
)
