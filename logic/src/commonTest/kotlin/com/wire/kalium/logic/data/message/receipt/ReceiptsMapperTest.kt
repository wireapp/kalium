package com.wire.kalium.logic.data.message.receipt

import com.wire.kalium.logic.data.id.IdMapper
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
import com.wire.kalium.persistence.dao.receipt.DetailedReceiptEntity
import com.wire.kalium.persistence.dao.receipt.ReceiptTypeEntity
import com.wire.kalium.util.DateTimeUtil
import io.mockative.Mock
import io.mockative.eq
import io.mockative.given
import io.mockative.mock
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class ReceiptsMapperTest {

    @Test
    fun givenREADReceiptType_whenMappingToEntity_thenReturnREADReceiptTypeEntity() = runTest {
        // given
        val (_, receiptsMapper) = Arrangement()
            .arrange()

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
        val (_, receiptsMapper) = Arrangement()
            .arrange()

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
        val (_, receiptsMapper) = Arrangement()
            .arrange()

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
        val (_, receiptsMapper) = Arrangement()
            .arrange()

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
            date = date
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
                availabilityStatus = UserAvailabilityStatus.NONE
            )
        )

        val (_, receiptsMapper) = Arrangement()
            .withDomainUserTypeStandard()
            .withConnectionStateAccepted()
            .withAvailabilityStatusNone()
            .withIdMapper(SELF_USER_ID_ENTITY)
            .arrange()

        // when
        val result = receiptsMapper.fromEntityToModel(detailedReceiptEntity)

        // then
        assertEquals(
            expectedDetailedReceipt,
            result
        )
    }

    private class Arrangement {

        @Mock
        val idMapper = mock(IdMapper::class)

        @Mock
        val availabilityStatusMapper = mock(AvailabilityStatusMapper::class)

        @Mock
        val connectionStateMapper = mock(ConnectionStateMapper::class)

        @Mock
        val domainUserTypeMapper = mock(DomainUserTypeMapper::class)

        fun withDomainUserTypeStandard() = apply {
            given(domainUserTypeMapper)
                .function(domainUserTypeMapper::fromUserTypeEntity)
                .whenInvokedWith(eq(UserTypeEntity.STANDARD))
                .then { UserType.INTERNAL }
        }

        fun withConnectionStateAccepted() = apply {
            given(connectionStateMapper)
                .function(connectionStateMapper::fromDaoConnectionStateToUser)
                .whenInvokedWith(eq(ConnectionEntity.State.ACCEPTED))
                .then { ConnectionState.ACCEPTED }
        }

        fun withAvailabilityStatusNone() = apply {
            given(availabilityStatusMapper)
                .function(availabilityStatusMapper::fromDaoAvailabilityStatusToModel)
                .whenInvokedWith(eq(UserAvailabilityStatusEntity.NONE))
                .then { UserAvailabilityStatus.NONE }
        }

        fun withIdMapper(id: QualifiedIDEntity) = apply {
            given(idMapper)
                .function(idMapper::fromDaoModel)
                .whenInvokedWith(eq(id))
                .then { QualifiedID(id.value, id.domain) }
        }

        fun arrange() = this to ReceiptsMapperImpl(
            idMapper, availabilityStatusMapper, connectionStateMapper, domainUserTypeMapper
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
