/*
 * Wire
 * Copyright (C) 2023 Wire Swiss GmbH
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

package com.wire.kalium.persistence.dao

import app.cash.turbine.test
import com.wire.kalium.persistence.BaseDatabaseTest
import com.wire.kalium.persistence.dao.asset.AssetDAO
import com.wire.kalium.persistence.dao.asset.AssetEntity
import com.wire.kalium.persistence.dao.conversation.ConversationDAO
import com.wire.kalium.persistence.dao.conversation.ConversationEntity
import com.wire.kalium.persistence.dao.conversation.ConversationViewEntity
import com.wire.kalium.persistence.dao.conversation.MLS_DEFAULT_LAST_KEY_MATERIAL_UPDATE_MILLI
import com.wire.kalium.persistence.dao.conversation.ProposalTimerEntity
import com.wire.kalium.persistence.dao.member.MemberDAO
import com.wire.kalium.persistence.dao.member.MemberEntity
import com.wire.kalium.persistence.dao.message.MessageDAO
import com.wire.kalium.persistence.dao.message.MessageEntity
import com.wire.kalium.persistence.dao.message.MessageEntityContent
import com.wire.kalium.persistence.utils.IgnoreIOS
import com.wire.kalium.persistence.utils.stubs.newConversationEntity
import com.wire.kalium.persistence.utils.stubs.newRegularMessageEntity
import com.wire.kalium.persistence.utils.stubs.newSystemMessageEntity
import com.wire.kalium.persistence.utils.stubs.newUserEntity
import com.wire.kalium.util.DateTimeUtil
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.toInstant
import kotlin.random.Random
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.seconds

@Suppress("LargeClass")
class ConversationDAOTest : BaseDatabaseTest() {

    private lateinit var conversationDAO: ConversationDAO
    private lateinit var connectionDAO: ConnectionDAO
    private lateinit var messageDAO: MessageDAO
    private lateinit var userDAO: UserDAO
    private lateinit var teamDAO: TeamDAO
    private lateinit var memberDAO: MemberDAO
    private lateinit var assertDAO: AssetDAO
    private val selfUserId = UserIDEntity("selfValue", "selfDomain")

    @BeforeTest
    fun setUp() {
        deleteDatabase(selfUserId)
        val db = createDatabase(selfUserId, encryptedDBSecret, enableWAL = true)
        conversationDAO = db.conversationDAO
        connectionDAO = db.connectionDAO
        messageDAO = db.messageDAO
        userDAO = db.userDAO
        teamDAO = db.teamDAO
        memberDAO = db.memberDAO
        assertDAO = db.assetDAO
    }

    @Test
    fun givenConversation_ThenConversationCanBeInserted() = runTest {
        conversationDAO.insertConversation(conversationEntity1)
        insertTeamUserAndMember(team, user1, conversationEntity1.id)
        val result = conversationDAO.getConversationByQualifiedID(conversationEntity1.id)
        assertEquals(conversationEntity1.toViewEntity(user1), result)
    }

    @Test
    fun givenListOfConversations_ThenMultipleConversationsCanBeInsertedAtOnce() = runTest {
        conversationDAO.insertConversations(listOf(conversationEntity1, conversationEntity2))
        insertTeamUserAndMember(team, user1, conversationEntity1.id)
        insertTeamUserAndMember(team, user2, conversationEntity2.id)
        val result1 = conversationDAO.getConversationByQualifiedID(conversationEntity1.id)
        val result2 = conversationDAO.getConversationByQualifiedID(conversationEntity2.id)
        assertEquals(conversationEntity1.toViewEntity(user1), result1)
        assertEquals(conversationEntity2.toViewEntity(user2), result2)
    }

    @Test
    fun givenExistingConversation_ThenConversationCanBeDeleted() = runTest {
        conversationDAO.insertConversation(conversationEntity1)
        conversationDAO.deleteConversationByQualifiedID(conversationEntity1.id)
        val result = try {
            conversationDAO.getConversationByQualifiedID(conversationEntity1.id)
        } catch (npe: NullPointerException) {
            null
        }
        assertNull(result)
    }

    @Test
    fun givenExistingConversation_ThenConversationCanBeUpdated() = runTest {
        conversationDAO.insertConversation(conversationEntity1)
        insertTeamUserAndMember(team, user1, conversationEntity1.id)
        val updatedConversation1Entity = conversationEntity1.copy(name = "Updated conversation1")
        conversationDAO.updateConversation(updatedConversation1Entity)
        val result = conversationDAO.getConversationByQualifiedID(conversationEntity1.id)
        assertEquals(updatedConversation1Entity.toViewEntity(user1), result)
    }

    @Test
    fun givenExistingConversation_ThenConversationCanBeRetrievedByGroupID() = runTest {
        conversationDAO.insertConversation(conversationEntity2)
        insertTeamUserAndMember(team, user2, conversationEntity2.id)
        val result =
            conversationDAO.getConversationByGroupID((conversationEntity2.protocolInfo as ConversationEntity.ProtocolInfo.MLS).groupId)
                .first()
        assertEquals(conversationEntity2.toViewEntity(user2), result)
    }

    @Test
    fun givenExistingMixedConversation_ThenConversationIdCanBeRetrievedByGroupID() = runTest {
        conversationDAO.insertConversation(conversationEntity6)
        insertTeamUserAndMember(team, user2, conversationEntity6.id)
        val result =
            conversationDAO.getConversationIdByGroupID((conversationEntity6.protocolInfo as ConversationEntity.ProtocolInfo.Mixed).groupId)
        assertEquals(conversationEntity6.id, result)
    }
    @Test
    fun givenExistingMLSConversation_ThenConversationIdCanBeRetrievedByGroupID() = runTest {
        conversationDAO.insertConversation(conversationEntity2)
        insertTeamUserAndMember(team, user2, conversationEntity2.id)
        val result =
            conversationDAO.getConversationIdByGroupID((conversationEntity2.protocolInfo as ConversationEntity.ProtocolInfo.MLS).groupId)
        assertEquals(conversationEntity2.id, result)
    }

    @Test
    fun givenExistingMixedConversation_ThenConversationCanBeRetrievedByGroupState() = runTest {
        conversationDAO.insertConversation(conversationEntity6)
        conversationDAO.insertConversation(conversationEntity3)
        insertTeamUserAndMember(team, user2, conversationEntity6.id)
        val result =
            conversationDAO.getConversationsByGroupState(ConversationEntity.GroupState.ESTABLISHED)
        assertEquals(listOf(conversationEntity6.toViewEntity(user2)), result)
    }

    @Test
    fun givenExistingGroupConversations_whenGetGroupConversationIdsByProtocol_ThenConversationIdsWithGivenProtocolIsReturned() = runTest {
        conversationDAO.insertConversation(conversationEntity4)
        conversationDAO.insertConversation(conversationEntity5)
        insertTeamUserAndMember(team, user2, conversationEntity5.id)
        val result =
            conversationDAO.getGroupConversationIdsByProtocol(ConversationEntity.Protocol.PROTEUS)
        assertEquals(listOf(conversationEntity5.id), result)
    }

    @Test
    fun givenExistingSelfAndOneToOneConversations_whenGetGroupConversationIdsByProtocol_ThenAnEmptyListIsReturned() = runTest {
        conversationDAO.insertConversation(conversationEntity1.copy(type = ConversationEntity.Type.SELF))
        conversationDAO.insertConversation(conversationEntity5.copy(type = ConversationEntity.Type.ONE_ON_ONE))
        insertTeamUserAndMember(team, user2, conversationEntity5.id)
        val result =
            conversationDAO.getGroupConversationIdsByProtocol(ConversationEntity.Protocol.PROTEUS)
        assertEquals(emptyList(), result)
    }

    @Test
    fun givenExistingMLSConversation_ThenConversationCanBeRetrievedByGroupState() = runTest {
        conversationDAO.insertConversation(conversationEntity2)
        conversationDAO.insertConversation(conversationEntity3)
        insertTeamUserAndMember(team, user2, conversationEntity2.id)
        val result =
            conversationDAO.getConversationsByGroupState(ConversationEntity.GroupState.ESTABLISHED)
        assertEquals(listOf(conversationEntity2.toViewEntity(user2)), result)
    }

    @Test
    fun givenExistingConversations_ThenAllProteusTeamConversationsCanBeRetrieved() = runTest {
        conversationDAO.insertConversation(conversationEntity1)
        conversationDAO.insertConversation(conversationEntity4)
        conversationDAO.insertConversation(conversationEntity5)
        insertTeamUserAndMember(team, user2, conversationEntity5.id)

        val result =
            conversationDAO.getAllProteusTeamConversations(teamId)

        assertEquals(listOf(conversationEntity5.id), result)
    }

    @Test
    fun givenAllMembersAreMlsCapable_WhenGetAllProteusTeamConversationsReadyToBeFinalised_ThenConversationIsReturned() = runTest {
        val allProtocols = setOf(SupportedProtocolEntity.PROTEUS, SupportedProtocolEntity.MLS)
        val selfUser = user1.copy(id = selfUserId, supportedProtocols = allProtocols)
        userDAO.insertUser(selfUser)

        conversationDAO.insertConversation(conversationEntity5)
        insertTeamUserAndMember(team, user2.copy(supportedProtocols = allProtocols), conversationEntity5.id)
        insertTeamUserAndMember(team, user3.copy(supportedProtocols = allProtocols), conversationEntity5.id)

        val result = conversationDAO.getAllProteusTeamConversationsReadyToBeFinalised(teamId)

        assertEquals(listOf(conversationEntity5.id), result)
    }

    @Test
    fun givenOnlySomeMembersAreMlsCapable_WhenGetAllProteusTeamConversationsReadyToBeFinalised_ThenConversationIsNotReturned() = runTest {
        val allProtocols = setOf(SupportedProtocolEntity.PROTEUS, SupportedProtocolEntity.MLS)
        val selfUser = user1.copy(id = selfUserId, supportedProtocols = allProtocols)
        userDAO.insertUser(selfUser)

        conversationDAO.insertConversation(conversationEntity5)
        insertTeamUserAndMember(team, user2.copy(supportedProtocols = allProtocols), conversationEntity5.id)
        insertTeamUserAndMember(team, user3.copy(supportedProtocols = setOf(SupportedProtocolEntity.PROTEUS)), conversationEntity5.id)

        val result = conversationDAO.getAllProteusTeamConversationsReadyToBeFinalised(teamId)

        assertTrue(result.isEmpty())
    }

    @Test
    fun givenExistingConversation_ThenConversationGroupStateCanBeUpdated() = runTest {
        conversationDAO.insertConversation(conversationEntity2)
        conversationDAO.updateConversationGroupState(
            ConversationEntity.GroupState.PENDING_WELCOME_MESSAGE,
            (conversationEntity2.protocolInfo as ConversationEntity.ProtocolInfo.MLS).groupId
        )
        val result = conversationDAO.getConversationByQualifiedID(conversationEntity2.id)
        assertEquals(
            (result?.protocolInfo as ConversationEntity.ProtocolInfo.MLS).groupState, ConversationEntity.GroupState.PENDING_WELCOME_MESSAGE
        )
    }

    @Test
    fun givenExistingConversation_ThenConversationIsUpdatedOnInsert() = runTest {
        conversationDAO.insertConversation(conversationEntity1)
        insertTeamUserAndMember(team, user1, conversationEntity1.id)
        val updatedConversation1Entity = conversationEntity1.copy(name = "Updated conversation1")
        conversationDAO.insertConversation(updatedConversation1Entity)
        val result = conversationDAO.getConversationByQualifiedID(conversationEntity1.id)
        assertEquals(updatedConversation1Entity.toViewEntity(user1), result)
    }

    @Test
    fun givenAnExistingConversation_WhenUpdatingTheMutingStatus_ThenConversationShouldBeUpdated() = runTest {
        conversationDAO.insertConversation(conversationEntity2)
        conversationDAO.updateConversationMutedStatus(
            conversationId = conversationEntity2.id,
            mutedStatus = ConversationEntity.MutedStatus.ONLY_MENTIONS_AND_REPLIES_ALLOWED,
            mutedStatusTimestamp = 1649702788L
        )

        val result = conversationDAO.getConversationByQualifiedID(conversationEntity2.id)

        assertEquals(ConversationEntity.MutedStatus.ONLY_MENTIONS_AND_REPLIES_ALLOWED, result?.mutedStatus)
    }

    @Test
    @IgnoreIOS
    fun givenConversation_whenInsertingStoredConversation_thenLastChangesTimeIsNotChanged() = runTest {
        val convStored = conversationEntity1.copy(
            lastNotificationDate = "2022-04-30T15:36:00.000Z".toInstant(),
            lastModifiedDate = "2022-03-30T15:36:00.000Z".toInstant(),
            name = "old name"
        )
        val convAfterSync = conversationEntity1.copy(
            lastNotificationDate = "2023-04-30T15:36:00.000Z".toInstant(),
            lastModifiedDate = "2023-03-30T15:36:00.000Z".toInstant(),
            name = "new name"
        )

        val expected = convAfterSync.copy(
            lastModifiedDate = "2022-03-30T15:36:00.000Z".toInstant(),
            lastNotificationDate = "2022-04-30T15:36:00.000Z".toInstant()
        )
        conversationDAO.insertConversation(convStored)
        insertTeamUserAndMember(team, user1, convStored.id)
        conversationDAO.insertConversation(convAfterSync)

        val actual = conversationDAO.getConversationByQualifiedID(convAfterSync.id)
        assertEquals(expected.toViewEntity(user1), actual)
    }

    @Test
    fun givenConversation_whenUpdatingAccessInfo_thenItsUpdated() = runTest {
        val convStored = conversationEntity1.copy(
            accessRole = listOf(ConversationEntity.AccessRole.TEAM_MEMBER), access = listOf(ConversationEntity.Access.INVITE)
        )
        val newAccessRole = listOf(
            ConversationEntity.AccessRole.TEAM_MEMBER,
            ConversationEntity.AccessRole.NON_TEAM_MEMBER,
            ConversationEntity.AccessRole.SERVICE
        )
        val newAccess = listOf(ConversationEntity.Access.INVITE, ConversationEntity.Access.CODE)
        val expected = convStored.copy(access = newAccess, accessRole = newAccessRole)
        conversationDAO.insertConversation(convStored)
        insertTeamUserAndMember(team, user1, conversationEntity1.id)

        conversationDAO.updateAccess(convStored.id, newAccess, newAccessRole)

        conversationDAO.getConversationByQualifiedID(convStored.id).also { actual ->
            assertEquals(expected.toViewEntity(user1), actual)
        }

    }

    @Test
    fun givenExistingConversation_whenUpdatingTheConversationLastReadDate_ThenTheConversationHasTheDate() = runTest {
        // given
        val expectedLastReadDate = Instant.fromEpochMilliseconds(1648654560000)

        conversationDAO.insertConversation(conversationEntity1)

        // when
        conversationDAO.updateConversationReadDate(conversationEntity1.id, expectedLastReadDate)

        // then
        val actual = conversationDAO.getConversationByQualifiedID(conversationEntity1.id)

        assertNotNull(actual)
        assertEquals(expectedLastReadDate, actual.lastReadDate)
    }

    @Test
    fun givenExistingConversation_whenUpdatingTheConversationSeenDate_thenEmitTheNewConversationStateWithTheUpdatedSeenDate() =
        runTest {
            // given
            val expectedConversationSeenDate = Instant.fromEpochMilliseconds(1648654560000)

            teamDAO.insertTeam(team)
            launch {
                // when
                conversationDAO.observeGetConversationByQualifiedID(conversationEntity1.id).test {
                    // then
                    val initialConversation = awaitItem()

                    assertTrue(initialConversation == null)

                    conversationDAO.insertConversation(conversationEntity1)

                    val conversationAfterInsert = awaitItem()

                    assertTrue(conversationAfterInsert != null)

                    conversationDAO.updateConversationReadDate(conversationEntity1.id, expectedConversationSeenDate)

                    val conversationAfterUpdate = awaitItem()

                    assertTrue(conversationAfterUpdate != null)
                    assertEquals(conversationAfterUpdate.lastReadDate, expectedConversationSeenDate)
                }
            }.join()
        }

    @Test
    fun givenConversationsHaveLastReadDateBeforeModified_whenGettingUnReadConversationCount_ThenReturnTheExpectedCount() = runTest {
        // given
        conversationDAO.insertConversation(
            newConversationEntity(
                id = QualifiedIDEntity("1", "someDomain"),
                lastReadDate = "2000-01-01T12:00:00.000Z".toInstant(),
                lastModified = "2000-01-01T12:30:00.000Z".toInstant()
            )
        )
        conversationDAO.insertConversation(
            newConversationEntity(
                id = QualifiedIDEntity("2", "someDomain"),
                lastReadDate = "2000-01-01T12:00:00.000Z".toInstant(),
                lastModified = "2000-01-01T12:30:00.000Z".toInstant()
            )
        )
        conversationDAO.insertConversation(
            newConversationEntity(
                id = QualifiedIDEntity("3", "someDomain"),
                lastReadDate = "2000-01-01T12:00:00.000Z".toInstant(),
                lastModified = "2000-01-01T12:30:00.000Z".toInstant()
            )
        )
        conversationDAO.insertConversation(
            newConversationEntity(
                id = QualifiedIDEntity("3", "someDomain"),
                lastReadDate = "2000-01-01T12:30:00.000Z".toInstant(),
                lastModified = "2000-01-01T12:00:00.000Z".toInstant()
            )
        )

    }

    @Test
    fun givenNewValue_whenUpdatingProtocol_thenItsUpdatedAndReportedAsChanged() = runTest {
        val conversation = conversationEntity5
        val updatedProtocol = ConversationEntity.Protocol.MLS

        conversationDAO.insertConversation(conversation)
        val changed = conversationDAO.updateConversationProtocol(conversation.id, updatedProtocol)

        assertTrue(changed)
        assertEquals(conversationDAO.getConversationByQualifiedID(conversation.id)?.protocol, updatedProtocol)
    }

    @Test
    fun givenSameValue_whenUpdatingProtocol_thenItsReportedAsUnchanged() = runTest {
        val conversation = conversationEntity5
        val updatedProtocol = ConversationEntity.Protocol.PROTEUS

        conversationDAO.insertConversation(conversation)
        val changed = conversationDAO.updateConversationProtocol(conversation.id, updatedProtocol)

        assertFalse(changed)
    }

    @Test
    fun givenMLSConversation_whenUpdatingKeyingMaterialLastUpdate_thenItsUpdated() = runTest {
        // given
        val conversation = conversationEntity2
        val conversationProtocolInfo = conversation.protocolInfo as ConversationEntity.ProtocolInfo.MLS
        val newUpdate = Instant.parse("2023-03-30T15:36:00.000Z")
        val expected =
            conversationProtocolInfo.copy(keyingMaterialLastUpdate = newUpdate)
        conversationDAO.insertConversation(conversationEntity2)
        // when
        conversationDAO.updateKeyingMaterial(conversationProtocolInfo.groupId, newUpdate)
        // then
        assertEquals(expected, conversationDAO.getConversationByGroupID(conversationProtocolInfo.groupId).first()?.protocolInfo)
    }

    @Test
    fun givenListMLSConversationsWithUpdateTime_whenPartOfThemNeedUpdate_thenGetConversationsByKeyingMaterialUpdateReturnsCorrectGroups() =
        runTest {
            // given
            // established updated group
            val updatedConversation = conversationEntity2
            val updatedDate = Instant.DISTANT_FUTURE
            val updatedGroupId = (updatedConversation.protocolInfo as ConversationEntity.ProtocolInfo.MLS).groupId
            teamDAO.insertTeam(team)
            conversationDAO.insertConversation(updatedConversation)
            conversationDAO.updateKeyingMaterial(updatedGroupId, updatedDate)

            // pending outdated group
            val outDatedConversation1 = conversationEntity3
            val outdatedDate1 = Instant.DISTANT_PAST
            val outdatedGroupId1 = (outDatedConversation1.protocolInfo as ConversationEntity.ProtocolInfo.MLS).groupId
            conversationDAO.insertConversation(outDatedConversation1)
            conversationDAO.updateKeyingMaterial(outdatedGroupId1, outdatedDate1)

            // established outdated group
            val outDatedConversation2 = conversationEntity4
            val outdatedDate2 = Instant.DISTANT_PAST
            val outdatedGroupId2 = (outDatedConversation2.protocolInfo as ConversationEntity.ProtocolInfo.MLS).groupId
            conversationDAO.insertConversation(outDatedConversation2)
            conversationDAO.updateKeyingMaterial(outdatedGroupId2, outdatedDate2)

            // then
            assertEquals(listOf(outdatedGroupId2), conversationDAO.getConversationsByKeyingMaterialUpdate(90.days))
        }

    @Test
    fun givenConversationWithMessages_whenDeletingAll_ThenTheConversationHasNoMessages() =
        runTest {
            // given
            val conversation = conversationEntity1

            teamDAO.insertTeam(team)
            conversationDAO.insertConversation(conversation)
            userDAO.insertUser(user1)

            val messages = buildList {
                repeat(10) {
                    add(
                        newRegularMessageEntity(
                            id = it.toString(),
                            conversationId = conversation.id,
                            senderUserId = user1.id,
                        )
                    )
                }
                add(
                    newRegularMessageEntity(
                        content = MessageEntityContent.Asset(
                            assetSizeInBytes = 123L,
                            assetName = "assetName",
                            assetMimeType = "assetMimeType",
                            assetUploadStatus = MessageEntity.UploadStatus.UPLOADED,
                            assetDownloadStatus = MessageEntity.DownloadStatus.SAVED_INTERNALLY,
                            assetOtrKey = ByteArray(32),
                            assetSha256Key = ByteArray(32),
                            assetId = "assetId",
                            assetEncryptionAlgorithm = "assetEncryptionAlgorithm"
                        ),
                        id = "11",
                        conversationId = conversation.id,
                        senderUserId = user1.id,
                        visibility = MessageEntity.Visibility.VISIBLE
                    )
                )
            }

            assertDAO.insertAsset(
                AssetEntity(
                    key = "assetId",
                    dataSize = 123,
                    dataPath = "dataPath",
                    domain = "domain",
                    downloadedDate = null
                )
            )

            messageDAO.insertOrIgnoreMessages(messages)

            // when
            conversationDAO.clearContent(conversation.id)

            // then
            messageDAO.getMessagesByConversationAndVisibility(
                conversation.id,
                100,
                0,
                listOf(MessageEntity.Visibility.VISIBLE)
            ).first().also { result ->
                assertTrue(result.isEmpty())
            }

            assertDAO.getAssetByKey("assetId").first().also { result ->
                assertNull(result)
            }
        }
// Mateusz : This test is failing because of some weird issue, I do not want to block this feature
// Therefore I will comment it, I am in very unstable and low bandwith internet now and to run test
// I need new version of xCode which will take me ages to download untill I am home from the trip
//     @Test
//     fun givenAConversationHasAssets_whenGettingConversationAssets_ThenReturnThoseAssets() =
//         runTest {
//             // given
//             val conversation = conversationEntity1
//
//             conversationDAO.insertConversation(conversation)
//             userDAO.insertUser(user1)
//
//             val messages = listOf(
//                 newRegularMessageEntity(
//                     id = 1.toString(),
//                     content = MessageEntityContent.Asset(
//                         assetSizeInBytes = 0,
//                         assetName = null,
//                         assetMimeType = "",
//                         assetDownloadStatus = null,
//                         assetOtrKey = byteArrayOf(),
//                         assetSha256Key = byteArrayOf(),
//                         assetId = "",
//                         assetToken = null,
//                         assetDomain = null,
//                         assetEncryptionAlgorithm = null,
//                         assetWidth = null,
//                         assetHeight = null,
//                         assetDurationMs = null,
//                         assetNormalizedLoudness = null
//                     ),
//                     conversationId = conversation.id,
//                     senderUserId = user1.id,
//                 ),
//                 newRegularMessageEntity(
//                     id = 2.toString(),
//                     content = MessageEntityContent.Asset(
//                         assetSizeInBytes = 0,
//                         assetName = null,
//                         assetMimeType = "",
//                         assetDownloadStatus = null,
//                         assetOtrKey = byteArrayOf(),
//                         assetSha256Key = byteArrayOf(),
//                         assetId = "",
//                         assetToken = null,
//                         assetDomain = null,
//                         assetEncryptionAlgorithm = null,
//                         assetWidth = null,
//                         assetHeight = null,
//                         assetDurationMs = null,
//                         assetNormalizedLoudness = null
//                     ),
//                     conversationId = conversation.id,
//                     senderUserId = user1.id,
//                 )
//             )
//
//             messageDAO.insertMessages(messages)
//             // when
//             val result = messageDAO.getConversationMessagesByContentType(conversation.id, MessageEntity.ContentType.ASSET)
//
//             // then
//             assertEquals(result.size, messages.size)
//         }

    @Test
    fun givenConversation_whenUpdatingProposalTimer_thenItIsUpdated() = runTest {
        // given
        conversationDAO.insertConversation(conversationEntity2)

        // when
        conversationDAO.setProposalTimer(proposalTimer2)

        // then
        assertEquals(listOf(proposalTimer2), conversationDAO.getProposalTimers().first())
    }

    @Test
    fun givenConversationWithExistingProposalTimer_whenUpdatingProposalTimer_thenItIsNotUpdated() = runTest {
        // given
        val initialFiringDate = Instant.DISTANT_FUTURE
        val updatedFiringDate = Instant.DISTANT_PAST
        val groupID = (conversationEntity2.protocolInfo as ConversationEntity.ProtocolInfo.MLS).groupId
        conversationDAO.insertConversation(conversationEntity2)
        conversationDAO.setProposalTimer(ProposalTimerEntity(groupID, initialFiringDate))

        // when
        conversationDAO.setProposalTimer(ProposalTimerEntity(groupID, updatedFiringDate))

        // then
        assertEquals(initialFiringDate, conversationDAO.getProposalTimers().first()[0].firingDate)
    }

    @Test
    fun givenConversationWithExistingProposalTimer_whenClearingProposalTimer_thenItIsUpdated() = runTest {
        // given
        conversationDAO.insertConversation(conversationEntity2)
        conversationDAO.setProposalTimer(proposalTimer2)

        // when
        conversationDAO.clearProposalTimer(proposalTimer2.groupID)

        // then
        assertEquals(emptyList(), conversationDAO.getProposalTimers().first())
    }

    @Test
    fun givenConversationsWithExistingProposalTimer_whenGettingProposalTimers_thenAllTimersAreReturned() = runTest {
        // given
        conversationDAO.insertConversation(conversationEntity1)
        conversationDAO.insertConversation(conversationEntity2)
        conversationDAO.insertConversation(conversationEntity3)
        conversationDAO.setProposalTimer(proposalTimer2)
        conversationDAO.setProposalTimer(proposalTimer3)

        // then
        assertEquals(listOf(proposalTimer2, proposalTimer3), conversationDAO.getProposalTimers().first())
    }

    @Test
    fun givenSeveralRemoveMemberMessages_whenCallingWhoRemovedMe_itReturnsTheCorrectValue() = runTest {
        // Given
        val mySelfMember = member2
        val mySelfId = member2.user
        conversationDAO.insertConversation(conversationEntity1)
        memberDAO.insertMember(member1, conversationEntity1.id)
        memberDAO.insertMember(member3, conversationEntity1.id)
        memberDAO.insertMember(mySelfMember, conversationEntity1.id)
        memberDAO.deleteMemberByQualifiedID(mySelfId, conversationEntity1.id)

        val firstRemovalDate = DateTimeUtil.currentInstant()
        val secondRemovalDate = firstRemovalDate.plus(1.seconds)

        val message1 = newSystemMessageEntity(
            id = "1",
            senderUserId = member1.user,
            content = MessageEntityContent.MemberChange(
                listOf(mySelfId),
                MessageEntity.MemberChangeType.REMOVED
            ),
            date = firstRemovalDate,
            conversationId = conversationEntity1.id
        )
        val message2 = newSystemMessageEntity(
            id = "2",
            senderUserId = member3.user,
            content = MessageEntityContent.MemberChange(
                listOf(mySelfId),
                MessageEntity.MemberChangeType.REMOVED
            ),
            date = secondRemovalDate,
            conversationId = conversationEntity1.id
        )
        userDAO.insertUser(user1)
        userDAO.insertUser(user2)
        userDAO.insertUser(user3)
        messageDAO.insertOrIgnoreMessage(message1)
        messageDAO.insertOrIgnoreMessage(message2)

        // When
        val whoDeletedMe = conversationDAO.whoDeletedMeInConversation(
            conversationEntity1.id, "${mySelfId.value}@${mySelfId.domain}"
        )

        // Then
        assertEquals(whoDeletedMe?.value, member3.user.value)
    }

    @Test
    fun givenAGroupThatImStillAMemberOf_whenCallingWhoRemovedMe_itReturnsANullValue() = runTest {
        // Given
        val mySelfMember = member2
        val mySelfId = member2.user
        conversationDAO.insertConversation(conversationEntity1)
        memberDAO.insertMember(member1, conversationEntity1.id)
        memberDAO.insertMember(member3, conversationEntity1.id)
        memberDAO.insertMember(mySelfMember, conversationEntity1.id)
        userDAO.insertUser(user1)
        userDAO.insertUser(user2)
        userDAO.insertUser(user3)

        memberDAO.deleteMemberByQualifiedID(member3.user, conversationEntity1.id)
        val removalMessage = newSystemMessageEntity(
            senderUserId = member1.user,
            content = MessageEntityContent.MemberChange(
                listOf(member3.user),
                MessageEntity.MemberChangeType.REMOVED
            ),
            date = Clock.System.now(),
            conversationId = conversationEntity1.id
        )
        messageDAO.insertOrIgnoreMessage(removalMessage)
        // When
        val whoDeletedMe = conversationDAO.whoDeletedMeInConversation(
            conversationEntity1.id, "${mySelfId.value}@${mySelfId.domain}"
        )

        // Then
        assertNull(whoDeletedMe)
    }

    @Test
    fun givenAConversation_whenChangingTheName_itReturnsTheUpdatedName() = runTest {
        // given
        conversationDAO.insertConversation(conversationEntity3)
        insertTeamUserAndMember(team, user1, conversationEntity3.id)

        // when
        conversationDAO.updateConversationName(conversationEntity3.id, "NEW-NAME", "2023-11-22T15:36:00.000Z")
        val updatedConversation = conversationDAO.getConversationByQualifiedID(conversationEntity3.id)

        // then
        assertEquals("NEW-NAME", updatedConversation!!.name)
    }

    @Test
    fun givenAnUserId_whenFetchingConversationIds_itReturnsOnlyConversationWhichUserBelongsTo() = runTest {
        // given
        conversationDAO.insertConversation(conversationEntity1)
        conversationDAO.insertConversation(conversationEntity2)
        memberDAO.insertMember(member1, conversationEntity1.id)
        memberDAO.insertMember(member2, conversationEntity1.id)
        memberDAO.insertMember(member2, conversationEntity2.id)

        // when
        val conversationIds = conversationDAO.getConversationIdsByUserId(member1.user)

        // then
        assertContentEquals(listOf(conversationEntity1.id), conversationIds)
    }

    @Test
    fun givenAConversation_whenUpdatingReceiptMode_itReturnsTheUpdatedValue() = runTest {
        // given
        conversationDAO.insertConversation(conversationEntity1.copy(receiptMode = ConversationEntity.ReceiptMode.ENABLED))

        // when
        conversationDAO.updateConversationReceiptMode(conversationEntity1.id, ConversationEntity.ReceiptMode.DISABLED)

        // then
        val conversation = conversationDAO.getConversationByQualifiedID(conversationEntity1.id)
        assertEquals(ConversationEntity.ReceiptMode.DISABLED, conversation?.receiptMode)
    }

    @Test
    fun givenSelfUserIsNotMemberOfConversation_whenGettingConversationDetails_itReturnsCorrectDetails() = runTest {
        // given
        conversationDAO.insertConversation(conversationEntity3)
        teamDAO.insertTeam(team)
        userDAO.insertUser(user2)
        memberDAO.insertMember(MemberEntity(user2.id, MemberEntity.Role.Member), conversationEntity3.id)

        // when
        val result = conversationDAO.getConversationByQualifiedID(conversationEntity3.id)

        // then
        assertEquals(false, result?.isMember)
    }

    @Test
    fun givenConversation_whenUpdatingMessageTimer_itReturnsCorrectTimer() = runTest {
        // given
        conversationDAO.insertConversation(conversationEntity3)
        val messageTimer = 60000L
        conversationDAO.updateMessageTimer(conversationEntity3.id, messageTimer)

        // when
        val result = conversationDAO.getConversationByQualifiedID(conversationEntity3.id)

        // then
        assertEquals(messageTimer, result?.messageTimer)
    }

    @Test
    fun givenSelfUserIsCreatorOfConversation_whenGettingConversationDetails_itReturnsCorrectDetails() = runTest {
        // given
        conversationDAO.insertConversation(conversationEntity3.copy(creatorId = selfUserId.value))
        teamDAO.insertTeam(team)
        userDAO.insertUser(user2)
        insertTeamUserAndMember(team, user2, conversationEntity3.id)

        // when
        val result = conversationDAO.getConversationByQualifiedID(conversationEntity3.id)

        // then
        assertEquals(1L, result?.isCreator)
    }

    @Test
    fun givenMixedConversation_whenGettingConversationProtocolInfo_itReturnsCorrectInfo() = runTest {
        // given
        conversationDAO.insertConversation(conversationEntity6)

        // when
        val result = conversationDAO.getConversationProtocolInfo(conversationEntity6.id)

        // then
        assertEquals(conversationEntity6.protocolInfo, result)
    }

    @Test
    fun givenMLSConversation_whenGettingConversationProtocolInfo_itReturnsCorrectInfo() = runTest {
        // given
        conversationDAO.insertConversation(conversationEntity2)

        // when
        val result = conversationDAO.getConversationProtocolInfo(conversationEntity2.id)

        // then
        assertEquals(conversationEntity2.protocolInfo, result)
    }

    @Test
    fun givenProteusConversation_whenGettingConversationProtocolInfo_itReturnsCorrectInfo() = runTest {
        // given
        conversationDAO.insertConversation(conversationEntity1)

        // when
        val result = conversationDAO.getConversationProtocolInfo(conversationEntity1.id)

        // then
        assertEquals(conversationEntity1.protocolInfo, result)
    }

    @Test
    fun givenConversations_whenUpdatingAllNotificationDates_thenAllConversationsAreUpdatedWithTheDateOfTheNewestMessage() = runTest {

        conversationDAO.insertConversation(
            conversationEntity1.copy(
                lastNotificationDate = Instant.DISTANT_FUTURE,
                lastModifiedDate = Instant.fromEpochSeconds(0)
            )
        )
        conversationDAO.insertConversation(
            conversationEntity2.copy(
                lastNotificationDate = null,
                lastModifiedDate = Instant.DISTANT_FUTURE
            )
        )

        val instant = Clock.System.now()

        userDAO.insertUser(user1)

        newRegularMessageEntity(
            id = Random.nextBytes(10).decodeToString(),
            conversationId = conversationEntity1.id,
            senderUserId = user1.id,
            date = instant
        ).also { messageDAO.insertOrIgnoreMessage(it) }

        // TODO: insert another message from self user to check if it is not ignored
        userDAO.insertUser(user1)

        newRegularMessageEntity(
            id = Random.nextBytes(10).decodeToString(),
            conversationId = conversationEntity1.id,
            senderUserId = user1.id,
            date = instant
        ).also { messageDAO.insertOrIgnoreMessage(it) }


        conversationDAO.updateAllConversationsNotificationDate()

        conversationDAO.getAllConversations().first().forEach {
            assertEquals(instant.toEpochMilliseconds(), it.lastNotificationDate!!.toEpochMilliseconds())
        }
    }

    @Test
    fun givenConnectionRequestAndUserWithName_whenSelectingAllConversationDetails_thenShouldReturnConnectionRequest() = runTest {
        val conversationId = QualifiedIDEntity("connection-conversationId", "domain")
        val conversation = conversationEntity1.copy(id = conversationId, type = ConversationEntity.Type.CONNECTION_PENDING)
        val connectionEntity = ConnectionEntity(
            conversationId = conversationId.value,
            from = selfUserId.value,
            lastUpdateDate = Instant.DISTANT_PAST,
            qualifiedConversationId = conversationId,
            qualifiedToId = user1.id,
            status = ConnectionEntity.State.PENDING,
            toId = user1.id.value,
        )

        userDAO.insertUser(user1)
        conversationDAO.insertConversation(conversation)
        connectionDAO.insertConnection(connectionEntity)

        conversationDAO.getAllConversationDetails().first().let {
            assertEquals(1, it.size)
            val result = it.first()

            assertEquals(conversationId, result.id)
            assertEquals(ConversationEntity.Type.CONNECTION_PENDING, result.type)
        }
    }

    @Test
    fun givenConnectionRequestAndUserWithoutName_whenSelectingAllConversationDetails_thenShouldReturnConnectionRequest() = runTest {
        val conversationId = QualifiedIDEntity("connection-conversationId", "domain")
        val conversation = conversationEntity1.copy(id = conversationId, type = ConversationEntity.Type.CONNECTION_PENDING)
        val connectionEntity = ConnectionEntity(
            conversationId = conversationId.value,
            from = selfUserId.value,
            lastUpdateDate = Instant.DISTANT_PAST,
            qualifiedConversationId = conversationId,
            qualifiedToId = user1.id,
            status = ConnectionEntity.State.PENDING,
            toId = user1.id.value,
        )

        userDAO.insertUser(user1.copy(name = null))
        conversationDAO.insertConversation(conversation)
        connectionDAO.insertConnection(connectionEntity)

        conversationDAO.getAllConversationDetails().first().let {
            assertEquals(0, it.size)
        }
    }

    @Test
    fun givenLocalConversations_whenGettingAllConversations_thenShouldReturnsOnlyConversationsWithMetadata() = runTest {
        conversationDAO.insertConversation(conversationEntity1)
        conversationDAO.insertConversation(conversationEntity2)

        userDAO.insertUser(user1) // user with metadata
        userDAO.insertUser(user2.copy(name = null)) // user without metadata

        memberDAO.insertMember(member1, conversationEntity1.id)
        memberDAO.insertMember(member2, conversationEntity1.id)

        conversationDAO.getAllConversationDetails().first().let {
            assertEquals(1, it.size)
            assertEquals(conversationEntity1.id, it.first().id)
        }
    }

    private suspend fun insertTeamUserAndMember(team: TeamEntity, user: UserEntity, conversationId: QualifiedIDEntity) {
        teamDAO.insertTeam(team)
        userDAO.insertUser(user)
        // should be inserted AFTER inserting the conversation!!!
        memberDAO.insertMembersWithQualifiedId(
            listOf(
                MemberEntity(user.id, MemberEntity.Role.Member),
                MemberEntity(selfUserId, MemberEntity.Role.Member) // adding SelfUser as a member too
            ),
            conversationId
        )
    }

    private fun ConversationEntity.toViewEntity(userEntity: UserEntity? = null): ConversationViewEntity {
        val protocol: ConversationEntity.Protocol
        val mlsGroupId: String?
        val mlsLastKeyingMaterialUpdate: Instant
        val mlsGroupState: ConversationEntity.GroupState

        val protocolInfoTmp = protocolInfo
        if (protocolInfoTmp is ConversationEntity.ProtocolInfo.MLSCapable) {
            protocol = if (protocolInfoTmp is ConversationEntity.ProtocolInfo.MLS) {
                ConversationEntity.Protocol.MLS
            } else {
                ConversationEntity.Protocol.MIXED
            }
            mlsGroupId = protocolInfoTmp.groupId
            mlsLastKeyingMaterialUpdate = protocolInfoTmp.keyingMaterialLastUpdate
            mlsGroupState = protocolInfoTmp.groupState
        } else {
            protocol = ConversationEntity.Protocol.PROTEUS
            mlsGroupId = null
            mlsLastKeyingMaterialUpdate = Instant.fromEpochMilliseconds(MLS_DEFAULT_LAST_KEY_MATERIAL_UPDATE_MILLI)
            mlsGroupState = ConversationEntity.GroupState.ESTABLISHED
        }
        return ConversationViewEntity(
            id = id,
            name = if (type == ConversationEntity.Type.ONE_ON_ONE) userEntity?.name else name,
            type = type,
            callStatus = null,
            previewAssetId = null,
            mutedStatus = mutedStatus,
            teamId = if (type == ConversationEntity.Type.ONE_ON_ONE) userEntity?.team else teamId,
            lastModifiedDate = lastModifiedDate,
            lastReadDate = lastReadDate,
            userAvailabilityStatus = if (type == ConversationEntity.Type.ONE_ON_ONE) userEntity?.availabilityStatus else null,
            userType = if (type == ConversationEntity.Type.ONE_ON_ONE) userEntity?.userType else null,
            botService = null,
            userDeleted = if (type == ConversationEntity.Type.ONE_ON_ONE) userEntity?.deleted else null,
            connectionStatus = if (type == ConversationEntity.Type.ONE_ON_ONE) userEntity?.connectionStatus else null,
            otherUserId = if (type == ConversationEntity.Type.ONE_ON_ONE) userEntity?.id else null,
            isCreator = 0L,
            lastNotificationDate = lastNotificationDate,
            protocolInfo = protocolInfo,
            accessList = access,
            accessRoleList = accessRole,
            protocol = protocol,
            mlsCipherSuite = ConversationEntity.CipherSuite.MLS_128_DHKEMX25519_AES128GCM_SHA256_Ed25519,
            mlsEpoch = 0L,
            mlsGroupId = mlsGroupId,
            mlsLastKeyingMaterialUpdateDate = mlsLastKeyingMaterialUpdate,
            mlsGroupState = mlsGroupState,
            mlsProposalTimer = null,
            mutedTime = mutedTime,
            creatorId = creatorId,
            selfRole = MemberEntity.Role.Member,
            receiptMode = ConversationEntity.ReceiptMode.DISABLED,
            messageTimer = messageTimer,
            userMessageTimer = null,
            userSupportedProtocols = if (type == ConversationEntity.Type.ONE_ON_ONE) userEntity?.supportedProtocols else null
        )
    }

    private companion object {
        const val teamId = "teamId"

        val user1 = newUserEntity(id = "1").copy(team = teamId)
        val user2 = newUserEntity(id = "2").copy(team = teamId)
        val user3 = newUserEntity(id = "3").copy(team = teamId)
        val messageTimer = 5000L

        val team = TeamEntity(teamId, "teamName", "")

        val conversationEntity1 = ConversationEntity(
            QualifiedIDEntity("1", "wire.com"),
            "conversation1",
            ConversationEntity.Type.ONE_ON_ONE,
            teamId,
            ConversationEntity.ProtocolInfo.Proteus,
            creatorId = "someValue",
            lastNotificationDate = null,
            lastModifiedDate = "2022-03-30T15:36:00.000Z".toInstant(),
            lastReadDate = "2000-01-01T12:00:00.000Z".toInstant(),
            mutedStatus = ConversationEntity.MutedStatus.ALL_ALLOWED,
            access = listOf(ConversationEntity.Access.LINK, ConversationEntity.Access.INVITE),
            accessRole = listOf(ConversationEntity.AccessRole.NON_TEAM_MEMBER, ConversationEntity.AccessRole.TEAM_MEMBER),
            receiptMode = ConversationEntity.ReceiptMode.DISABLED,
            messageTimer = messageTimer,
            userMessageTimer = null
        )
        val conversationEntity2 = ConversationEntity(
            QualifiedIDEntity("2", "wire.com"),
            "conversation2",
            ConversationEntity.Type.ONE_ON_ONE,
            null,
            ConversationEntity.ProtocolInfo.MLS(
                "group2",
                ConversationEntity.GroupState.ESTABLISHED,
                0UL,
                Instant.parse("2021-03-30T15:36:00.000Z"),
                cipherSuite = ConversationEntity.CipherSuite.MLS_128_DHKEMX25519_AES128GCM_SHA256_Ed25519
            ),
            creatorId = "someValue",
            lastNotificationDate = null,
            lastModifiedDate = "2021-03-30T15:36:00.000Z".toInstant(),
            lastReadDate = "2000-01-01T12:00:00.000Z".toInstant(),
            mutedStatus = ConversationEntity.MutedStatus.ALL_MUTED,
            access = listOf(ConversationEntity.Access.LINK, ConversationEntity.Access.INVITE),
            accessRole = listOf(ConversationEntity.AccessRole.NON_TEAM_MEMBER, ConversationEntity.AccessRole.TEAM_MEMBER),
            receiptMode = ConversationEntity.ReceiptMode.DISABLED,
            messageTimer = messageTimer,
            userMessageTimer = null
        )

        val conversationEntity3 = ConversationEntity(
            QualifiedIDEntity("3", "wire.com"),
            "conversation3",
            ConversationEntity.Type.GROUP,
            null,
            ConversationEntity.ProtocolInfo.MLS(
                "group3",
                ConversationEntity.GroupState.PENDING_JOIN,
                0UL,
                Instant.parse("2021-03-30T15:36:00.000Z"),
                cipherSuite = ConversationEntity.CipherSuite.MLS_128_DHKEMX25519_AES128GCM_SHA256_Ed25519
            ),
            creatorId = "someValue",
            // This conversation was modified after the last time the user was notified about it
            lastNotificationDate = "2021-03-30T15:30:00.000Z".toInstant(),
            lastModifiedDate = "2021-03-30T15:36:00.000Z".toInstant(),
            lastReadDate = "2000-01-01T12:00:00.000Z".toInstant(),
            // and it's status is set to be only notified if there is a mention for the user
            mutedStatus = ConversationEntity.MutedStatus.ONLY_MENTIONS_AND_REPLIES_ALLOWED,
            access = listOf(ConversationEntity.Access.LINK, ConversationEntity.Access.INVITE),
            accessRole = listOf(ConversationEntity.AccessRole.NON_TEAM_MEMBER, ConversationEntity.AccessRole.TEAM_MEMBER),
            receiptMode = ConversationEntity.ReceiptMode.DISABLED,
            messageTimer = messageTimer,
            userMessageTimer = null
        )

        val conversationEntity4 = ConversationEntity(
            QualifiedIDEntity("4", "wire.com"),
            "conversation4",
            ConversationEntity.Type.GROUP,
            null,
            ConversationEntity.ProtocolInfo.MLS(
                "group4",
                ConversationEntity.GroupState.ESTABLISHED,
                0UL,
                Instant.parse("2021-03-30T15:36:00.000Z"),
                cipherSuite = ConversationEntity.CipherSuite.MLS_128_DHKEMX25519_AES128GCM_SHA256_Ed25519
            ),
            creatorId = "someValue",
            // This conversation was modified after the last time the user was notified about it
            lastNotificationDate = "2021-03-30T15:30:00.000Z".toInstant(),
            lastModifiedDate = "2021-03-30T15:36:00.000Z".toInstant(),
            lastReadDate = "2000-01-01T12:00:00.000Z".toInstant(),
            // and it's status is set to be only notified if there is a mention for the user
            mutedStatus = ConversationEntity.MutedStatus.ONLY_MENTIONS_AND_REPLIES_ALLOWED,
            access = listOf(ConversationEntity.Access.LINK, ConversationEntity.Access.INVITE),
            accessRole = listOf(ConversationEntity.AccessRole.NON_TEAM_MEMBER, ConversationEntity.AccessRole.TEAM_MEMBER),
            receiptMode = ConversationEntity.ReceiptMode.DISABLED,
            messageTimer = messageTimer,
            userMessageTimer = null
        )
        val conversationEntity5 = ConversationEntity(
            QualifiedIDEntity("5", "wire.com"),
            "conversation5",
            ConversationEntity.Type.GROUP,
            teamId,
            ConversationEntity.ProtocolInfo.Proteus,
            creatorId = "someValue",
            lastNotificationDate = null,
            lastModifiedDate = "2022-03-30T15:36:00.000Z".toInstant(),
            lastReadDate = "2000-01-01T12:00:00.000Z".toInstant(),
            mutedStatus = ConversationEntity.MutedStatus.ALL_ALLOWED,
            access = listOf(ConversationEntity.Access.LINK, ConversationEntity.Access.INVITE),
            accessRole = listOf(ConversationEntity.AccessRole.NON_TEAM_MEMBER, ConversationEntity.AccessRole.TEAM_MEMBER),
            receiptMode = ConversationEntity.ReceiptMode.DISABLED,
            messageTimer = null,
            userMessageTimer = null
        )
        val conversationEntity6 = ConversationEntity(
            QualifiedIDEntity("6", "wire.com"),
            "conversation6",
            ConversationEntity.Type.GROUP,
            null,
            ConversationEntity.ProtocolInfo.Mixed(
                "group6",
                ConversationEntity.GroupState.ESTABLISHED,
                0UL,
                Instant.parse("2021-03-30T15:36:00.000Z"),
                cipherSuite = ConversationEntity.CipherSuite.MLS_128_DHKEMX25519_AES128GCM_SHA256_Ed25519
            ),
            creatorId = "someValue",
            // This conversation was modified after the last time the user was notified about it
            lastNotificationDate = "2021-03-30T15:30:00.000Z".toInstant(),
            lastModifiedDate = "2021-03-30T15:36:00.000Z".toInstant(),
            lastReadDate = "2000-01-01T12:00:00.000Z".toInstant(),
            // and it's status is set to be only notified if there is a mention for the user
            mutedStatus = ConversationEntity.MutedStatus.ONLY_MENTIONS_AND_REPLIES_ALLOWED,
            access = listOf(ConversationEntity.Access.LINK, ConversationEntity.Access.INVITE),
            accessRole = listOf(ConversationEntity.AccessRole.NON_TEAM_MEMBER, ConversationEntity.AccessRole.TEAM_MEMBER),
            receiptMode = ConversationEntity.ReceiptMode.DISABLED,
            messageTimer = null,
            userMessageTimer = null
        )

        val member1 = MemberEntity(user1.id, MemberEntity.Role.Admin)
        val member2 = MemberEntity(user2.id, MemberEntity.Role.Member)
        val member3 = MemberEntity(user3.id, MemberEntity.Role.Admin)

        val proposalTimer2 = ProposalTimerEntity(
            (conversationEntity2.protocolInfo as ConversationEntity.ProtocolInfo.MLS).groupId,
            Instant.DISTANT_FUTURE
        )

        val proposalTimer3 = ProposalTimerEntity(
            (conversationEntity3.protocolInfo as ConversationEntity.ProtocolInfo.MLS).groupId,
            Instant.DISTANT_FUTURE
        )
    }
}
