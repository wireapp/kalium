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

package com.wire.kalium.logic.data.conversation

import app.cash.turbine.test
import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.isLeft
import com.wire.kalium.common.functional.isRight
import com.wire.kalium.common.functional.right
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.GroupID
import com.wire.kalium.logic.data.id.PersistenceQualifiedId
import com.wire.kalium.logic.data.id.QualifiedID
import com.wire.kalium.logic.data.id.SelfTeamIdProvider
import com.wire.kalium.logic.data.id.toApi
import com.wire.kalium.logic.data.id.toDao
import com.wire.kalium.logic.data.id.toModel
import com.wire.kalium.logic.data.message.UnreadEventType
import com.wire.kalium.logic.data.user.OtherUser
import com.wire.kalium.logic.data.user.SelfUser
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.logic.framework.TestConversationDetails
import com.wire.kalium.logic.framework.TestTeam
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.logic.test_util.TestNetworkException
import com.wire.kalium.logic.util.arrangement.dao.MemberDAOArrangement
import com.wire.kalium.logic.util.arrangement.dao.MemberDAOArrangementImpl
import com.wire.kalium.logic.util.shouldFail
import com.wire.kalium.logic.util.shouldSucceed
import com.wire.kalium.network.api.authenticated.conversation.ChannelAddPermissionTypeDTO
import com.wire.kalium.network.api.authenticated.conversation.ConvProtocol
import com.wire.kalium.network.api.authenticated.conversation.ConversationMemberDTO
import com.wire.kalium.network.api.authenticated.conversation.ConversationMembersResponse
import com.wire.kalium.network.api.authenticated.conversation.ConversationNameUpdateEvent
import com.wire.kalium.network.api.authenticated.conversation.ConversationPagingResponse
import com.wire.kalium.network.api.authenticated.conversation.ConversationRenameResponse
import com.wire.kalium.network.api.authenticated.conversation.ConversationResponse
import com.wire.kalium.network.api.authenticated.conversation.ConversationResponseDTO
import com.wire.kalium.network.api.authenticated.conversation.ReceiptMode
import com.wire.kalium.network.api.authenticated.conversation.UpdateChannelAddPermissionResponse
import com.wire.kalium.network.api.authenticated.conversation.UpdateConversationAccessRequest
import com.wire.kalium.network.api.authenticated.conversation.UpdateConversationAccessResponse
import com.wire.kalium.network.api.authenticated.conversation.UpdateConversationProtocolResponse
import com.wire.kalium.network.api.authenticated.conversation.UpdateConversationReceiptModeResponse
import com.wire.kalium.network.api.authenticated.conversation.channel.ChannelAddPermissionDTO
import com.wire.kalium.network.api.authenticated.conversation.model.ConversationAccessInfoDTO
import com.wire.kalium.network.api.authenticated.conversation.model.ConversationMemberRoleDTO
import com.wire.kalium.network.api.authenticated.conversation.model.ConversationProtocolDTO
import com.wire.kalium.network.api.authenticated.conversation.model.ConversationReceiptModeDTO
import com.wire.kalium.network.api.authenticated.notification.EventContentDTO
import com.wire.kalium.network.api.base.authenticated.client.ClientApi
import com.wire.kalium.network.api.base.authenticated.conversation.ConversationApi
import com.wire.kalium.network.api.model.ConversationAccessDTO
import com.wire.kalium.network.api.model.ConversationAccessRoleDTO
import com.wire.kalium.network.exceptions.KaliumException
import com.wire.kalium.network.utils.NetworkResponse
import com.wire.kalium.persistence.dao.ConversationIDEntity
import com.wire.kalium.persistence.dao.MetadataDAO
import com.wire.kalium.persistence.dao.QualifiedIDEntity
import com.wire.kalium.persistence.dao.UserIDEntity
import com.wire.kalium.persistence.dao.client.ClientDAO
import com.wire.kalium.persistence.dao.client.ClientTypeEntity
import com.wire.kalium.persistence.dao.client.DeviceTypeEntity
import com.wire.kalium.persistence.dao.conversation.ConversationDAO
import com.wire.kalium.persistence.dao.conversation.ConversationDetailsWithEventsEntity
import com.wire.kalium.persistence.dao.conversation.ConversationEntity
import com.wire.kalium.persistence.dao.conversation.ConversationMetaDataDAO
import com.wire.kalium.persistence.dao.conversation.ConversationViewEntity
import com.wire.kalium.persistence.dao.message.MessageDAO
import com.wire.kalium.persistence.dao.message.MessageEntity
import com.wire.kalium.persistence.dao.message.MessagePreviewEntity
import com.wire.kalium.persistence.dao.message.MessagePreviewEntityContent
import com.wire.kalium.persistence.dao.message.draft.MessageDraftDAO
import com.wire.kalium.persistence.dao.message.draft.MessageDraftEntity
import com.wire.kalium.persistence.dao.unread.ConversationUnreadEventEntity
import com.wire.kalium.persistence.dao.unread.UnreadEventTypeEntity
import com.wire.kalium.util.DateTimeUtil
import io.ktor.http.HttpStatusCode
import io.mockative.any
import io.mockative.coEvery
import io.mockative.coVerify
import io.mockative.eq
import io.mockative.fake.valueOf
import io.mockative.matchers.AnyMatcher
import io.mockative.matchers.EqualsMatcher
import io.mockative.matchers.Matcher
import io.mockative.matches
import io.mockative.mock
import io.mockative.once
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import com.wire.kalium.network.api.model.ConversationId as APIConversationId
import com.wire.kalium.persistence.dao.client.Client as ClientEntity

@Suppress("LargeClass")
class ConversationRepositoryTest {

    @Test
    fun givenConversationDaoReturnsAGroupConversation_whenGettingConversationDetailsById_thenReturnAGroupConversationDetails() =
        runTest {
            val conversationEntity = TestConversation.VIEW_ENTITY.copy(type = ConversationEntity.Type.GROUP)

            val (_, conversationRepository) = Arrangement()
                .withExpectedObservableConversationDetails(conversationEntity)
                .arrange()

            conversationRepository.observeConversationDetailsById(TestConversation.ID).test {
                assertIs<Either.Right<ConversationDetails.Group>>(awaitItem())
                awaitComplete()
            }
        }

    @Test
    fun givenConversationDaoReturnsASelfConversation_whenGettingConversationDetailsById_thenReturnASelfConversationDetails() =
        runTest {
            val conversationEntity = TestConversation.VIEW_ENTITY.copy(type = ConversationEntity.Type.SELF)

            val (_, conversationRepository) = Arrangement()
                .withExpectedObservableConversationDetails(conversationEntity)
                .arrange()

            conversationRepository.observeConversationDetailsById(TestConversation.ID).test {
                assertIs<Either.Right<ConversationDetails.Self>>(awaitItem())
                awaitComplete()
            }
        }

    @Test
    fun givenConversationDaoReturnsAOneOneConversation_whenGettingConversationDetailsById_thenReturnAOneOneConversationDetails() =
        runTest {
            val conversationId = TestConversation.ENTITY_ID
            val conversationEntity = TestConversation.VIEW_ENTITY.copy(
                id = conversationId,
                type = ConversationEntity.Type.ONE_ON_ONE,
                otherUserId = QualifiedIDEntity("otherUser", "domain")
            )

            val (_, conversationRepository) = Arrangement()
                .withExpectedObservableConversationDetails(conversationEntity)
                .arrange()

            conversationRepository.observeConversationDetailsById(TestConversation.ID).test {
                assertIs<Either.Right<ConversationDetails.OneOne>>(awaitItem())
                awaitComplete()
            }
        }

    @Test
    fun givenUserHasKnownContactAndConversation_WhenGettingConversationByExistingConversation_ReturnTheCorrectConversation() =
        runTest {
            // given
            val (_, conversationRepository) = Arrangement()
                .withSelfUserFlow(flowOf(TestUser.SELF))
                .withExpectedConversationWithOtherUser(TestConversation.ENTITY)
                .withExpectedOtherKnownUser(TestUser.OTHER)
                .arrange()

            // when
            conversationRepository.observeOneToOneConversationWithOtherUser(OTHER_USER_ID).test {
                val result = awaitItem()
                // then
                assertIs<Either.Right<ConversationDetails.OneOne>>(result)
                awaitComplete()
            }
        }

    @Test
    fun givenUserHasKnownContactAndConversation_WhenGettingConversationDetailsByExistingConversation_ReturnTheCorrectConversationDetails() =
        runTest {
            // given
            val (_, conversationRepository) = Arrangement()
                .withSelfUserFlow(flowOf(TestUser.SELF))
                .withExpectedConversationDetailsWithOtherUser(TestConversation.VIEW_ONE_ON_ONE.copy(otherUserId = OTHER_USER_ID.toDao()))
                .withExpectedOtherKnownUser(TestUser.OTHER)
                .arrange()

            // when
            conversationRepository.observeOneToOneConversationDetailsWithOtherUser(OTHER_USER_ID).test {
                val result = awaitItem()
                // then
                assertIs<Either.Right<ConversationDetails.OneOne>>(result)
                awaitComplete()
            }
        }

    @Test
    fun whenCallingUpdateMutedStatusRemotely_thenShouldDelegateCallToConversationApi() = runTest {
        val (arrangement, conversationRepository) = Arrangement()
            .withUpdateConversationMemberStateResult(NetworkResponse.Success(Unit, mapOf(), HttpStatusCode.OK.value))
            .arrange()

        conversationRepository.updateMutedStatusRemotely(
            TestConversation.ID,
            MutedConversationStatus.AllMuted,
            DateTimeUtil.currentInstant().toEpochMilliseconds()
        )

        coVerify {
            arrangement.conversationApi.updateConversationMemberState(any(), any())
        }.wasInvoked(exactly = once)

        coVerify {
            arrangement.conversationDAO.updateConversationMutedStatus(any(), any(), any())
        }.wasNotInvoked()
    }

    @Test
    fun whenCallingUpdateArchivedStatusRemotely_thenShouldDelegateCallToConversationApi() = runTest {
        val isArchived = false
        val (arrangement, conversationRepository) = Arrangement()
            .withUpdateConversationMemberStateResult(NetworkResponse.Success(Unit, mapOf(), HttpStatusCode.OK.value))
            .arrange()

        conversationRepository.updateArchivedStatusRemotely(
            TestConversation.ID,
            isArchived,
            DateTimeUtil.currentInstant().toEpochMilliseconds()
        )

        coVerify {
            arrangement.conversationApi.updateConversationMemberState(any(), any())
        }.wasInvoked(exactly = once)

        coVerify {
            arrangement.conversationDAO.updateConversationArchivedStatus(any(), any(), any())
        }.wasNotInvoked()
    }

    @Test
    fun whenCallingUpdateMutedStatusLocally_thenShouldUpdateTheDatabase() = runTest {
        val (arrangement, conversationRepository) = Arrangement()
            .withUpdateConversationMemberStateResult(NetworkResponse.Success(Unit, mapOf(), HttpStatusCode.OK.value))
            .arrange()

        conversationRepository.updateMutedStatusLocally(
            TestConversation.ID,
            MutedConversationStatus.AllMuted,
            DateTimeUtil.currentInstant().toEpochMilliseconds()
        )

        coVerify {
            arrangement.conversationDAO.updateConversationMutedStatus(any(), any(), any())
        }.wasInvoked(exactly = once)

        coVerify {
            arrangement.conversationApi.updateConversationMemberState(any(), any())
        }.wasNotInvoked()
    }

    @Suppress("LongMethod")
    @Test
    fun givenUpdateAccessRoleSuccess_whenUpdatingConversationAccessInfo_thenTheNewAccessSettingsAreUpdatedLocally() =
        runTest {

            val conversationIdDTO = APIConversationId("conv_id", "conv_domain")
            val newAccessInfoDTO = ConversationAccessInfoDTO(
                accessRole = setOf(
                    ConversationAccessRoleDTO.TEAM_MEMBER,
                    ConversationAccessRoleDTO.NON_TEAM_MEMBER,
                    ConversationAccessRoleDTO.SERVICE,
                    ConversationAccessRoleDTO.GUEST,
                ),
                access = setOf(
                    ConversationAccessDTO.INVITE,
                    ConversationAccessDTO.CODE,
                    ConversationAccessDTO.PRIVATE,
                    ConversationAccessDTO.LINK
                )
            )
            val newAccess = UpdateConversationAccessResponse.AccessUpdated(
                EventContentDTO.Conversation.AccessUpdate(
                    conversationIdDTO,
                    data = newAccessInfoDTO,
                    qualifiedFrom = com.wire.kalium.network.api.model.UserId(
                        "from_id",
                        "from_domain"
                    )
                )
            )

            val (arrange, conversationRepository) = Arrangement()
                .withApiUpdateAccessRoleReturns(NetworkResponse.Success(newAccess, mapOf(), 200))
                .arrange()

            conversationRepository.updateAccessInfo(
                conversationID = ConversationId(conversationIdDTO.value, conversationIdDTO.domain),
                access = setOf(
                    Conversation.Access.INVITE,
                    Conversation.Access.CODE,
                    Conversation.Access.PRIVATE,
                    Conversation.Access.LINK
                ),
                accessRole = setOf(
                    Conversation.AccessRole.TEAM_MEMBER,
                    Conversation.AccessRole.NON_TEAM_MEMBER,
                    Conversation.AccessRole.SERVICE,
                    Conversation.AccessRole.GUEST
                )
            ).shouldSucceed()

            with(arrange) {
                coVerify {
                    conversationApi.updateAccess(
                        conversationIdDTO,
                        UpdateConversationAccessRequest(
                            newAccessInfoDTO.access,
                            newAccessInfoDTO.accessRole
                        )
                    )
                }.wasInvoked(exactly = once)

                coVerify {
                    conversationDAO.updateAccess(
                        ConversationIDEntity(conversationIdDTO.value, conversationIdDTO.domain),
                        accessList = listOf(
                            ConversationEntity.Access.INVITE,
                            ConversationEntity.Access.CODE,
                            ConversationEntity.Access.PRIVATE,
                            ConversationEntity.Access.LINK
                        ),
                        accessRoleList = listOf(
                            ConversationEntity.AccessRole.TEAM_MEMBER,
                            ConversationEntity.AccessRole.NON_TEAM_MEMBER,
                            ConversationEntity.AccessRole.SERVICE,
                            ConversationEntity.AccessRole.GUEST
                        )
                    )
                }.wasInvoked(exactly = once)
            }
        }

    @Test
    fun givenUpdateConversationMemberRoleSuccess_whenUpdatingConversationMemberRole_thenTheNewRoleIsUpdatedLocally() =
        runTest {
            val (arrange, conversationRepository) = Arrangement()
                .withApiUpdateConversationMemberRoleReturns(NetworkResponse.Success(Unit, mapOf(), 200))
                .withDaoUpdateConversationMemberRoleSuccess()
                .arrange()
            val conversationId = ConversationId("conv_id", "conv_domain")
            val userId: UserId = UserId("user_id", "user_domain")
            val newRole = Conversation.Member.Role.Admin

            conversationRepository.updateConversationMemberRole(conversationId, userId, newRole).shouldSucceed()

            with(arrange) {
                coVerify {
                    conversationApi.updateConversationMemberRole(
                        conversationId.toApi(),
                        userId.toApi(),
                        ConversationMemberRoleDTO(MapperProvider.conversationRoleMapper().toApi(newRole))
                    )
                }.wasInvoked(exactly = once)

                coVerify {
                    memberDAO.updateConversationMemberRole(
                        conversationId.toDao(),
                        userId.toDao(),
                        MapperProvider.conversationRoleMapper().toDAO(newRole)
                    )
                }.wasInvoked(exactly = once)
            }
        }

    @Test
    fun givenProteusConversation_WhenDeletingTheConversation_ThenShouldBeDeletedLocally() = runTest {
        val (arrangement, conversationRepository) = Arrangement()
            .withGetConversationProtocolInfoReturns(PROTEUS_PROTOCOL_INFO)
            .withSuccessfulConversationDeletion()
            .arrange()
        val conversationId = ConversationId("conv_id", "conv_domain")

        conversationRepository.deleteConversationLocally(conversationId).shouldSucceed()

        with(arrangement) {
            coVerify {
                conversationDAO.deleteConversationByQualifiedID(eq(conversationId.toDao()))
            }.wasInvoked(once)
        }
    }

    @Test
    fun givenAGroupConversationHasNewMessages_whenGettingConversationDetails_ThenCorrectlyGetUnreadMessageCountAndLastMessage() = runTest {
        // given
        val conversationIdEntity = ConversationIDEntity("some_value", "some_domain")
        val conversationId = QualifiedID("some_value", "some_domain")
        val shouldFetchFromArchivedConversations = false
        val messagePreviewEntity = MESSAGE_PREVIEW_ENTITY.copy(conversationId = conversationIdEntity)

        val conversationEntity = TestConversation.VIEW_ENTITY.copy(
            id = conversationIdEntity,
            type = ConversationEntity.Type.GROUP,
        )

        val unreadMessagesCount = 5
        val conversationUnreadEventEntity = ConversationUnreadEventEntity(
            conversationIdEntity,
            mapOf(UnreadEventTypeEntity.MESSAGE to unreadMessagesCount)
        )

        val (_, conversationRepository) = Arrangement()
            .withConversations(listOf(conversationEntity))
            .withLastMessages(listOf(messagePreviewEntity))
            .withConversationUnreadEvents(listOf(conversationUnreadEventEntity))
            .withMessageDrafts(listOf())
            .arrange()

        // when
        conversationRepository.observeConversationListDetailsWithEvents(shouldFetchFromArchivedConversations).test {
            val result = awaitItem()

            assertContains(result.map { it.conversationDetails.conversation.id }, conversationId)
            val conversation = result.first { it.conversationDetails.conversation.id == conversationId }

            assertEquals(conversation.unreadEventCount[UnreadEventType.MESSAGE], unreadMessagesCount)
            assertEquals(
                MapperProvider.messageMapper(TestUser.SELF.id).fromEntityToMessagePreview(messagePreviewEntity),
                conversation.lastMessage
            )

            awaitComplete()
        }
    }

    @Test
    fun givenArchivedConversationHasNewMessages_whenGettingConversationDetails_ThenCorrectlyGetUnreadMessageCountAndNullLastMessage() =
        runTest {
            // given
            val conversationIdEntity = ConversationIDEntity("some_value", "some_domain")
            val conversationId = QualifiedID("some_value", "some_domain")
            val shouldFetchFromArchivedConversations = true

            val conversationEntity = TestConversation.VIEW_ENTITY.copy(
                id = conversationIdEntity,
                type = ConversationEntity.Type.GROUP,
            )

            val unreadMessagesCount = 5
            val conversationUnreadEventEntity = ConversationUnreadEventEntity(
                conversationIdEntity,
                mapOf(UnreadEventTypeEntity.MESSAGE to unreadMessagesCount)
            )

            val (_, conversationRepository) = Arrangement()
                .withConversations(listOf(conversationEntity))
                .withLastMessages(listOf(MESSAGE_PREVIEW_ENTITY.copy(conversationId = conversationIdEntity)))
                .withMessageDrafts(listOf())
                .withConversationUnreadEvents(listOf(conversationUnreadEventEntity))
                .arrange()

            // when
            conversationRepository.observeConversationListDetailsWithEvents(shouldFetchFromArchivedConversations).test {
                val result = awaitItem()

                assertContains(result.map { it.conversationDetails.conversation.id }, conversationId)
                val conversation = result.first { it.conversationDetails.conversation.id == conversationId }

                assertEquals(conversation.unreadEventCount[UnreadEventType.MESSAGE], unreadMessagesCount)
                assertEquals(null, conversation.lastMessage)

                awaitComplete()
            }
        }

    // TODO: bring back once pagination is implemented
    @Ignore
    @Test
    fun givenAGroupConversationHasNotNewMessages_whenGettingConversationDetails_ThenReturnZeroUnreadMessageCount() = runTest {
        // given
        val conversationEntity = TestConversation.VIEW_ENTITY.copy(
            type = ConversationEntity.Type.GROUP,
        )
        val (_, conversationRepository) = Arrangement()
            .withExpectedObservableConversationDetails(conversationEntity)
            .arrange()

        // when
        conversationRepository.observeConversationDetailsById(conversationEntity.id.toModel()).test {
            // then
            val conversationDetail = awaitItem()

            assertIs<ConversationDetails.Group>(conversationDetail)
//             assertTrue { conversationDetail.lastMessage == null }

            awaitComplete()
        }
    }

    // TODO: bring back once pagination is implemented
//     @Test
//     fun givenAOneToOneConversationHasNotNewMessages_whenGettingConversationDetails_ThenReturnZeroUnreadMessageCount() =
//         runTest {
//             // given
//             val conversationIdEntity = ConversationIDEntity("some_value", "some_domain")
//             val conversationId = QualifiedID("some_value", "some_domain")
//             val shouldFetchFromArchivedConversations = false
//             val conversationEntity = TestConversation.VIEW_ENTITY.copy(
//                 id = conversationIdEntity,
//                 type = ConversationEntity.Type.ONE_ON_ONE,
//                 otherUserId = QualifiedIDEntity("otherUser", "domain"),
//             )
//             val conversationDetailsWithEventsEntity = ConversationDetailsWithEventsEntity(conversationViewEntity = conversationEntity)
//
//             val (_, conversationRepository) = Arrangement()
//                 .withConversationDetailsWithEvents(listOf(conversationDetailsWithEventsEntity))
//                 .arrange()
//
//             // when
//             conversationRepository.observeConversationListDetailsWithEvents(shouldFetchFromArchivedConversations).test {
//                 // then
//                 val conversation = awaitItem().first { it.conversationDetails.conversation.id == conversationId }
//
//                 assertIs<ConversationDetails.OneOne>(conversation.conversationDetails)
//                 assertTrue { conversation.lastMessage == null }
//
//                 awaitComplete()
//             }
//         }

    @Test
    fun givenAGroupConversationHasNewMessages_whenObservingConversationListDetails_ThenCorrectlyGetUnreadMessageCount() = runTest {
        // given
        val conversationIdEntity = ConversationIDEntity("some_value", "some_domain")
        val conversationId = QualifiedID("some_value", "some_domain")
        val shouldFetchFromArchivedConversations = false

        val conversationEntity = TestConversation.VIEW_ENTITY.copy(
            id = conversationIdEntity, type = ConversationEntity.Type.ONE_ON_ONE,
            otherUserId = QualifiedIDEntity("otherUser", "domain")
        )

        val unreadMessagesCount = 5
        val conversationUnreadEventEntity = ConversationUnreadEventEntity(
            conversationIdEntity,
            mapOf(UnreadEventTypeEntity.MESSAGE to unreadMessagesCount)
        )

        val (_, conversationRepository) = Arrangement()
            .withConversations(listOf(conversationEntity))
            .withLastMessages(listOf())
            .withMessageDrafts(listOf())
            .withConversationUnreadEvents(listOf(conversationUnreadEventEntity))
            .arrange()

        // when
        conversationRepository.observeConversationListDetailsWithEvents(shouldFetchFromArchivedConversations).test {
            val result = awaitItem()

            assertContains(result.map { it.conversationDetails.conversation.id }, conversationId)
            val conversation = result.first { it.conversationDetails.conversation.id == conversationId }

            assertEquals(conversation.unreadEventCount[UnreadEventType.MESSAGE], unreadMessagesCount)

            awaitComplete()
        }
    }

    @Test
    fun givenAConversationDaoFailed_whenUpdatingTheConversationReadDate_thenShouldNotSucceed() = runTest {
        // given
        val (arrangement, conversationRepository) = Arrangement()
            .withUpdateConversationReadDateException(IllegalStateException("Some illegal state"))
            .arrange()

        // when
        val result = conversationRepository.updateConversationReadDate(
            TestConversation.ID,
            Instant.fromEpochMilliseconds(1648654560000)
        )

        // then
        coVerify {
            arrangement.conversationDAO.updateConversationReadDate(any(), any())
        }.wasInvoked(exactly = once)
        assertIs<Either.Left<StorageFailure>>(result)
    }

    @Test
    fun givenAMemberInAConversation_WhenCheckingIfItIsMember_ThenShouldSucceed() = runTest {
        val isMember = true

        // given
        val (arrangement, conversationRepository) = Arrangement()
            .withExpectedIsUserMemberFlow(flowOf(isMember))
            .arrange()

        // when
        conversationRepository.observeIsUserMember(CONVERSATION_ID, USER_ID).test {
            // then
            val isMemberResponse = awaitItem()

            assertIs<Either.Right<Boolean>>(isMemberResponse)
            assertEquals(isMemberResponse.value, isMember)

            coVerify {
                arrangement.memberDAO.observeIsUserMember(eq(CONVERSATION_ENTITY_ID), eq(USER_ENTITY_ID))
            }.wasInvoked(exactly = once)

            awaitComplete()
        }

    }

    @Test
    fun givenAMemberIsNotInAConversation_WhenCheckingIfItIsMember_ThenShouldSucceed() = runTest {
        val isMember = false
        val (arrangement, conversationRepository) = Arrangement()
            .withExpectedIsUserMemberFlow(flowOf(isMember))
            .arrange()

        // when
        conversationRepository.observeIsUserMember(CONVERSATION_ID, USER_ID).test {
            // then
            val isMemberResponse = awaitItem()

            assertIs<Either.Right<Boolean>>(isMemberResponse)
            assertEquals(isMemberResponse.value, isMember)

            coVerify {
                arrangement.memberDAO.observeIsUserMember(eq(CONVERSATION_ENTITY_ID), eq(USER_ENTITY_ID))
            }.wasInvoked(exactly = once)

            awaitComplete()
        }
    }

    @Test
    fun givenADeletedMember_WhenInvokingWhoDeletedMe_ThenDaoCallShouldSucceed() = runTest {
        val whoDeletedMe = UserId("deletion-author", "deletion-author-domain")
        val conversationId = ConversationId("conv_id", "conv_domain")
        val selfUserFlow = flowOf(TestUser.SELF)
        val (arrange, conversationRepository) = Arrangement().withSelfUserFlow(selfUserFlow)
            .withWhoDeletedMe(whoDeletedMe).arrange()

        val result = conversationRepository.whoDeletedMe(conversationId)

        with(arrange) {
            result.shouldSucceed {}
            coVerify {
                conversationDAO.whoDeletedMeInConversation(any(), any())
            }.wasInvoked(once)
        }
    }

    @Test
    fun givenAConversationId_whenTheConversationDoesNotExists_thenShouldReturnDataNotFound() = runTest {
        val conversationId = ConversationId("conv_id", "conv_domain")
        val (_, conversationRepository) = Arrangement()
            .withExpectedConversationBase(null)
            .arrange()

        conversationRepository.getConversationById(conversationId)
            .shouldFail {
                assertIs<StorageFailure.DataNotFound>(it)
            }
    }

    @Test
    fun givenAConversationId_WhenTheConversationExists_ShouldReturnAConversationInstance() = runTest {
        val conversationId = ConversationId("conv_id", "conv_domain")
        val (_, conversationRepository) = Arrangement()
            .withExpectedObservableConversation(TestConversation.ENTITY)
            .arrange()

        val result = conversationRepository.getConversationById(conversationId)
        assertNotNull(result)
    }

    @Test
    fun givenAnUserId_WhenGettingConversationIds_ShouldReturnSuccess() = runTest {
        val userId = UserId("user_id", "user_domain")
        val (arrange, conversationRepository) = Arrangement()
            .withConversationsByUserId(listOf(TestConversation.ENTITY))
            .arrange()

        val result = conversationRepository.getConversationsByUserId(userId)
        with(result) {
            shouldSucceed()
            coVerify {
                arrange.conversationDAO.getConversationsByUserId(any())
            }.wasInvoked(exactly = once)
        }
    }

    @Test
    fun givenAConversation_WhenChangingNameConversation_ShouldReturnSuccess() = runTest {
        val newConversationName = "new_name"
        val (arrange, conversationRepository) = Arrangement()
            .withConversationRenameApiCall(newConversationName)
            .withConversationRenameCall(newConversationName)
            .arrange()

        val result = conversationRepository.changeConversationName(CONVERSATION_ID, newConversationName)
        result.shouldSucceed()
    }

    @Test
    fun whenGettingConversationRecipients_thenDAOFunctionIscalled() = runTest {
        val (arrange, conversationRepository) = Arrangement()
            .withConversationRecipients(CONVERSATION_ENTITY_ID, emptyMap())
            .arrange()

        val result = conversationRepository.getConversationRecipients(CONVERSATION_ID)
        with(result) {
            shouldSucceed()
            coVerify {
                arrange.clientDao.conversationRecipient(any())
            }.wasInvoked(exactly = once)
        }
    }

    @Test
    fun givenAConversationReceiptMode_whenUpdatingConversationReceiptMode_thenShouldUpdateLocally() = runTest {
        // given
        val apiReceiptMode = ReceiptMode.ENABLED
        val receiptMode = Conversation.ReceiptMode.ENABLED

        val (arrange, conversationRepository) = Arrangement()
            .withUpdateReceiptModeSuccess(apiReceiptMode)
            .arrange()

        // when
        val result = conversationRepository.updateReceiptMode(CONVERSATION_ID, receiptMode)

        // then
        with(result) {
            shouldSucceed()
            coVerify {
                arrange.conversationDAO.updateConversationReceiptMode(any(), any())
            }.wasInvoked(exactly = once)
        }
    }

    @Test
    fun givenSuccess_whenGettingDeleteMessageRecipients_thenSuccessIsPropagated() = runTest {
        val user = QualifiedIDEntity("userId", "domain.com")
        val conversationId = QualifiedIDEntity("conversationId", "domain.com")
        val clients = listOf(
            ClientEntity(
                user,
                "clientId",
                DeviceTypeEntity.Desktop,
                ClientTypeEntity.Permanent,
                true,
                true,
                null,
                null,
                null,
                null,
                null,
                false,
                false
            )
        )

        val expected = Recipient(
            user.toModel(),
            clients.map { ClientId(it.id) }
        )

        val (arrangement, repo) = Arrangement()
            .withConversationRecipientByUserSuccess(mapOf(user to clients))
            .arrange()

        repo.getRecipientById(conversationId.toModel(), listOf(user.toModel())).shouldSucceed {
            assertEquals(listOf(expected), it)
        }

        coVerify {
            arrangement.clientDao.recipientsIfTheyArePartOfConversation(eq(conversationId), eq(setOf(user)))
        }.wasInvoked(exactly = once)
    }

    @Test
    fun givenDomains_whenGettingConversationsWithMembersWithBothDomains_thenShouldReturnConversationsWithMembers() = runTest {
        // Given
        val federatedDomain = "federated.com"
        val selfDomain = "selfdomain.com"
        val federatedUserId = QualifiedIDEntity("fed_user", federatedDomain)
        val federatedUserIdList = List(5) {
            QualifiedIDEntity("fed_user_$it", federatedDomain)
        }
        val selfUserIdList = List(5) {
            QualifiedIDEntity("self_user_$it", selfDomain)
        }

        val userIdList = federatedUserIdList + selfUserIdList
        val groupConversationId = QualifiedIDEntity("conversation_group", selfDomain)
        val oneOnOneConversationId = QualifiedIDEntity("conversation_one_on_one", selfDomain)

        val (arrangement, conversationRepository) = Arrangement()
            .withGetGroupConversationWithUserIdsWithBothDomains(
                mapOf(groupConversationId to userIdList),
                EqualsMatcher(selfDomain),
                EqualsMatcher(federatedDomain)
            )
            .withGetOneOnOneConversationWithFederatedUserId(
                mapOf(oneOnOneConversationId to federatedUserId),
                EqualsMatcher(federatedDomain)
            )
            .arrange()

        // When
        val groupConversationResult = conversationRepository.getGroupConversationsWithMembersWithBothDomains(selfDomain, federatedDomain)
        val oneOnOneConversationResult = conversationRepository.getOneOnOneConversationsWithFederatedMembers(federatedDomain)

        // Then
        groupConversationResult.shouldSucceed {
            assertEquals(userIdList.map { idEntity -> idEntity.toModel() }, it[groupConversationId.toModel()])
        }

        oneOnOneConversationResult.shouldSucceed {
            assertEquals(federatedUserId.toModel(), it[oneOnOneConversationId.toModel()])
        }

        coVerify {
            arrangement.memberDAO.getGroupConversationWithUserIdsWithBothDomains(any(), any())
        }.wasInvoked(exactly = once)
    }

    @Test
    fun givenUnreadArchivedConversationsCount_WhenObserving_ThenShouldReturnSuccess() = runTest {
        val unreadCount = 10L
        val (arrange, conversationRepository) = Arrangement()
            .withUnreadArchivedConversationsCount(unreadCount)
            .arrange()

        val result = conversationRepository.observeUnreadArchivedConversationsCount().first()
        assertEquals(unreadCount, result)
    }

    @Test
    fun givenNoChange_whenUpdatingProtocolToMls_thenShouldUpdateLocally() = runTest {
        // given
        val protocol = Conversation.Protocol.MLS
        val conversationResponse = NetworkResponse.Success(
            TestConversation.CONVERSATION_RESPONSE,
            emptyMap(),
            HttpStatusCode.OK.value
        )
        val (arrange, conversationRepository) = Arrangement()
            .withDaoUpdateProtocolSuccess()
            .withUpdateProtocolResponse(UPDATE_PROTOCOL_UNCHANGED)
            .withFetchConversationsDetails(conversationResponse)
            .arrange()

        // when
        val result = conversationRepository.updateProtocolRemotely(CONVERSATION_ID, protocol)

        // then
        with(result) {
            shouldSucceed()
            coVerify {
                arrange.conversationDAO.updateConversationProtocolAndCipherSuite(
                    eq(CONVERSATION_ID.toDao()),
                    eq(conversationResponse.value.groupId),
                    eq(protocol.toDao()),
                    eq(ConversationEntity.CipherSuite.fromTag(conversationResponse.value.mlsCipherSuiteTag))
                )
            }.wasInvoked(exactly = once)
        }
    }

    @Test
    fun givenChange_whenUpdatingProtocol_thenShouldFetchConversationDetails() = runTest {
        // given
        val protocol = Conversation.Protocol.MIXED
        val conversationResponse = NetworkResponse.Success(
            TestConversation.CONVERSATION_RESPONSE,
            emptyMap(),
            HttpStatusCode.OK.value
        )

        val (arrangement, conversationRepository) = Arrangement()
            .withUpdateProtocolResponse(UPDATE_PROTOCOL_SUCCESS)
            .withFetchConversationsDetails(conversationResponse)
            .withDaoUpdateProtocolSuccess()
            .arrange()

        // when
        val result = conversationRepository.updateProtocolRemotely(CONVERSATION_ID, protocol)

        // then
        with(result) {
            shouldSucceed()
            coVerify {
                arrangement.conversationApi.fetchConversationDetails(eq(CONVERSATION_ID.toApi()))
            }.wasInvoked(exactly = once)
        }
    }

    @Test
    fun givenChange_whenUpdatingProtocol_thenShouldUpdateLocally() = runTest {
        // given
        val protocol = Conversation.Protocol.MLS
        val conversationResponse = NetworkResponse.Success(
            TestConversation.CONVERSATION_RESPONSE,
            emptyMap(),
            HttpStatusCode.OK.value
        )

        val (arrange, conversationRepository) = Arrangement()
            .withUpdateProtocolResponse(UPDATE_PROTOCOL_SUCCESS)
            .withFetchConversationsDetails(conversationResponse)
            .withDaoUpdateProtocolSuccess()
            .arrange()

        // when
        val result = conversationRepository.updateProtocolRemotely(CONVERSATION_ID, protocol)

        // then
        with(result) {
            shouldSucceed()
            coVerify {
                arrange.conversationDAO.updateConversationProtocolAndCipherSuite(
                    eq(CONVERSATION_ID.toDao()),
                    eq(conversationResponse.value.groupId),
                    eq(protocol.toDao()),
                    eq(ConversationEntity.CipherSuite.fromTag(conversationResponse.value.mlsCipherSuiteTag))
                )
            }.wasInvoked(exactly = once)
        }
    }

    @Test
    fun givenSuccessFetchingConversationDetails_whenUpdatingProtocolLocally_thenShouldUpdateLocally() = runTest {
        // given
        val protocol = Conversation.Protocol.MLS
        val conversationResponse = NetworkResponse.Success(
            TestConversation.CONVERSATION_RESPONSE,
            emptyMap(),
            HttpStatusCode.OK.value
        )

        val (arrange, conversationRepository) = Arrangement()
            .withFetchConversationsDetails(conversationResponse)
            .withDaoUpdateProtocolSuccess()
            .arrange()

        // when
        val result = conversationRepository.updateProtocolLocally(CONVERSATION_ID, protocol)

        // then
        with(result) {
            shouldSucceed()
            coVerify {
                arrange.conversationDAO.updateConversationProtocolAndCipherSuite(
                    eq(CONVERSATION_ID.toDao()),
                    eq(conversationResponse.value.groupId),
                    eq(protocol.toDao()),
                    eq(ConversationEntity.CipherSuite.fromTag(conversationResponse.value.mlsCipherSuiteTag))
                )
            }.wasInvoked(exactly = once)
        }
    }

    @Test
    fun givenFailureFetchingConversationDetails_whenUpdatingProtocolLocally_thenShouldNotUpdateLocally() = runTest {
        // given
        val protocol = Conversation.Protocol.MLS
        val (arrange, conversationRepository) = Arrangement()
            .withFetchConversationsDetails(NetworkResponse.Error(KaliumException.NoNetwork()))
            .withDaoUpdateProtocolSuccess()
            .arrange()

        // when
        val result = conversationRepository.updateProtocolLocally(CONVERSATION_ID, protocol)

        // then
        with(result) {
            shouldFail()
            coVerify { arrange.conversationDAO.updateConversationProtocolAndCipherSuite(any(), any(), any(), any()) }
                .wasNotInvoked()
        }
    }

    @Test
    fun givenLegalHoldStatus_whenUpdateIsCalled_thenInvokeUpdateLegalHoldStatusOnce() = runTest {
        // given
        val (arrange, conversationRepository) = Arrangement()
            .withUpdateLegalHoldStatus(true)
            .withUpdateLegalHoldStatusChangeNotified(true)
            .arrange()
        // when
        conversationRepository.updateLegalHoldStatus(CONVERSATION_ID, Conversation.LegalHoldStatus.ENABLED)
        // then
        coVerify {
            arrange.conversationDAO.updateLegalHoldStatus(eq(CONVERSATION_ID.toDao()), any())
        }.wasInvoked(exactly = once)
    }

    @Test
    fun givenLegalHoldStatusUpdated_whenUpdateChangeNotifiedIsCalled_thenInvokeUpdateLegalHoldStatusChangeNotifiedOnce() = runTest {
        // given
        val (arrange, conversationRepository) = Arrangement()
            .withUpdateLegalHoldStatus(true)
            .withUpdateLegalHoldStatusChangeNotified(true)
            .arrange()
        // when
        conversationRepository.updateLegalHoldStatus(CONVERSATION_ID, Conversation.LegalHoldStatus.ENABLED)
        // then
        coVerify {
            arrange.conversationDAO.updateLegalHoldStatusChangeNotified(eq(CONVERSATION_ID.toDao()), any())
        }.wasInvoked(exactly = once)
    }

    @Test
    fun givenLegalHoldStatusNotUpdated_whenUpdateChangeNotifiedIsCalled_thenDoNotInvokeUpdateLegalHoldStatusChangeNotified() = runTest {
        // given
        val (arrange, conversationRepository) = Arrangement()
            .withUpdateLegalHoldStatus(false)
            .withUpdateLegalHoldStatusChangeNotified(false)
            .arrange()
        // when
        conversationRepository.updateLegalHoldStatus(CONVERSATION_ID, Conversation.LegalHoldStatus.ENABLED)
        // then
        coVerify {
            arrange.conversationDAO.updateLegalHoldStatusChangeNotified(eq(CONVERSATION_ID.toDao()), any())
        }.wasNotInvoked()
    }

    @Test
    fun givenConversationId_whenObservingLegalHoldStatus_thenInvokeObserveLegalHoldStatusOnce() = runTest {
        val (arrange, conversationRepository) = Arrangement()
            .withObserveLegalHoldStatus()
            .arrange()

        conversationRepository.observeLegalHoldStatus(CONVERSATION_ID)

        coVerify {
            arrange.conversationDAO.observeLegalHoldStatus(eq(CONVERSATION_ID.toDao()))
        }.wasInvoked(exactly = once)
    }

    @Test
    fun givenConversationId_whenObservingLegalHoldStatusChangeNotified_thenInvokeObserveLegalHoldStatusChangeNotifiedOnce() = runTest {
        val (arrange, conversationRepository) = Arrangement()
            .withObserveLegalHoldStatusChangeNotified()
            .arrange()

        conversationRepository.observeLegalHoldStatusChangeNotified(CONVERSATION_ID)

        coVerify {
            arrange.conversationDAO.observeLegalHoldStatusChangeNotified(eq(CONVERSATION_ID.toDao()))
        }.wasInvoked(exactly = once)
    }

    @Test
    fun givenAPIError_whenUpdateChannelAddPermissionIsCalled_thenDoNothing() = runTest {
        val error = NetworkResponse.Error(TestNetworkException.generic)

        val (arrange, conversationRepository) = Arrangement()
            .withUpdateChannelAddPermissionRemotelyReturning(error)
            .arrange()

        val result = conversationRepository.updateChannelAddPermission(
            CONVERSATION_ID,
            ConversationDetails.Group.Channel.ChannelAddPermission.ADMINS
        )

        coVerify {
            arrange.conversationDAO.updateChannelAddPermission(any(), any())
        }.wasNotInvoked()
        assertTrue { result.isLeft() }
    }

    @Test
    fun givenPermissionUnchanged_whenUpdateChannelAddPermissionIsCalled_thenDoNothing() = runTest {
        val permissionUnchanged =
            NetworkResponse.Success(value = UpdateChannelAddPermissionResponse.PermissionUnchanged, mapOf(), HttpStatusCode.OK.value)

        val (arrange, conversationRepository) = Arrangement()
            .withUpdateChannelAddPermissionRemotelyReturning(permissionUnchanged)
            .arrange()

        val result = conversationRepository.updateChannelAddPermission(
            CONVERSATION_ID,
            ConversationDetails.Group.Channel.ChannelAddPermission.ADMINS
        )

        coVerify {
            arrange.conversationDAO.updateChannelAddPermission(any(), any())
        }.wasNotInvoked()
        assertTrue { result.isRight() }
    }

    @Test
    fun givenPermissionChanged_whenUpdateChannelAddPermissionIsCalled_thenUpdateStateLocally() = runTest {
        val permissionUpdated = NetworkResponse.Success(
            value = UpdateChannelAddPermissionResponse.PermissionUpdated(
                EventContentDTO.Conversation.ChannelAddPermissionUpdate(
                    "conversationId",
                    com.wire.kalium.network.api.model.ConversationId("conversationId", "domain"),
                    ChannelAddPermissionDTO(ChannelAddPermissionTypeDTO.ADMINS),
                    from = "userId",
                    qualifiedFrom = com.wire.kalium.network.api.model.UserId("from_id", "from_domain"),
                    time = Clock.System.now()
                )
            ), mapOf(), HttpStatusCode.OK.value
        )

        val (arrange, conversationRepository) = Arrangement()
            .withUpdateChannelAddPermissionRemotelyReturning(permissionUpdated)
            .arrange()

        val result = conversationRepository.updateChannelAddPermission(
            CONVERSATION_ID,
            ConversationDetails.Group.Channel.ChannelAddPermission.ADMINS
        )

        coVerify {
            arrange.conversationDAO.updateChannelAddPermission(any(), any())
        }.wasInvoked(exactly = once)
        assertTrue { result.isRight() }
    }

    private class Arrangement :
        MemberDAOArrangement by MemberDAOArrangementImpl() {

        val userRepository: UserRepository = mock(UserRepository::class)
        val selfTeamIdProvider: SelfTeamIdProvider = mock(SelfTeamIdProvider::class)
        val conversationDAO: ConversationDAO = mock(ConversationDAO::class)
        val conversationApi: ConversationApi = mock(ConversationApi::class)
        val clientDao: ClientDAO = mock(ClientDAO::class)
        private val clientApi = mock(ClientApi::class)
        private val messageDAO = mock(MessageDAO::class)
        private val messageDraftDAO = mock(MessageDraftDAO::class)
        val conversationMetaDataDAO: ConversationMetaDataDAO = mock(ConversationMetaDataDAO::class)
        val metadataDAO: MetadataDAO = mock(MetadataDAO::class)

        val conversationRepository =
            ConversationDataSource(
                TestUser.USER_ID,
                conversationDAO,
                memberDAO,
                conversationApi,
                messageDAO,
                messageDraftDAO,
                clientDao,
                clientApi,
                conversationMetaDataDAO,
                metadataDAO
            )

        suspend fun withSelfUserFlow(selfUserFlow: Flow<SelfUser>) = apply {
            coEvery {
                userRepository.observeSelfUser()
            }.returns(selfUserFlow)
        }

        suspend fun withExpectedOtherKnownUser(user: OtherUser) = apply {
            coEvery {
                userRepository.getKnownUser(any())
            }.returns(flowOf(user))
        }

        suspend fun withSelfUser(selfUser: SelfUser) = apply {
            coEvery {
                userRepository.getSelfUser()
            }.returns(selfUser.right())
        }

        suspend fun withFetchConversationsDetails(response: NetworkResponse<ConversationResponse>) = apply {
            coEvery {
                conversationApi.fetchConversationDetails(any())
            }.returns(response)
        }

        suspend fun withFetchConversationsIds(response: NetworkResponse<ConversationPagingResponse>) = apply {
            coEvery {
                conversationApi.fetchConversationsIds(null)
            }.returns(response)
        }

        suspend fun withFetchConversationsListDetails(
            predicate: (List<APIConversationId>) -> Boolean,
            response: NetworkResponse<ConversationResponseDTO>
        ) = apply {
            coEvery {
                conversationApi.fetchConversationsListDetails(matches { predicate(it) })
            }.returns(response)
        }

        suspend fun withExpectedConversationWithOtherUser(conversation: ConversationEntity?) = apply {
            coEvery {
                conversationDAO.observeOneOnOneConversationWithOtherUser(any())
            }.returns(flowOf(conversation))
        }

        suspend fun withExpectedConversationDetailsWithOtherUser(conversation: ConversationViewEntity?) = apply {
            coEvery {
                conversationDAO.observeOneOnOneConversationDetailsWithOtherUser(any())
            }.returns(flowOf(conversation))
        }

        suspend fun withUpdateConversationMemberStateResult(response: NetworkResponse<Unit>) = apply {
            coEvery {
                conversationApi.updateConversationMemberState(any(), any())
            }.returns(response)
        }

        suspend fun withConversationUnreadEvents(unreadEvents: List<ConversationUnreadEventEntity>) = apply {
            coEvery {
                messageDAO.observeConversationsUnreadEvents()
            }.returns(flowOf(unreadEvents))
        }

        suspend fun withUnreadArchivedConversationsCount(unreadCount: Long) = apply {
            coEvery {
                conversationDAO.observeUnreadArchivedConversationsCount()
            }.returns(flowOf(unreadCount))
        }

        suspend fun withUnreadMessageCounter(unreadCounter: Map<ConversationIDEntity, Int>) = apply {
            coEvery {
                messageDAO.observeUnreadMessageCounter()
            }.returns(flowOf(unreadCounter))
        }

        suspend fun withConversations(conversations: List<ConversationViewEntity>) = apply {
            coEvery {
                conversationDAO.getAllConversationDetails(any(), any())
            }.returns(flowOf(conversations))
        }

        suspend fun withLastMessages(messages: List<MessagePreviewEntity>) = apply {
            coEvery {
                messageDAO.observeLastMessages()
            }.returns(flowOf(messages))
        }

        suspend fun withMessageDrafts(messageDrafts: List<MessageDraftEntity>) = apply {
            coEvery {
                messageDraftDAO.observeMessageDrafts()
            }.returns(flowOf(messageDrafts))
        }

        suspend fun withUpdateConversationReadDateException(exception: Throwable) = apply {
            coEvery { conversationDAO.updateConversationReadDate(any(), any()) }
                .throws(exception)
        }

        suspend fun withApiUpdateAccessRoleReturns(response: NetworkResponse<UpdateConversationAccessResponse>) = apply {
            coEvery {
                conversationApi.updateAccess(any(), any())
            }.returns(response)
        }

        suspend fun withDaoUpdateProtocolSuccess() = apply {
            coEvery { conversationDAO.updateConversationProtocolAndCipherSuite(any(), any(), any(), any()) }
                .returns(true)
        }

        suspend fun withGetConversationProtocolInfoReturns(result: ConversationEntity.ProtocolInfo) = apply {
            coEvery {
                conversationDAO.getConversationProtocolInfo(any())
            }.returns(result)
        }

        suspend fun withApiUpdateConversationMemberRoleReturns(response: NetworkResponse<Unit>) = apply {
            coEvery {
                conversationApi.updateConversationMemberRole(any(), any(), any())
            }.returns(response)
        }

        suspend fun withDaoUpdateConversationMemberRoleSuccess() = apply {
            withUpdateMemberRoleSuccess()
        }

        suspend fun withSuccessfulConversationDeletion() = apply {
            coEvery {
                conversationDAO.deleteConversationByQualifiedID(any())
            }.returns(Unit)
        }

        suspend fun withExpectedIsUserMemberFlow(expectedIsUserMember: Flow<Boolean>) = apply {
            withObserveIsUserMember(expectedIsUserMember)
        }

        suspend fun withExpectedObservableConversationDetails(conversationEntity: ConversationViewEntity? = null) = apply {
            coEvery {
                conversationDAO.observeConversationDetailsById(any())
            }.returns(flowOf(conversationEntity))
        }

        suspend fun withExpectedObservableConversation(conversationEntity: ConversationEntity? = null) = apply {
            coEvery {
                conversationDAO.observeConversationById(any())
            }.returns(flowOf(conversationEntity))
        }

        suspend fun withConversationRecipientByUserSuccess(result: Map<UserIDEntity, List<ClientEntity>>) = apply {
            coEvery {
                clientDao.recipientsIfTheyArePartOfConversation(any(), any())
            }.returns(result)
        }

        suspend fun withExpectedConversation(conversationEntity: ConversationViewEntity?) = apply {
            coEvery {
                conversationDAO.getConversationDetailsById(any())
            }.returns(conversationEntity)
        }

        suspend fun withExpectedConversationView(conversationEntity: ConversationViewEntity?) = apply {
            coEvery {
                conversationDAO.observeConversationDetailsById(any())
            }.returns(flowOf(conversationEntity))
        }

        suspend fun withExpectedConversationBase(conversationEntity: ConversationEntity?) = apply {
            coEvery {
                conversationDAO.getConversationById(any())
            }.returns(conversationEntity)
        }

        suspend fun withFetchConversationDetailsResult(
            response: NetworkResponse<ConversationResponse>,
            idMatcher: Matcher<APIConversationId> = AnyMatcher(valueOf())
        ) = apply {
            coEvery {
                conversationApi.fetchConversationDetails(matches { idMatcher.matches(it) })
            }.returns(response)
        }

        suspend fun withWhoDeletedMe(deletionAuthor: UserId?) = apply {
            val author = deletionAuthor?.let { it.toDao() }
            coEvery {
                conversationDAO.whoDeletedMeInConversation(any(), any())
            }.returns(author)
        }

        suspend fun withConversationsByUserId(conversations: List<ConversationEntity>) = apply {
            coEvery {
                conversationDAO.getConversationsByUserId(any())
            }.returns(conversations)
        }

        suspend fun withConversationRenameCall(newName: String = "newName") = apply {
            coEvery {
                conversationDAO.updateConversationName(any(), eq(newName), any())
            }.returns(Unit)
        }

        suspend fun withConversationRenameApiCall(newName: String = "newName") = apply {
            coEvery {
                conversationApi.updateConversationName(any(), eq(newName))
            }.returns(NetworkResponse.Success(CONVERSATION_RENAME_RESPONSE, emptyMap(), HttpStatusCode.OK.value))
        }

        suspend fun withConversationRecipients(
            conversationIDEntity: ConversationIDEntity,
            result: Map<QualifiedIDEntity, List<ClientEntity>>
        ) =
            apply {
                coEvery {
                    clientDao.conversationRecipient(conversationIDEntity)
                }.returns(result)
            }

        suspend fun withUpdateReceiptModeSuccess(receiptMode: ReceiptMode) = apply {
            coEvery {
                conversationApi.updateReceiptMode(any(), eq(ConversationReceiptModeDTO(receiptMode)))
            }.returns(
                NetworkResponse.Success(
                    UpdateConversationReceiptModeResponse.ReceiptModeUpdated(
                        event = EventContentDTO.Conversation.ReceiptModeUpdate(
                            qualifiedConversation = CONVERSATION_ID.toApi(),
                            data = ConversationReceiptModeDTO(receiptMode = ReceiptMode.ENABLED),
                            qualifiedFrom = USER_ID.toApi()
                        )
                    ),
                    emptyMap(),
                    HttpStatusCode.OK.value
                )
            )
        }

        suspend fun withConversationsWithoutMetadataId(result: List<QualifiedIDEntity>) = apply {
            coEvery {
                conversationDAO.getConversationsWithoutMetadata()
            }.returns(result)
        }

        suspend fun withGetGroupConversationWithUserIdsWithBothDomains(
            result: Map<ConversationIDEntity, List<UserIDEntity>>,
            firstDomain: Matcher<String> = any(),
            secondDomain: Matcher<String> = any()
        ) = apply {
            coEvery {
                memberDAO.getGroupConversationWithUserIdsWithBothDomains(
                    matches { firstDomain.matches(it) },
                    matches { secondDomain.matches(it) }
                )
            }.returns(result)
        }

        suspend fun withGetOneOnOneConversationWithFederatedUserId(
            result: Map<ConversationIDEntity, UserIDEntity>,
            domain: Matcher<String> = any()
        ) = apply {
            coEvery {
                memberDAO.getOneOneConversationWithFederatedMembers(matches { domain.matches(it) })
            }.returns(result)
        }

        suspend fun withUpdateProtocolResponse(response: NetworkResponse<UpdateConversationProtocolResponse>) = apply {
            coEvery {
                conversationApi.updateProtocol(any(), any())
            }.returns(response)
        }

        suspend fun withObserveLegalHoldStatus() = apply {
            coEvery {
                conversationDAO.observeLegalHoldStatus(any())
            }.returns(flowOf(ConversationEntity.LegalHoldStatus.ENABLED))
        }

        suspend fun withObserveLegalHoldStatusChangeNotified() = apply {
            coEvery {
                conversationDAO.observeLegalHoldStatusChangeNotified(any())
            }.returns(flowOf(true))
        }

        suspend fun withUpdateChannelAddPermissionRemotelyReturning(result: NetworkResponse<UpdateChannelAddPermissionResponse>) = apply {
            coEvery {
                conversationApi.updateChannelAddPermission(any(), any())
            }.returns(result)
        }

        suspend fun withUpdateLegalHoldStatus(updated: Boolean) = apply {
            coEvery {
                conversationDAO.updateLegalHoldStatus(any(), any())
            }.returns(updated)
        }

        suspend fun withUpdateLegalHoldStatusChangeNotified(updated: Boolean) = apply {
            coEvery {
                conversationDAO.updateLegalHoldStatusChangeNotified(any(), any())
            }.returns(updated)
        }

        suspend fun arrange() = this to conversationRepository.also {
            coEvery { conversationDAO.insertConversations(any()) }
                .returns(Unit)

            withInsertMemberWithConversationIdSuccess()

            coEvery {
                conversationDAO.updateConversationMutedStatus(any(), any(), any())
            }.returns(Unit)

            coEvery {
                selfTeamIdProvider.invoke()
            }.returns(Either.Right(TestTeam.TEAM_ID))
        }
    }

    companion object {
        private const val RAW_GROUP_ID = "mlsGroupId"
        val GROUP_ID = GroupID(RAW_GROUP_ID)
        const val GROUP_NAME = "Group Name"

        val CONVERSATION_ID = TestConversation.ID
        val USER_ID = TestUser.USER_ID

        val CONVERSATION_ENTITY_ID = QualifiedIDEntity(CONVERSATION_ID.value, CONVERSATION_ID.domain)
        val USER_ENTITY_ID = QualifiedIDEntity(USER_ID.value, USER_ID.domain)

        val CONVERSATION_IDS_DTO_ONE =
            APIConversationId("someValue1", "someDomain1")

        val CONVERSATION_IDS_DTO_TWO =
            APIConversationId("someValue2", "someDomain2")

        val CONVERSATION_RESPONSE = ConversationResponse(
            "creator",
            ConversationMembersResponse(
                ConversationMemberDTO.Self(TestUser.SELF.id.toApi(), "wire_member"),
                emptyList()
            ),
            GROUP_NAME,
            TestConversation.NETWORK_ID,
            null,
            0UL,
            ConversationResponse.Type.GROUP,
            0,
            null,
            ConvProtocol.PROTEUS,
            lastEventTime = "2022-03-30T15:36:00.000Z",
            access = setOf(ConversationAccessDTO.INVITE, ConversationAccessDTO.CODE),
            accessRole = setOf(
                ConversationAccessRoleDTO.GUEST,
                ConversationAccessRoleDTO.TEAM_MEMBER,
                ConversationAccessRoleDTO.NON_TEAM_MEMBER
            ),
            mlsCipherSuiteTag = null,
            receiptMode = ReceiptMode.DISABLED
        )

        val CONVERSATION_RESPONSE_DTO = ConversationResponseDTO(
            conversationsFound = listOf(CONVERSATION_RESPONSE),
            conversationsFailed = listOf(APIConversationId("failedId", "someDomain")),
            conversationsNotFound = emptyList()
        )

        val MESSAGE_PREVIEW_ENTITY = MessagePreviewEntity(
            id = "some_id",
            conversationId = CONVERSATION_ENTITY_ID,
            content = MessagePreviewEntityContent.Text("sender", "Hey"),
            date = "2022-03-30T15:36:00.000Z",
            visibility = MessageEntity.Visibility.VISIBLE,
            isSelfMessage = false,
            senderUserId = USER_ENTITY_ID
        )

        private val TEST_QUALIFIED_ID_ENTITY = PersistenceQualifiedId("value", "domain")
        val OTHER_USER_ID = UserId("otherValue", "domain")

        val PROTEUS_PROTOCOL_INFO = ConversationEntity.ProtocolInfo.Proteus
        val MLS_PROTOCOL_INFO = ConversationEntity.ProtocolInfo.MLS(
            RAW_GROUP_ID,
            ConversationEntity.GroupState.ESTABLISHED,
            0UL,
            Clock.System.now(),
            ConversationEntity.CipherSuite.MLS_128_DHKEMX25519_AES128GCM_SHA256_Ed25519
        )

        private val CONVERSATION_RENAME_RESPONSE = ConversationRenameResponse.Changed(
            EventContentDTO.Conversation.ConversationRenameDTO(
                CONVERSATION_ID.toApi(),
                USER_ID.toApi(),
                Clock.System.now(),
                ConversationNameUpdateEvent("newName")
            )
        )

        val UPDATE_PROTOCOL_SUCCESS = NetworkResponse.Success(
            UpdateConversationProtocolResponse.ProtocolUpdated(
                EventContentDTO.Conversation.ProtocolUpdate(
                    TestConversation.NETWORK_ID,
                    ConversationProtocolDTO(ConvProtocol.MIXED),
                    TestUser.NETWORK_ID
                )
            ), emptyMap(), 200
        )
        val UPDATE_PROTOCOL_UNCHANGED = NetworkResponse.Success(
            UpdateConversationProtocolResponse.ProtocolUnchanged,
            emptyMap(), 204
        )
    }
}
