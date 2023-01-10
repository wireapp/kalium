package com.wire.kalium.persistence.dao.receipt

import com.wire.kalium.persistence.dao.ConnectionEntity
import com.wire.kalium.persistence.dao.QualifiedIDEntity
import com.wire.kalium.persistence.dao.UserAvailabilityStatusEntity
import com.wire.kalium.persistence.dao.UserTypeEntity
import kotlinx.datetime.Instant

internal object ReceiptMapper {

    @Suppress("LongParameterList", "UNUSED_PARAMETER")
    fun fromDetailedReceiptView(
        type: ReceiptTypeEntity,
        date: String,
        messageId: String,
        conversationId: QualifiedIDEntity,
        userId: QualifiedIDEntity,
        userName: String?,
        userHandle: String?,
        previewAssetId: QualifiedIDEntity?,
        userType: UserTypeEntity,
        isUserDeleted: Boolean,
        connectionStatus: ConnectionEntity.State,
        userAvailabilityStatus: UserAvailabilityStatusEntity,
    ) = DetailedReceiptEntity(
        type = type,
        date = Instant.parse(date),
        userId = userId,
        userName = userName,
        userHandle = userHandle,
        userPreviewAssetId = previewAssetId,
        userType = userType,
        isUserDeleted = isUserDeleted,
        connectionStatus = connectionStatus,
        availabilityStatus = userAvailabilityStatus,
    )
}
