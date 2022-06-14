package com.wire.kalium.logic.data.call

import com.wire.kalium.logic.data.id.QualifiedID
import com.wire.kalium.logic.data.user.UserAssetId

data class Participant(
    val id: QualifiedID,
    val clientId: String,
    val name: String = "",
    val isMuted: Boolean,
    val isSpeaking: Boolean = false,
    val avatarAssetId: UserAssetId? = null
)
