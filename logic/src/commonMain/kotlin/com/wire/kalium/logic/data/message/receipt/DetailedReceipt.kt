package com.wire.kalium.logic.data.message.receipt

import com.wire.kalium.logic.data.id.QualifiedID
import com.wire.kalium.logic.data.user.ConnectionState
import com.wire.kalium.logic.data.user.UserAvailabilityStatus
import com.wire.kalium.logic.data.user.type.UserType
import kotlinx.datetime.Instant

data class DetailedReceipt(
    val type: ReceiptType,
    val date: Instant,
    val userId: QualifiedID,
    val userName: String?,
    val userHandle: String?,
    val userPreviewAssetId: QualifiedID?,
    val userType: UserType,
    val isUserDeleted: Boolean,
    val connectionStatus: ConnectionState,
    val availabilityStatus: UserAvailabilityStatus
)

enum class ReceiptType {
    DELIVERY, READ
}
