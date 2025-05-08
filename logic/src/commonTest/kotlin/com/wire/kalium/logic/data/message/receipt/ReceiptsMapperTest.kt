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

import com.wire.kalium.logic.data.id.QualifiedID
import com.wire.kalium.logic.data.message.UserSummary
import com.wire.kalium.logic.data.user.AvailabilityStatusMapper
import com.wire.kalium.logic.data.user.ConnectionState
import com.wire.kalium.logic.data.user.ConnectionStateMapper
import com.wire.kalium.logic.data.user.UserAvailabilityStatus
import com.wire.kalium.logic.data.user.type.DomainUserTypeMapper
import com.wire.kalium.logic.data.user.type.UserType
import com.wire.kalium.persistence.dao.ConnectionEntity
import com.wire.kalium.persistence.dao.QualifiedIDEntity
import com.wire.kalium.persistence.dao.UserAvailabilityStatusEntity
import com.wire.kalium.persistence.dao.UserTypeEntity
import com.wire.kalium.persistence.dao.message.MessageEntity
import com.wire.kalium.persistence.dao.receipt.DetailedReceiptEntity
import com.wire.kalium.persistence.dao.receipt.ReceiptTypeEntity
import com.wire.kalium.util.DateTimeUtil
import io.mockative.eq
import io.mockative.every
import io.mockative.mock
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class ReceiptsMapperTest {

    @Test
    fun givenREADReceiptType_whenMappingToEntity_thenReturnREADReceiptTypeEntity() = runTest {
        // given
        val (_, receiptsMapper) = Arrangement().arrange()

        // when
        val result = receiptsMapper.toTypeEntity(type = ReceiptType.READ)

        // then
        assertEquals(
            ReceiptTypeEntity.READ,
            result
        )
    }

    @Test
    fun givenDELIVERYReceiptType_whenMappingToEntity_thenReturnDELIVERYReceiptTypeEntity() = runTest {
        // given
        val (_, receiptsMapper) = Arrangement().arrange()

        // when
        val result = receiptsMapper.toTypeEntity(type = ReceiptType.DELIVERED)

        // then
        assertEquals(
            ReceiptTypeEntity.DELIVERY,
            result
        )
    }

    @Test
    fun givenREADReceiptTypeEntity_whenMappingFromEntity_thenReturnREADReceiptType() = runTest {
        // given
        val (_, receiptsMapper) = Arrangement().arrange()

        // when
        val result = receiptsMapper.fromTypeEntity(type = ReceiptTypeEntity.READ)

        // then
        assertEquals(
            ReceiptType.READ,
            result
        )
    }

    @Test
    fun givenDELIVERYReceiptTypeEntity_whenMappingFromEntity_thenReturnDELIVERYReceiptType() = runTest {
        // given
        val (_, receiptsMapper) = Arrangement().arrange()

        // when
        val result = receiptsMapper.fromTypeEntity(type = ReceiptTypeEntity.DELIVERY)

        // then
        assertEquals(
            ReceiptType.DELIVERED,
            result
        )
    }

    @Test
    fun givenDetailedReceiptEntity_whenMappingToModel_thenReturnDetailedReceipt() = runTest {
        // given
        val date = DateTimeUtil.currentInstant()
        val detailedReceiptEntity = DetailedReceiptEntity(
            type = ReceiptTypeEntity.READ,
            userId = SELF_USER_ID_ENTITY,
            userName = "Self User Name",
            userHandle = "selfuserhandle",
            userPreviewAssetId = null,
            userType = UserTypeEntity.STANDARD,
            isUserDeleted = false,
            connectionStatus = ConnectionEntity.State.ACCEPTED,
            availabilityStatus = UserAvailabilityStatusEntity.NONE,
            date = date,
            accentId = 0
        )

        val expectedDetailedReceipt = DetailedReceipt(
            type = ReceiptType.READ,
            date = date,
            userSummary = UserSummary(
                userId = SELF_USER_ID,
                userName = "Self User Name",
                userHandle = "selfuserhandle",
                userPreviewAssetId = null,
                userType = UserType.INTERNAL,
                isUserDeleted = false,
                connectionStatus = ConnectionState.ACCEPTED,
                availabilityStatus = UserAvailabilityStatus.NONE,
                accentId = 0
            )
        )

        val (_, receiptsMapper) = Arrangement().withDomainUserTypeStandard().withConnectionStateAccepted().withAvailabilityStatusNone()
            .arrange()

        // when
        val result = receiptsMapper.fromEntityToModel(detailedReceiptEntity)

        // then
        assertEquals(
            expectedDetailedReceipt,
            result
        )
    }

    @Test
    fun givenReadReceiptType_whenMappingToMessageEntityStatus_thenReturnReadStatus() = runTest {
        // given
        val (_, receiptsMapper) = Arrangement().arrange()

        // when
        val result = receiptsMapper.fromTypeToMessageStatus(type = ReceiptType.READ)

        // then
        assertEquals(
            MessageEntity.Status.READ,
            result
        )
    }

    @Test
    fun givenDeliveryReceiptType_whenMappingToMessageEntityStatus_thenReturnDeliveryStatus() = runTest {
        // given
        val (_, receiptsMapper) = Arrangement().arrange()

        // when
        val result = receiptsMapper.fromTypeEntity(type = ReceiptTypeEntity.DELIVERY)

        // then
        assertEquals(
            ReceiptType.DELIVERED,
            result
        )
    }

    private class Arrangement {
        val availabilityStatusMapper = mock(AvailabilityStatusMapper::class)
        val connectionStateMapper = mock(ConnectionStateMapper::class)
        val domainUserTypeMapper = mock(DomainUserTypeMapper::class)

        fun withDomainUserTypeStandard() = apply {
            every {
                domainUserTypeMapper.fromUserTypeEntity(eq(UserTypeEntity.STANDARD))
            }.returns(UserType.INTERNAL)
        }

        fun withConnectionStateAccepted() = apply {
            every {
                connectionStateMapper.fromDaoConnectionStateToUser(eq(ConnectionEntity.State.ACCEPTED))
            }.returns(ConnectionState.ACCEPTED)
        }

        fun withAvailabilityStatusNone() = apply {
            every {
                availabilityStatusMapper.fromDaoAvailabilityStatusToModel(eq(UserAvailabilityStatusEntity.NONE))
            }.returns(UserAvailabilityStatus.NONE)
        }

        fun arrange() = this to ReceiptsMapperImpl(
            availabilityStatusMapper, connectionStateMapper, domainUserTypeMapper
        )
    }

    private companion object {
        val SELF_USER_ID = QualifiedID(
            value = "selfUserValue",
            domain = "selfUserDomain"
        )
        val SELF_USER_ID_ENTITY = QualifiedIDEntity(
            value = "selfUserValue",
            domain = "selfUserDomain"
        )
    }
}
