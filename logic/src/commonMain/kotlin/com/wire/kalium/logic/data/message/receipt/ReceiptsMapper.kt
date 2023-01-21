package com.wire.kalium.logic.data.message.receipt

import com.wire.kalium.logic.data.id.IdMapper
import com.wire.kalium.logic.data.id.toModel
import com.wire.kalium.logic.data.message.UserSummary
import com.wire.kalium.logic.data.user.AvailabilityStatusMapper
import com.wire.kalium.logic.data.user.ConnectionStateMapper
import com.wire.kalium.logic.data.user.type.DomainUserTypeMapper
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.persistence.dao.receipt.DetailedReceiptEntity
import com.wire.kalium.persistence.dao.receipt.ReceiptTypeEntity

interface ReceiptsMapper {
    fun toTypeEntity(type: ReceiptType): ReceiptTypeEntity
    fun fromTypeEntity(type: ReceiptTypeEntity): ReceiptType
    fun fromEntityToModel(detailedReceiptEntity: DetailedReceiptEntity): DetailedReceipt
}

internal class ReceiptsMapperImpl(
    private val idMapper: IdMapper = MapperProvider.idMapper(),
    private val availabilityStatusMapper: AvailabilityStatusMapper = MapperProvider.availabilityStatusMapper(),
    private val connectionStateMapper: ConnectionStateMapper = MapperProvider.connectionStateMapper(),
    private val domainUserTypeMapper: DomainUserTypeMapper
) : ReceiptsMapper {

    override fun toTypeEntity(type: ReceiptType): ReceiptTypeEntity = when (type) {
        ReceiptType.READ -> ReceiptTypeEntity.READ
        ReceiptType.DELIVERED -> ReceiptTypeEntity.DELIVERY
    }

    override fun fromTypeEntity(type: ReceiptTypeEntity): ReceiptType = when (type) {
        ReceiptTypeEntity.READ -> ReceiptType.READ
        ReceiptTypeEntity.DELIVERY -> ReceiptType.DELIVERED
    }

    override fun fromEntityToModel(detailedReceiptEntity: DetailedReceiptEntity): DetailedReceipt =
        with(detailedReceiptEntity) {
            val messageUserId = userId.toModel()
            DetailedReceipt(
                type = fromTypeEntity(type),
                date = date,
                userSummary = UserSummary(
                    userId = messageUserId,
                    userName = userName,
                    userHandle = userHandle,
                    userPreviewAssetId = userPreviewAssetId?.toModel(),
                    userType = domainUserTypeMapper.fromUserTypeEntity(userType),
                    isUserDeleted = isUserDeleted,
                    connectionStatus = connectionStateMapper.fromDaoConnectionStateToUser(connectionStatus),
                    availabilityStatus = availabilityStatusMapper.fromDaoAvailabilityStatusToModel(availabilityStatus)
                )
            )
        }
}
