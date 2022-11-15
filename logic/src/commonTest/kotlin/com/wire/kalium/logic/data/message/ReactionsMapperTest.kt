package com.wire.kalium.logic.data.message

import com.wire.kalium.logic.data.id.IdMapper
import com.wire.kalium.logic.data.id.QualifiedID
import com.wire.kalium.logic.data.message.reaction.MessageReaction
import com.wire.kalium.logic.data.message.reaction.ReactionsMapperImpl
import com.wire.kalium.logic.data.user.AvailabilityStatusMapper
import com.wire.kalium.logic.data.user.ConnectionState
import com.wire.kalium.logic.data.user.ConnectionStateMapper
import com.wire.kalium.logic.data.user.UserAvailabilityStatus
import com.wire.kalium.logic.data.user.type.DomainUserTypeMapper
import com.wire.kalium.logic.data.user.type.UserType
import com.wire.kalium.persistence.MessageDetailsReactions
import com.wire.kalium.persistence.dao.ConnectionEntity
import com.wire.kalium.persistence.dao.QualifiedIDEntity
import com.wire.kalium.persistence.dao.UserAvailabilityStatusEntity
import com.wire.kalium.persistence.dao.UserTypeEntity
import com.wire.kalium.persistence.dao.reaction.MessageReactionEntity
import io.mockative.Mock
import io.mockative.eq
import io.mockative.given
import io.mockative.mock
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class ReactionsMapperTest {

    @Test
    fun givenMessageDetailsReactions_whenMappingToEntity_thenReturnMessageReactionEntity() = runTest {
        // given
        val messageDetailsReactions = MessageDetailsReactions(
            emoji = "🤯",
            messageId = "messageId",
            conversationId = CONVERSATION_ID,
            userId = USER_ID,
            name = "User Name",
            handle = "userhandle",
            previewAssetId = null,
            userType = UserTypeEntity.STANDARD,
            deleted = false,
            connectionStatus = ConnectionEntity.State.ACCEPTED,
            userAvailabilityStatus = UserAvailabilityStatusEntity.NONE
        )

        val expectedMessageReactionEntity = MessageReactionEntity(
            emoji = "🤯",
            userId = USER_ID,
            name = "User Name",
            handle = "userhandle",
            previewAssetIdEntity = null,
            userTypeEntity = UserTypeEntity.STANDARD,
            deleted = false,
            connectionStatus = ConnectionEntity.State.ACCEPTED,
            availabilityStatus = UserAvailabilityStatusEntity.NONE
        )

        val (_, reactionsMapper) = Arrangement()
            .arrange()

        // when
        val result = reactionsMapper.fromDAOToEntity(messageReaction = messageDetailsReactions)

        // then
        assertEquals(
            expectedMessageReactionEntity,
            result
        )
    }

    @Test
    fun givenMessageReactionEntity_whenMappingToModel_thenReturnMessageReaction() = runTest {
        // given
        val messageReactionEntity = MessageReactionEntity(
            emoji = "🤯",
            userId = SELF_USER_ID_ENTITY,
            name = "Self User Name",
            handle = "selfuserhandle",
            previewAssetIdEntity = null,
            userTypeEntity = UserTypeEntity.STANDARD,
            deleted = false,
            connectionStatus = ConnectionEntity.State.ACCEPTED,
            availabilityStatus = UserAvailabilityStatusEntity.NONE
        )

        val expectedMessageReaction = MessageReaction(
            emoji = "🤯",
            userId = SELF_USER_ID,
            name = "Self User Name",
            handle = "selfuserhandle",
            isSelfUser = true,
            previewAssetId = null,
            userType = UserType.INTERNAL,
            deleted = false,
            connectionStatus = ConnectionState.ACCEPTED,
            userAvailabilityStatus = UserAvailabilityStatus.NONE
        )

        val (_, reactionsMapper) = Arrangement()
            .withDomainUserTypeStandard()
            .withConnectionStateAccepted()
            .withAvailabilityStatusNone()
            .withIdMapper(SELF_USER_ID_ENTITY)
            .arrange()

        // when
        val result = reactionsMapper.fromEntityToModel(
            selfUserId = SELF_USER_ID,
            messageReactionEntity
        )

        // then
        assertEquals(
            expectedMessageReaction,
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

        fun arrange() = this to ReactionsMapperImpl(
            idMapper, availabilityStatusMapper, connectionStateMapper, domainUserTypeMapper
        )
    }

    private companion object {
        val CONVERSATION_ID = QualifiedIDEntity(
            value = "convValue",
            domain = "convDomain"
        )
        val SELF_USER_ID = QualifiedID(
            value = "selfUserValue",
            domain = "selfUserDomain"
        )
        val SELF_USER_ID_ENTITY = QualifiedIDEntity(
            value = "selfUserValue",
            domain = "selfUserDomain"
        )
        val USER_ID = QualifiedIDEntity(
            value = "userValue",
            domain = "userDomain"
        )
    }
}
