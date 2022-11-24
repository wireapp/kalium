package com.wire.kalium.persistence.dao.receipt

import com.wire.kalium.persistence.dao.ConnectionEntity
import com.wire.kalium.persistence.dao.QualifiedIDEntity
import com.wire.kalium.persistence.dao.UserAvailabilityStatusEntity
import com.wire.kalium.persistence.dao.UserTypeEntity
import kotlinx.datetime.Instant

data class DetailedReceiptEntity(
    val type: ReceiptTypeEntity,
    val date: Instant,
    val userId: QualifiedIDEntity,
    val userName: String?,
    val userHandle: String?,
    val userPreviewAssetId: QualifiedIDEntity?,
    val userType: UserTypeEntity,
    val isUserDeleted: Boolean,
    val connectionStatus: ConnectionEntity.State,
    val availabilityStatus: UserAvailabilityStatusEntity
)
