/*
 * Wire
 * Copyright (C) 2024 Wire Swiss GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see http://www.gnu.org/licenses/.
 */

package com.wire.kalium.logic.data.message.receipt

import com.wire.kalium.logic.data.id.toModel
import com.wire.kalium.logic.data.message.UserSummary
import com.wire.kalium.logic.data.user.AvailabilityStatusMapper
import com.wire.kalium.logic.data.user.ConnectionStateMapper
import com.wire.kalium.logic.data.user.type.DomainUserTypeMapper
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.persistence.dao.message.MessageEntity
import com.wire.kalium.persistence.dao.receipt.DetailedReceiptEntity
import com.wire.kalium.persistence.dao.receipt.ReceiptTypeEntity

interface ReceiptsMapper {
    fun toTypeEntity(type: ReceiptType): ReceiptTypeEntity
    fun fromTypeEntity(type: ReceiptTypeEntity): ReceiptType
    fun fromEntityToModel(detailedReceiptEntity: DetailedReceiptEntity): DetailedReceipt
    fun fromTypeToMessageStatus(type: ReceiptType): MessageEntity.Status
}

internal class ReceiptsMapperImpl(
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
                    availabilityStatus = availabilityStatusMapper.fromDaoAvailabilityStatusToModel(availabilityStatus),
                    accentId = accentId
                )
            )
        }

    override fun fromTypeToMessageStatus(type: ReceiptType): MessageEntity.Status = when (type) {
        ReceiptType.READ -> MessageEntity.Status.READ
        ReceiptType.DELIVERED -> MessageEntity.Status.DELIVERED
    }
}
