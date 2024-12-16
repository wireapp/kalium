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
import com.wire.kalium.cryptography.MLSClient
import com.wire.kalium.logic.MLSFailure
import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.data.client.MLSClientProvider
import com.wire.kalium.logic.data.event.Event
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.GroupID
import com.wire.kalium.logic.data.id.PersistenceQualifiedId
import com.wire.kalium.logic.data.id.QualifiedID
import com.wire.kalium.logic.data.id.SelfTeamIdProvider
import com.wire.kalium.logic.data.id.TeamId
import com.wire.kalium.logic.data.id.toApi
import com.wire.kalium.logic.data.id.toCrypto
import com.wire.kalium.logic.data.id.toDao
import com.wire.kalium.logic.data.id.toModel
import com.wire.kalium.logic.data.message.UnreadEventType
import com.wire.kalium.logic.data.user.OtherUser
import com.wire.kalium.logic.data.user.SelfUser
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.logic.framework.TestTeam
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.sync.receiver.conversation.RenamedConversationEventHandler
import com.wire.kalium.logic.util.arrangement.dao.MemberDAOArrangement
import com.wire.kalium.logic.util.arrangement.dao.MemberDAOArrangementImpl
import com.wire.kalium.logic.util.shouldFail
import com.wire.kalium.logic.util.shouldSucceed
import com.wire.kalium.network.api.base.authenticated.client.ClientApi
import com.wire.kalium.network.api.base.authenticated.conversation.ConvProtocol
import com.wire.kalium.network.api.base.authenticated.conversation.ConvProtocol.MLS
import com.wire.kalium.network.api.base.authenticated.conversation.ConversationApi
import com.wire.kalium.network.api.base.authenticated.conversation.ConversationMemberDTO
import com.wire.kalium.network.api.base.authenticated.conversation.ConversationMembersResponse
import com.wire.kalium.network.api.base.authenticated.conversation.ConversationNameUpdateEvent
import com.wire.kalium.network.api.base.authenticated.conversation.ConversationPagingResponse
import com.wire.kalium.network.api.base.authenticated.conversation.ConversationRenameResponse
import com.wire.kalium.network.api.base.authenticated.conversation.ConversationResponse
import com.wire.kalium.network.api.base.authenticated.conversation.ConversationResponseDTO
import com.wire.kalium.network.api.base.authenticated.conversation.ReceiptMode
import com.wire.kalium.network.api.base.authenticated.conversation.UpdateConversationAccessRequest
import com.wire.kalium.network.api.base.authenticated.conversation.UpdateConversationAccessResponse
import com.wire.kalium.network.api.base.authenticated.conversation.UpdateConversationProtocolResponse
import com.wire.kalium.network.api.base.authenticated.conversation.UpdateConversationReceiptModeResponse
import com.wire.kalium.network.api.base.authenticated.conversation.model.ConversationAccessInfoDTO
import com.wire.kalium.network.api.base.authenticated.conversation.model.ConversationMemberRoleDTO
import com.wire.kalium.network.api.base.authenticated.conversation.model.ConversationProtocolDTO
import com.wire.kalium.network.api.base.authenticated.conversation.model.ConversationReceiptModeDTO
import com.wire.kalium.network.api.base.authenticated.notification.EventContentDTO
import com.wire.kalium.network.api.base.model.ConversationAccessDTO
import com.wire.kalium.network.api.base.model.ConversationAccessRoleDTO
import com.wire.kalium.network.exceptions.KaliumException
import com.wire.kalium.network.utils.NetworkResponse
import com.wire.kalium.persistence.dao.ConversationIDEntity
import com.wire.kalium.persistence.dao.QualifiedIDEntity
import com.wire.kalium.persistence.dao.UserIDEntity
import com.wire.kalium.persistence.dao.client.ClientDAO
import com.wire.kalium.persistence.dao.client.ClientTypeEntity
import com.wire.kalium.persistence.dao.client.DeviceTypeEntity
import com.wire.kalium.persistence.dao.conversation.ConversationDAO
import com.wire.kalium.persistence.dao.conversation.ConversationEntity
import com.wire.kalium.persistence.dao.conversation.ConversationMetaDataDAO
import com.wire.kalium.persistence.dao.conversation.ConversationViewEntity
import com.wire.kalium.persistence.dao.message.MessageDAO
import com.wire.kalium.persistence.dao.message.MessageEntity
import com.wire.kalium.persistence.dao.message.MessagePreviewEntity
import com.wire.kalium.persistence.dao.message.MessagePreviewEntityContent
import com.wire.kalium.persistence.dao.unread.ConversationUnreadEventEntity
import com.wire.kalium.persistence.dao.unread.UnreadEventTypeEntity
import com.wire.kalium.util.DateTimeUtil
import io.ktor.http.HttpStatusCode
import io.mockative.Mock
import io.mockative.any
import io.mockative.anything
import io.mockative.configure
import io.mockative.eq
import io.mockative.given
import io.mockative.matchers.Matcher
import io.mockative.matching
import io.mockative.mock
import io.mockative.once
import io.mockative.thenDoNothing
import io.mockative.verify
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import com.wire.kalium.network.api.base.model.ConversationId as ConversationIdDTO
import com.wire.kalium.persistence.dao.client.Client as ClientEntity

@Suppress("LargeClass")
class ConversationRepositoryTest {

    @Test
    fun givenNewConversationEvent_whenCallingPersistConversation_thenConversationShouldBePersisted() = runTest {
        val event = Event.Conversation.NewConversation(
            "id",
            TestConversation.ID,
            TestUser.SELF.id,
            "time",
            CONVERSATION_RESPONSE
        )
        val selfUserFlow = flowOf(TestUser.SELF)
        val (arrangement, conversationRepository) = Arrangement()
            .withSelfUserFlow(selfUserFlow)
            .arrange()

        conversationRepository.persistConversations(listOf(event.conversation), TeamId("teamId"))

        with(arrangement) {
            verify(conversationDAO)
                .suspendFunction(conversationDAO::insertConversations)
                .with(
                    matching { conversations ->
                        conversations.any { entity -> entity.id.value == CONVERSATION_RESPONSE.id.value }
                    }
                )
                .wasInvoked(exactly = once)
        }
    }

    @Test
    fun givenNewConversationEvent_whenCallingPersistConversationFromEvent_thenConversationShouldBePersisted() =
        runTest {
            val event = Event.Conversation.NewConversation(
                "id",
                TestConversation.ID,
                TestUser.SELF.id,
                "time",
                CONVERSATION_RESPONSE
            )
            val selfUserFlow = flowOf(TestUser.SELF)
            val (arrangement, conversationRepository) = Arrangement()
                .withSelfUserFlow(selfUserFlow)
                .withExpectedConversationBase(null)
                .arrange()

            conversationRepository.persistConversation(event.conversation, "teamId")

            with(arrangement) {
                verify(conversationDAO)
                    .suspendFunction(conversationDAO::insertConversation)
                    .with(
                        matching { conversation ->
                            conversation.id.value == CONVERSATION_RESPONSE.id.value
                        }
                    )
                    .wasInvoked(exactly = once)
            }
        }

    @Test
    fun givenNewMLSConversationEvent_whenMLSIsDisabled_thenConversationShouldNotPersisted() =
        runTest {
            val event = Event.Conversation.NewConversation(
                "id",
                TestConversation.ID,
                TestUser.SELF.id,
                "time",
                CONVERSATION_RESPONSE.copy(
                    groupId = RAW_GROUP_ID,
                    protocol = MLS,
                    mlsCipherSuiteTag = ConversationEntity.CipherSuite.MLS_128_DHKEMX25519_AES128GCM_SHA256_Ed25519.cipherSuiteTag
                )
            )
            val selfUserFlow = flowOf(TestUser.SELF)
            val (arrangement, conversationRepository) = Arrangement()
                .withSelfUserFlow(selfUserFlow)
                .withExpectedConversationBase(null)
                .withDisabledMlsClientProvider()
                .withHasEstablishedMLSGroup(true)
                .arrange()

            conversationRepository.persistConversation(event.conversation, "teamId")

            verify(arrangement.conversationDAO)
                .suspendFunction(arrangement.conversationDAO::insertConversation)
                .with(
                    matching { conversation ->
                        conversation.id.value == CONVERSATION_RESPONSE.id.value
                    }
                )
                .wasNotInvoked()
        }

    @Test
    fun givenNewConversationEvent_whenCallingPersistConversationFromEventAndExists_thenConversationPersistenceShouldBeSkipped() =
        runTest {
            val event = Event.Conversation.NewConversation(
                "id",
                TestConversation.ID,
                TestUser.SELF.id,
                "time",
                CONVERSATION_RESPONSE
            )
            val selfUserFlow = flowOf(TestUser.SELF)
            val (arrangement, conversationRepository) = Arrangement()
                .withSelfUserFlow(selfUserFlow)
                .withExpectedConversationBase(TestConversation.ENTITY)
                .arrange()

            conversationRepository.persistConversation(event.conversation, "teamId")

            with(arrangement) {
                verify(conversationDAO)
                    .suspendFunction(conversationDAO::insertConversation)
                    .with(
                        matching { conversation ->
                            conversation.id.value == CONVERSATION_RESPONSE.id.value
                        }
                    )
                    .wasNotInvoked()
            }
        }

    @Test
    fun givenNewConversationEventWithMlsConversation_whenCallingInsertConversation_thenMlsGroupExistenceShouldBeQueried() =
        runTest {
            val event = Event.Conversation.NewConversation(
                "id",
                TestConversation.ID,
                TestUser.SELF.id,
                "time",
                CONVERSATION_RESPONSE.copy(
                    groupId = RAW_GROUP_ID,
                    protocol = MLS,
                    mlsCipherSuiteTag = ConversationEntity.CipherSuite.MLS_128_DHKEMX25519_AES128GCM_SHA256_Ed25519.cipherSuiteTag
                )
            )
            val protocolInfo = ConversationEntity.ProtocolInfo.MLS(
                RAW_GROUP_ID,
                ConversationEntity.GroupState.ESTABLISHED,
                0UL,
                Instant.parse("2021-03-30T15:36:00.000Z"),
                cipherSuite = ConversationEntity.CipherSuite.MLS_128_DHKEMX25519_AES128GCM_SHA256_Ed25519
            )

            val (arrangement, conversationRepository) = Arrangement()
                .withSelfUserFlow(flowOf(TestUser.SELF))
                .withHasEstablishedMLSGroup(true)
                .arrange()

            conversationRepository.persistConversations(
                listOf(event.conversation),
                TeamId("teamId"),
                originatedFromEvent = true
            )

            verify(arrangement.mlsClient)
                .suspendFunction(arrangement.mlsClient::conversationExists)
                .with(eq(RAW_GROUP_ID))
                .wasInvoked(once)

            verify(arrangement.conversationDAO)
                .suspendFunction(arrangement.conversationDAO::insertConversations)
                .with(
                    matching { conversations ->
                        conversations.any { entity ->
                            entity.id.value == CONVERSATION_RESPONSE.id.value && entity.protocolInfo == protocolInfo.copy(
                                keyingMaterialLastUpdate = (entity.protocolInfo as ConversationEntity.ProtocolInfo.MLS).keyingMaterialLastUpdate
                            )
                        }
                    }
                )
                .wasInvoked(once)
        }

    @Test
    fun givenTwoPagesOfConversation_whenFetchingConversationsAndItsDetails_thenThePagesShouldBeAddedAndPersistOnlyFounds() =
        runTest {
            // given
            val response =
                ConversationPagingResponse(listOf(CONVERSATION_IDS_DTO_ONE, CONVERSATION_IDS_DTO_TWO), false, "")

            val (arrangement, conversationRepository) = Arrangement()
                .withFetchConversationsIds(NetworkResponse.Success(response, emptyMap(), HttpStatusCode.OK.value))
                .withFetchConversationsListDetails(
                    { it.size == 2 },
                    NetworkResponse.Success(CONVERSATION_RESPONSE_DTO, emptyMap(), HttpStatusCode.OK.value)
                )
                .withSelfUserFlow(flowOf(TestUser.SELF))
                .arrange()

            // when
            conversationRepository.fetchConversations()

            // then
            verify(arrangement.conversationDAO)
                .suspendFunction(arrangement.conversationDAO::insertConversations)
                .with(
                    matching { conversations ->
                        conversations.any { entity ->
                            entity.id.value == CONVERSATION_RESPONSE_DTO.conversationsFailed.first().value
                        }
                    }
                ).wasInvoked(exactly = once)

            verify(arrangement.conversationDAO)
                .suspendFunction(arrangement.conversationDAO::insertConversations)
                .with(
                    matching { list ->
                        list.any {
                            it.id.value == CONVERSATION_RESPONSE.id.value
                        }
                    }
                )
                .wasInvoked(exactly = once)
        }

    @Test
    fun givenFetchingAConversation_whenFetchingAConnectionSentConversation_thenTheTypeShouldBePersistedAsPendingAlways() =
        runTest {
            // given
            val (arrangement, conversationRepository) = Arrangement()
                .withFetchConversationDetailsResult(
                    NetworkResponse.Success(
                        TestConversation.CONVERSATION_RESPONSE,
                        mapOf(),
                        HttpStatusCode.OK.value
                    )
                )
                .withSelfUserFlow(flowOf(TestUser.SELF))
                .arrange()

            // when
            conversationRepository.fetchSentConnectionConversation(TestConversation.ID)

            // then
            verify(arrangement.conversationDAO)
                .suspendFunction(arrangement.conversationDAO::insertConversations)
                .with(
                    matching { list ->
                        list.any {
                            it.type == ConversationEntity.Type.CONNECTION_PENDING
                        }
                    }
                )
                .wasInvoked(exactly = once)
        }

    @Test
    fun givenConversationDaoReturnsAGroupConversation_whenGettingConversationDetailsById_thenReturnAGroupConversationDetails() =
        runTest {
            val conversationEntity = TestConversation.VIEW_ENTITY.copy(type = ConversationEntity.Type.GROUP)

            val (_, conversationRepository) = Arrangement()
                .withExpectedObservableConversation(conversationEntity)
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
                .withExpectedObservableConversation(conversationEntity)
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
                .withExpectedObservableConversation(conversationEntity)
                .arrange()

            conversationRepository.observeConversationDetailsById(TestConversation.ID).test {
                assertIs<Either.Right<ConversationDetails.OneOne>>(awaitItem())
                awaitComplete()
            }
        }

    @Test
    fun givenUserHasKnownContactAndConversation_WhenGettingConversationDetailsByExistingConversation_ReturnTheCorrectConversation() =
        runTest {
            // given
            val (_, conversationRepository) = Arrangement()
                .withSelfUserFlow(flowOf(TestUser.SELF))
                .withExpectedConversationWithOtherUser(TestConversation.VIEW_ENTITY)
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
    fun whenCallingUpdateMutedStatusRemotely_thenShouldDelegateCallToConversationApi() = runTest {
        val (arrangement, conversationRepository) = Arrangement()
            .withUpdateConversationMemberStateResult(NetworkResponse.Success(Unit, mapOf(), HttpStatusCode.OK.value))
            .arrange()

        conversationRepository.updateMutedStatusRemotely(
            TestConversation.ID,
            MutedConversationStatus.AllMuted,
            DateTimeUtil.currentInstant().toEpochMilliseconds()
        )

        verify(arrangement.conversationApi)
            .suspendFunction(arrangement.conversationApi::updateConversationMemberState)
            .with(any(), any())
            .wasInvoked(exactly = once)

        verify(arrangement.conversationDAO)
            .suspendFunction(arrangement.conversationDAO::updateConversationMutedStatus)
            .with(any(), any(), any())
            .wasNotInvoked()
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

        verify(arrangement.conversationApi)
            .suspendFunction(arrangement.conversationApi::updateConversationMemberState)
            .with(any(), any())
            .wasInvoked(exactly = once)

        verify(arrangement.conversationDAO)
            .suspendFunction(arrangement.conversationDAO::updateConversationArchivedStatus)
            .with(any(), any(), any())
            .wasNotInvoked()
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

        verify(arrangement.conversationDAO)
            .suspendFunction(arrangement.conversationDAO::updateConversationMutedStatus)
            .with(any(), any(), any())
            .wasInvoked(exactly = once)

        verify(arrangement.conversationApi)
            .suspendFunction(arrangement.conversationApi::updateConversationMemberState)
            .with(any(), any())
            .wasNotInvoked()
    }

    @Test
    fun givenAConversationExists_whenFetchingConversationIfUnknown_thenShouldNotFetchFromApi() = runTest {
        val conversationId = TestConversation.ID
        val (arrangement, conversationRepository) = Arrangement()
            .withExpectedConversation(TestConversation.VIEW_ENTITY)
            .arrange()

        conversationRepository.fetchConversationIfUnknown(conversationId)

        verify(arrangement.conversationApi)
            .suspendFunction(arrangement.conversationApi::fetchConversationDetails)
            .with(eq(ConversationId(value = conversationId.value, domain = conversationId.domain)))
            .wasNotInvoked()
    }

    @Test
    fun givenAConversationExists_whenFetchingConversationIfUnknown_thenShouldSucceed() = runTest {
        val conversationId = TestConversation.ID
        val (_, conversationRepository) = Arrangement()
            .withExpectedConversation(TestConversation.VIEW_ENTITY)
            .arrange()

        conversationRepository.fetchConversationIfUnknown(conversationId)
            .shouldSucceed()
    }

    @Test
    fun givenAConversationDoesNotExist_whenFetchingConversationIfUnknown_thenShouldFetchFromAPI() = runTest {
        val conversationId = TestConversation.ID
        val conversationIdDTO = ConversationIdDTO(value = conversationId.value, domain = conversationId.domain)

        val (arrangement, conversationRepository) = Arrangement()
            .withExpectedConversation(null)
            .withSelfUser(TestUser.SELF)
            .withFetchConversationDetailsResult(
                NetworkResponse.Success(TestConversation.CONVERSATION_RESPONSE, mapOf(), HttpStatusCode.OK.value),
                eq(conversationIdDTO)
            )
            .arrange()

        conversationRepository.fetchConversationIfUnknown(conversationId)
            .shouldSucceed()

        verify(arrangement.conversationApi)
            .suspendFunction(arrangement.conversationApi::fetchConversationDetails)
            .with(eq(conversationIdDTO))
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenAConversationDoesNotExistAndAPISucceeds_whenFetchingConversationIfUnknown_thenShouldSucceed() = runTest {
        val conversationId = TestConversation.ID

        val (_, conversationRepository) = Arrangement()
            .withExpectedConversation(null)
            .withSelfUser(TestUser.SELF)
            .withFetchConversationDetailsResult(
                NetworkResponse.Success(TestConversation.CONVERSATION_RESPONSE, mapOf(), HttpStatusCode.OK.value),
                eq(ConversationIdDTO(value = conversationId.value, domain = conversationId.domain))
            )
            .arrange()

        conversationRepository.fetchConversationIfUnknown(conversationId)
            .shouldSucceed()
    }

    @Suppress("LongMethod")
    @Test
    fun givenUpdateAccessRoleSuccess_whenUpdatingConversationAccessInfo_thenTheNewAccessSettingsAreUpdatedLocally() =
        runTest {

            val conversationIdDTO = ConversationIdDTO("conv_id", "conv_domain")
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
                    qualifiedFrom = com.wire.kalium.network.api.base.model.UserId("from_id", "from_domain")
                )
            )

            val (arrange, conversationRepository) = Arrangement()
                .withApiUpdateAccessRoleReturns(NetworkResponse.Success(newAccess, mapOf(), 200))
                .withDaoUpdateAccessSuccess()
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
                verify(conversationApi)
                    .coroutine {
                        conversationApi.updateAccess(
                            conversationIdDTO,
                            UpdateConversationAccessRequest(
                                newAccessInfoDTO.access,
                                newAccessInfoDTO.accessRole
                            )
                        )
                    }
                    .wasInvoked(exactly = once)

                verify(conversationDAO)
                    .coroutine {
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
                    }
                    .wasInvoked(exactly = once)
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
                verify(conversationApi)
                    .coroutine {
                        conversationApi.updateConversationMemberRole(
                            conversationId.toApi(),
                            userId.toApi(),
                            ConversationMemberRoleDTO(MapperProvider.conversationRoleMapper().toApi(newRole))
                        )
                    }
                    .wasInvoked(exactly = once)

                verify(memberDAO)
                    .coroutine {
                        memberDAO.updateConversationMemberRole(
                            conversationId.toDao(),
                            userId.toDao(),
                            MapperProvider.conversationRoleMapper().toDAO(newRole)
                        )
                    }
                    .wasInvoked(exactly = once)
            }
        }

    @Test
    fun givenProteusConversation_WhenDeletingTheConversation_ThenShouldBeDeletedLocally() = runTest {
        val (arrangement, conversationRepository) = Arrangement()
            .withGetConversationProtocolInfoReturns(PROTEUS_PROTOCOL_INFO)
            .withSuccessfulConversationDeletion()
            .arrange()
        val conversationId = ConversationId("conv_id", "conv_domain")

        conversationRepository.deleteConversation(conversationId).shouldSucceed()

        with(arrangement) {
            verify(conversationDAO)
                .suspendFunction(conversationDAO::deleteConversationByQualifiedID)
                .with(eq(conversationId.toDao()))
                .wasInvoked(once)
        }
    }

    @Test
    fun givenMlsConversation_WhenDeletingTheConversation_ThenShouldBeDeletedLocally() = runTest {
        val (arrangement, conversationRepository) = Arrangement()
            .withGetConversationProtocolInfoReturns(MLS_PROTOCOL_INFO)
            .withSuccessfulConversationDeletion()
            .arrange()
        val conversationId = ConversationId("conv_id", "conv_domain")

        conversationRepository.deleteConversation(conversationId).shouldSucceed()

        with(arrangement) {
            verify(mlsClient)
                .function(mlsClient::wipeConversation)
                .with(eq(GROUP_ID.toCrypto()))
                .wasInvoked(once)
            verify(conversationDAO)
                .suspendFunction(conversationDAO::deleteConversationByQualifiedID)
                .with(eq(conversationId.toDao()))
                .wasInvoked(once)
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
            .arrange()

        // when
        conversationRepository.observeConversationListDetails(shouldFetchFromArchivedConversations).test {
            val result = awaitItem()

            assertContains(result.map { it.conversation.id }, conversationId)
            val conversation = result.first { it.conversation.id == conversationId }

            assertIs<ConversationDetails.Group>(conversation)
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
                .withConversationUnreadEvents(listOf(conversationUnreadEventEntity))
                .arrange()

            // when
            conversationRepository.observeConversationListDetails(shouldFetchFromArchivedConversations).test {
                val result = awaitItem()

                assertContains(result.map { it.conversation.id }, conversationId)
                val conversation = result.first { it.conversation.id == conversationId }

                assertIs<ConversationDetails.Group>(conversation)
                assertEquals(conversation.unreadEventCount[UnreadEventType.MESSAGE], unreadMessagesCount)
                assertEquals(null, conversation.lastMessage)

                awaitComplete()
            }
        }

    @Test
    fun givenAGroupConversationHasNotNewMessages_whenGettingConversationDetails_ThenReturnZeroUnreadMessageCount() = runTest {
        // given
        val conversationEntity = TestConversation.VIEW_ENTITY.copy(
            type = ConversationEntity.Type.GROUP,
        )
        val (_, conversationRepository) = Arrangement()
            .withExpectedObservableConversation(conversationEntity)
            .arrange()

        // when
        conversationRepository.observeConversationDetailsById(TestConversation.ID).test {
            // then
            val conversationDetail = awaitItem()

            assertIs<Either.Right<ConversationDetails.Group>>(conversationDetail)
            assertTrue { conversationDetail.value.lastMessage == null }

            awaitComplete()
        }
    }

    @Test
    fun givenAOneToOneConversationHasNotNewMessages_whenGettingConversationDetails_ThenReturnZeroUnreadMessageCount() =
        runTest {
            // given
            val conversationEntity = TestConversation.VIEW_ENTITY.copy(
                type = ConversationEntity.Type.ONE_ON_ONE,
                otherUserId = QualifiedIDEntity("otherUser", "domain")
            )

            val (_, conversationRepository) = Arrangement()
                .withExpectedObservableConversation(conversationEntity)
                .arrange()

            // when
            conversationRepository.observeConversationDetailsById(TestConversation.ID).test {
                // then
                val conversationDetail = awaitItem()

                assertIs<Either.Right<ConversationDetails.OneOne>>(conversationDetail)
                assertTrue { conversationDetail.value.lastMessage == null }

                awaitComplete()
            }
        }

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
            .withConversationUnreadEvents(listOf(conversationUnreadEventEntity))
            .arrange()

        // when
        conversationRepository.observeConversationListDetails(shouldFetchFromArchivedConversations).test {
            val result = awaitItem()

            assertContains(result.map { it.conversation.id }, conversationId)
            val conversation = result.first { it.conversation.id == conversationId }

            assertIs<ConversationDetails.OneOne>(conversation)
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
        verify(arrangement.conversationDAO)
            .suspendFunction(arrangement.conversationDAO::updateConversationReadDate)
            .with(anything(), anything())
            .wasInvoked(exactly = once)
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

            verify(arrangement.memberDAO)
                .suspendFunction(arrangement.memberDAO::observeIsUserMember)
                .with(eq(CONVERSATION_ENTITY_ID), eq(USER_ENTITY_ID))
                .wasInvoked(exactly = once)

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

            verify(arrangement.memberDAO)
                .suspendFunction(arrangement.memberDAO::observeIsUserMember)
                .with(eq(CONVERSATION_ENTITY_ID), eq(USER_ENTITY_ID))
                .wasInvoked(exactly = once)

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
            verify(conversationDAO)
                .suspendFunction(conversationDAO::whoDeletedMeInConversation)
                .with(any(), any())
                .wasInvoked(once)
        }
    }

    @Test
    fun givenAConversationId_WhenTheConversationDoesNotExists_ShouldReturnANullConversation() = runTest {
        val conversationId = ConversationId("conv_id", "conv_domain")
        val (_, conversationRepository) = Arrangement().withExpectedObservableConversation().arrange()

        val result = conversationRepository.getConversationById(conversationId)
        assertNull(result)
    }

    @Test
    fun givenAConversationId_WhenTheConversationExists_ShouldReturnAConversationInstance() = runTest {
        val conversationId = ConversationId("conv_id", "conv_domain")
        val (_, conversationRepository) = Arrangement().withExpectedObservableConversation(TestConversation.VIEW_ENTITY)
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
            verify(arrange.conversationDAO)
                .suspendFunction(arrange.conversationDAO::getConversationsByUserId)
                .with(any())
                .wasInvoked(exactly = once)
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
            verify(arrange.clientDao)
                .suspendFunction(arrange.clientDao::conversationRecipient)
                .with(any())
                .wasInvoked(exactly = once)
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
            verify(arrange.conversationDAO)
                .suspendFunction(arrange.conversationDAO::updateConversationReceiptMode)
                .with(any(), any())
                .wasInvoked(exactly = once)
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

        verify(arrangement.clientDao)
            .suspendFunction(arrangement.clientDao::recipientsIfTheyArePartOfConversation)
            .with(eq(conversationId), eq(setOf(user)))
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenAConversationWithoutMetadata_whenUpdatingMetadata_thenShouldUpdateLocally() = runTest {
        // given
        val (arrange, conversationRepository) = Arrangement()
            .withConversationsWithoutMetadataId(listOf(CONVERSATION_ENTITY_ID))
            .withFetchConversationsListDetails(
                { it.size == 1 },
                NetworkResponse.Success(CONVERSATION_RESPONSE_DTO, emptyMap(), HttpStatusCode.OK.value)
            )
            .arrange()

        // when
        val result = conversationRepository.syncConversationsWithoutMetadata()

        // then
        with(result) {
            shouldSucceed()
            verify(arrange.conversationDAO)
                .suspendFunction(arrange.conversationDAO::getConversationsWithoutMetadata)
                .wasInvoked(exactly = once)

            verify(arrange.conversationApi)
                .suspendFunction(arrange.conversationApi::fetchConversationsListDetails)
                .with(matching {
                    it.first() == CONVERSATION_ID.toApi()
                })
                .wasInvoked(exactly = once)

            verify(arrange.conversationDAO)
                .suspendFunction(arrange.conversationDAO::insertConversations)
                .with(matching {
                    it.first().id.value == CONVERSATION_RESPONSE.id.value
                })
                .wasInvoked(exactly = once)
        }
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
                eq(selfDomain),
                eq(federatedDomain)
            )
            .withGetOneOnOneConversationWithFederatedUserId(
                mapOf(oneOnOneConversationId to federatedUserId),
                eq(federatedDomain)
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

        verify(arrangement.memberDAO)
            .suspendFunction(arrangement.memberDAO::getGroupConversationWithUserIdsWithBothDomains)
            .with(any(), any())
            .wasInvoked(exactly = once)
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
    fun givenNoChange_whenUpdatingProtocolToMls_thenShouldNotUpdateLocally() = runTest {
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
            verify(arrange.conversationDAO)
                .suspendFunction(arrange.conversationDAO::updateConversationProtocolAndCipherSuite)
                .with(any(), any(), any(), any())
                .wasNotInvoked()
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
            verify(arrangement.conversationApi)
                .suspendFunction(arrangement.conversationApi::fetchConversationDetails)
                .with(eq(CONVERSATION_ID.toApi()))
                .wasInvoked(exactly = once)
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
            verify(arrange.conversationDAO)
                .suspendFunction(arrange.conversationDAO::updateConversationProtocolAndCipherSuite)
                .with(
                    eq(CONVERSATION_ID.toDao()),
                    eq(conversationResponse.value.groupId),
                    eq(protocol.toDao()),
                    eq(ConversationEntity.CipherSuite.fromTag(conversationResponse.value.mlsCipherSuiteTag))
                )
                .wasInvoked(exactly = once)
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
            verify(arrange.conversationDAO)
                .suspendFunction(arrange.conversationDAO::updateConversationProtocolAndCipherSuite)
                .with(
                    eq(CONVERSATION_ID.toDao()),
                    eq(conversationResponse.value.groupId),
                    eq(protocol.toDao()),
                    eq(ConversationEntity.CipherSuite.fromTag(conversationResponse.value.mlsCipherSuiteTag))
                )
                .wasInvoked(exactly = once)
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
            verify(arrange.conversationDAO)
                .suspendFunction(arrange.conversationDAO::updateConversationProtocolAndCipherSuite)
                .with(any(), any(), any(), any())
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
        verify(arrange.conversationDAO)
            .suspendFunction(arrange.conversationDAO::updateLegalHoldStatus)
            .with(eq(CONVERSATION_ID.toDao()), any())
            .wasInvoked(exactly = once)
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
        verify(arrange.conversationDAO)
            .suspendFunction(arrange.conversationDAO::updateLegalHoldStatusChangeNotified)
            .with(eq(CONVERSATION_ID.toDao()), any())
            .wasInvoked(exactly = once)
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
        verify(arrange.conversationDAO)
            .suspendFunction(arrange.conversationDAO::updateLegalHoldStatusChangeNotified)
            .with(eq(CONVERSATION_ID.toDao()), any())
            .wasNotInvoked()
    }

    @Test
    fun givenConversationId_whenObservingLegalHoldStatus_thenInvokeObserveLegalHoldStatusOnce() = runTest {
        val (arrange, conversationRepository) = Arrangement()
            .withObserveLegalHoldStatus()
            .arrange()

        conversationRepository.observeLegalHoldStatus(CONVERSATION_ID)

        verify(arrange.conversationDAO)
            .suspendFunction(arrange.conversationDAO::observeLegalHoldStatus)
            .with(eq(CONVERSATION_ID.toDao()))
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenConversationId_whenObservingLegalHoldStatusChangeNotified_thenInvokeObserveLegalHoldStatusChangeNotifiedOnce() = runTest {
        val (arrange, conversationRepository) = Arrangement()
            .withObserveLegalHoldStatusChangeNotified()
            .arrange()

        conversationRepository.observeLegalHoldStatusChangeNotified(CONVERSATION_ID)

        verify(arrange.conversationDAO)
            .suspendFunction(arrange.conversationDAO::observeLegalHoldStatusChangeNotified)
            .with(eq(CONVERSATION_ID.toDao()))
            .wasInvoked(exactly = once)
    }

    private class Arrangement :
        MemberDAOArrangement by MemberDAOArrangementImpl() {

        @Mock
        val userRepository: UserRepository = mock(UserRepository::class)

        @Mock
        val mlsClient: MLSClient = mock(MLSClient::class)

        @Mock
        val mlsClientProvider: MLSClientProvider = mock(MLSClientProvider::class)

        @Mock
        val selfTeamIdProvider: SelfTeamIdProvider = mock(SelfTeamIdProvider::class)

        @Mock
        val conversationDAO: ConversationDAO = mock(ConversationDAO::class)

        @Mock
        val conversationApi: ConversationApi = mock(ConversationApi::class)

        @Mock
        val clientDao: ClientDAO = mock(ClientDAO::class)

        @Mock
        private val clientApi = mock(ClientApi::class)

        @Mock
        private val messageDAO = configure(mock(MessageDAO::class)) { stubsUnitByDefault = true }

        @Mock
        val conversationMetaDataDAO: ConversationMetaDataDAO = mock(ConversationMetaDataDAO::class)

        @Mock
        val renamedConversationEventHandler =
            configure(mock(RenamedConversationEventHandler::class)) { stubsUnitByDefault = true }

        val conversationRepository =
            ConversationDataSource(
                TestUser.USER_ID,
                mlsClientProvider,
                selfTeamIdProvider,
                conversationDAO,
                memberDAO,
                conversationApi,
                messageDAO,
                clientDao,
                clientApi,
                conversationMetaDataDAO
            )

        init {
            given(conversationDAO)
                .suspendFunction(conversationDAO::insertConversation)
                .whenInvokedWith(anything())
                .thenDoNothing()

            withInsertMemberWithConversationIdSuccess()

            given(conversationDAO)
                .suspendFunction(conversationDAO::updateConversationMutedStatus)
                .whenInvokedWith(any(), any(), any())
                .thenReturn(Unit)

            given(mlsClientProvider)
                .suspendFunction(mlsClientProvider::getMLSClient)
                .whenInvokedWith(anything())
                .thenReturn(Either.Right(mlsClient))

            given(selfTeamIdProvider)
                .suspendFunction(selfTeamIdProvider::invoke)
                .whenInvoked()
                .then { Either.Right(TestTeam.TEAM_ID) }
        }

        fun withHasEstablishedMLSGroup(isClient: Boolean) = apply {
            given(mlsClient)
                .suspendFunction(mlsClient::conversationExists)
                .whenInvokedWith(anything())
                .thenReturn(isClient)
        }

        fun withSelfUserFlow(selfUserFlow: Flow<SelfUser>) = apply {
            given(userRepository)
                .suspendFunction(userRepository::observeSelfUser)
                .whenInvoked()
                .thenReturn(selfUserFlow)
        }

        fun withExpectedOtherKnownUser(user: OtherUser) = apply {
            given(userRepository)
                .suspendFunction(userRepository::getKnownUser)
                .whenInvokedWith(any())
                .thenReturn(flowOf(user))
        }

        fun withSelfUser(selfUser: SelfUser) = apply {
            given(userRepository)
                .suspendFunction(userRepository::getSelfUser)
                .whenInvoked()
                .thenReturn(selfUser)
        }

        fun withFetchConversationsDetails(response: NetworkResponse<ConversationResponse>) = apply {
            given(conversationApi)
                .suspendFunction(conversationApi::fetchConversationDetails)
                .whenInvokedWith(any())
                .thenReturn(response)
        }

        fun withFetchConversationsIds(response: NetworkResponse<ConversationPagingResponse>) = apply {
            given(conversationApi)
                .suspendFunction(conversationApi::fetchConversationsIds)
                .whenInvokedWith(eq(null))
                .thenReturn(response)
        }

        fun withFetchConversationsListDetails(
            predicate: (List<com.wire.kalium.network.api.base.model.ConversationId>) -> Boolean,
            response: NetworkResponse<ConversationResponseDTO>
        ) = apply {
            given(conversationApi)
                .suspendFunction(conversationApi::fetchConversationsListDetails)
                .whenInvokedWith(matching(predicate))
                .thenReturn(response)
        }

        fun withExpectedConversationWithOtherUser(conversation: ConversationViewEntity?) = apply {
            given(conversationDAO)
                .suspendFunction(conversationDAO::observeOneOnOneConversationWithOtherUser)
                .whenInvokedWith(anything())
                .then { flowOf(conversation) }
        }

        fun withUpdateConversationMemberStateResult(response: NetworkResponse<Unit>) = apply {
            given(conversationApi)
                .suspendFunction(conversationApi::updateConversationMemberState)
                .whenInvokedWith(any(), any())
                .thenReturn(response)
        }

        fun withConversationUnreadEvents(unreadEvents: List<ConversationUnreadEventEntity>) = apply {
            given(messageDAO)
                .suspendFunction(messageDAO::observeConversationsUnreadEvents)
                .whenInvoked()
                .thenReturn(flowOf(unreadEvents))
        }

        fun withUnreadArchivedConversationsCount(unreadCount: Long) = apply {
            given(conversationDAO)
                .suspendFunction(conversationDAO::observeUnreadArchivedConversationsCount)
                .whenInvoked()
                .thenReturn(flowOf(unreadCount))
        }

        fun withUnreadMessageCounter(unreadCounter: Map<ConversationIDEntity, Int>) = apply {
            given(messageDAO)
                .suspendFunction(messageDAO::observeUnreadMessageCounter)
                .whenInvoked()
                .thenReturn(flowOf(unreadCounter))
        }

        fun withConversations(conversations: List<ConversationViewEntity>) = apply {
            given(conversationDAO)
                .suspendFunction(conversationDAO::getAllConversationDetails)
                .whenInvokedWith(any())
                .thenReturn(flowOf(conversations))
        }

        fun withLastMessages(messages: List<MessagePreviewEntity>) = apply {
            given(messageDAO)
                .suspendFunction(messageDAO::observeLastMessages)
                .whenInvoked()
                .thenReturn(flowOf(messages))
        }

        fun withUpdateConversationReadDateException(exception: Throwable) = apply {
            given(conversationDAO)
                .suspendFunction(conversationDAO::updateConversationReadDate)
                .whenInvokedWith(any(), any())
                .thenThrow(exception)
        }

        fun withApiUpdateAccessRoleReturns(response: NetworkResponse<UpdateConversationAccessResponse>) = apply {
            given(conversationApi)
                .suspendFunction(conversationApi::updateAccess)
                .whenInvokedWith(any(), any())
                .thenReturn(response)
        }

        fun withDaoUpdateAccessSuccess() = apply {
            given(conversationDAO)
                .suspendFunction(conversationDAO::updateAccess)
                .whenInvokedWith(any(), any(), any())
                .thenReturn(Unit)
        }

        fun withDaoUpdateProtocolSuccess() = apply {
            given(conversationDAO)
                .suspendFunction(conversationDAO::updateConversationProtocolAndCipherSuite)
                .whenInvokedWith(anything(), anything(), anything(), anything())
                .thenReturn(true)
        }

        fun withGetConversationProtocolInfoReturns(result: ConversationEntity.ProtocolInfo) = apply {
            given(conversationDAO)
                .suspendFunction(conversationDAO::getConversationProtocolInfo)
                .whenInvokedWith(any())
                .thenReturn(result)
        }

        fun withApiUpdateConversationMemberRoleReturns(response: NetworkResponse<Unit>) = apply {
            given(conversationApi)
                .suspendFunction(conversationApi::updateConversationMemberRole)
                .whenInvokedWith(any(), any(), any())
                .thenReturn(response)
        }

        fun withDaoUpdateConversationMemberRoleSuccess() = apply {
            withUpdateMemberRoleSuccess()
        }

        fun withSuccessfulConversationDeletion() = apply {
            given(conversationDAO)
                .suspendFunction(conversationDAO::deleteConversationByQualifiedID)
                .whenInvokedWith(any())
                .thenReturn(Unit)
        }

        fun withExpectedIsUserMemberFlow(expectedIsUserMember: Flow<Boolean>) = apply {
            withObserveIsUserMember(expectedIsUserMember)
        }

        fun withExpectedObservableConversation(conversationEntity: ConversationViewEntity? = null) = apply {
            given(conversationDAO)
                .suspendFunction(conversationDAO::observeGetConversationByQualifiedID)
                .whenInvokedWith(any())
                .thenReturn(flowOf(conversationEntity))
        }

        fun withConversationRecipientByUserSuccess(result: Map<UserIDEntity, List<ClientEntity>>) = apply {
            given(clientDao)
                .suspendFunction(clientDao::recipientsIfTheyArePartOfConversation)
                .whenInvokedWith(any(), any())
                .thenReturn(result)
        }

        fun withExpectedConversation(conversationEntity: ConversationViewEntity?) = apply {
            given(conversationDAO)
                .suspendFunction(conversationDAO::getConversationByQualifiedID)
                .whenInvokedWith(any())
                .thenReturn(conversationEntity)
        }

        fun withExpectedConversationBase(conversationEntity: ConversationEntity?) = apply {
            given(conversationDAO)
                .suspendFunction(conversationDAO::getConversationBaseInfoByQualifiedID)
                .whenInvokedWith(any())
                .thenReturn(conversationEntity)
        }

        fun withFetchConversationDetailsResult(
            response: NetworkResponse<ConversationResponse>,
            idMatcher: Matcher<ConversationIdDTO> = any()
        ) = apply {
            given(conversationApi)
                .suspendFunction(conversationApi::fetchConversationDetails)
                .whenInvokedWith(idMatcher)
                .thenReturn(response)
        }

        fun withWhoDeletedMe(deletionAuthor: UserId?) = apply {
            val author = deletionAuthor?.let { it.toDao() }
            given(conversationDAO)
                .suspendFunction(conversationDAO::whoDeletedMeInConversation)
                .whenInvokedWith(any(), any())
                .thenReturn(author)
        }

        fun withConversationsByUserId(conversations: List<ConversationEntity>) = apply {
            given(conversationDAO)
                .suspendFunction(conversationDAO::getConversationsByUserId)
                .whenInvokedWith(any())
                .thenReturn(conversations)
        }

        fun withConversationRenameCall(newName: String = "newName") = apply {
            given(conversationDAO)
                .suspendFunction(conversationDAO::updateConversationName)
                .whenInvokedWith(any(), eq(newName), any())
                .thenReturn(Unit)
        }

        fun withConversationRenameApiCall(newName: String = "newName") = apply {
            given(conversationApi)
                .suspendFunction(conversationApi::updateConversationName)
                .whenInvokedWith(any(), eq(newName))
                .thenReturn(NetworkResponse.Success(CONVERSATION_RENAME_RESPONSE, emptyMap(), HttpStatusCode.OK.value))
        }

        suspend fun withConversationRecipients(
            conversationIDEntity: ConversationIDEntity,
            result: Map<QualifiedIDEntity, List<ClientEntity>>
        ) =
            apply {
                given(clientDao)
                    .coroutine { clientDao.conversationRecipient(conversationIDEntity) }
                    .then { result }
            }

        fun withUpdateReceiptModeSuccess(receiptMode: ReceiptMode) = apply {
            given(conversationApi)
                .suspendFunction(conversationApi::updateReceiptMode)
                .whenInvokedWith(any(), eq(ConversationReceiptModeDTO(receiptMode)))
                .thenReturn(
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

        fun withConversationsWithoutMetadataId(result: List<QualifiedIDEntity>) = apply {
            given(conversationDAO)
                .suspendFunction(conversationDAO::getConversationsWithoutMetadata)
                .whenInvoked()
                .thenReturn(result)
        }

        fun withGetGroupConversationWithUserIdsWithBothDomains(
            result: Map<ConversationIDEntity, List<UserIDEntity>>,
            firstDomain: Matcher<String> = any(),
            secondDomain: Matcher<String> = any()
        ) = apply {
            given(memberDAO)
                .suspendFunction(memberDAO::getGroupConversationWithUserIdsWithBothDomains)
                .whenInvokedWith(firstDomain, secondDomain)
                .thenReturn(result)
        }

        fun withGetOneOnOneConversationWithFederatedUserId(
            result: Map<ConversationIDEntity, UserIDEntity>,
            domain: Matcher<String> = any()
        ) = apply {
            given(memberDAO)
                .suspendFunction(memberDAO::getOneOneConversationWithFederatedMembers)
                .whenInvokedWith(domain)
                .thenReturn(result)
        }

        fun withUpdateProtocolResponse(response: NetworkResponse<UpdateConversationProtocolResponse>) = apply {
            given(conversationApi)
                .suspendFunction(conversationApi::updateProtocol)
                .whenInvokedWith(any(), any())
                .thenReturn(response)
        }

        fun withObserveLegalHoldStatus() = apply {
            given(conversationDAO)
                .suspendFunction(conversationDAO::observeLegalHoldStatus)
                .whenInvokedWith(any())
                .thenReturn(flowOf(ConversationEntity.LegalHoldStatus.ENABLED))
        }

        fun withObserveLegalHoldStatusChangeNotified() = apply {
            given(conversationDAO)
                .suspendFunction(conversationDAO::observeLegalHoldStatusChangeNotified)
                .whenInvokedWith(any())
                .thenReturn(flowOf(true))
        }

        fun withUpdateLegalHoldStatus(updated: Boolean) = apply {
            given(conversationDAO)
                .suspendFunction(conversationDAO::updateLegalHoldStatus)
                .whenInvokedWith(any(), any())
                .thenReturn(updated)
        }

        fun withUpdateLegalHoldStatusChangeNotified(updated: Boolean) = apply {
            given(conversationDAO)
                .suspendFunction(conversationDAO::updateLegalHoldStatusChangeNotified)
                .whenInvokedWith(any(), any())
                .thenReturn(updated)
        }

        suspend fun withDisabledMlsClientProvider() = apply {
            given(mlsClientProvider)
                .suspendFunction(mlsClientProvider::getMLSClient)
                .whenInvokedWith(any())
                .thenReturn(Either.Left(MLSFailure.Disabled))
        }

        fun arrange() = this to conversationRepository
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
            ConversationIdDTO("someValue1", "someDomain1")

        val CONVERSATION_IDS_DTO_TWO =
            ConversationIdDTO("someValue2", "someDomain2")

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
            conversationsFailed = listOf(ConversationIdDTO("failedId", "someDomain")),
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
                DateTimeUtil.currentIsoDateTimeString(),
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
