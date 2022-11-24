package com.wire.kalium.logic.data.message.reaction

import com.wire.kalium.logic.data.id.QualifiedID
import com.wire.kalium.logic.data.user.ConnectionState
import com.wire.kalium.logic.data.user.UserAvailabilityStatus
import com.wire.kalium.logic.data.user.type.UserType

data class MessageReaction(
    val emoji: String,
    val userId: QualifiedID,
    val name: String?,
    val handle: String?,
    val isSelfUser: Boolean,
    val previewAssetId: QualifiedID?,
    val userType: UserType,
    val deleted: Boolean,
    val connectionStatus: ConnectionState,
    val userAvailabilityStatus: UserAvailabilityStatus
)
