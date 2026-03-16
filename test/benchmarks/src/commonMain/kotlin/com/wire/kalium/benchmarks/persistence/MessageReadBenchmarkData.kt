@file:Suppress("MagicNumber")

package com.wire.kalium.benchmarks.persistence

import app.cash.paging.PagingConfig
import app.cash.paging.PagingSourceLoadResultPage
import com.wire.kalium.persistence.dao.ConnectionEntity
import com.wire.kalium.persistence.dao.ConversationIDEntity
import com.wire.kalium.persistence.dao.QualifiedIDEntity
import com.wire.kalium.persistence.dao.SupportedProtocolEntity
import com.wire.kalium.persistence.dao.UserAvailabilityStatusEntity
import com.wire.kalium.persistence.dao.UserEntity
import com.wire.kalium.persistence.dao.UserIDEntity
import com.wire.kalium.persistence.dao.UserTypeEntity
import com.wire.kalium.persistence.dao.conversation.ConversationEntity
import com.wire.kalium.persistence.dao.conversation.ConversationFilterEntity
import com.wire.kalium.persistence.dao.member.MemberEntity
import com.wire.kalium.persistence.dao.message.MessageEntity
import com.wire.kalium.persistence.dao.message.MessageEntityContent
import com.wire.kalium.persistence.db.UserDatabaseBuilder
import kotlinx.coroutines.flow.first
import kotlinx.datetime.Instant
import kotlin.math.floor
import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.TimeSource

internal object MessageReadBenchmarkData {
    const val TotalUsers = 2_000
    const val TotalConversations = 2_000
    const val GlobalConversationCount = 100
    const val OneOnOneConversationCount = 600
    const val SmallGroupConversationCount = 900
    const val LargeGroupConversationCount = 400
    const val TotalMessages = 400_000
    const val HotConversationMessageCount = 20_000
    const val ReadConversationCount = 900
    const val Seed = 0x4B_41_4C_49_55_4DL
    const val PageSize = 50
    const val InboxDeepOffset = 500
    const val MessageDeepOffset = 5_000

    private const val Domain = "benchmark.wire.com"
    private const val TeamId = "benchmark-team"
    private const val ConversationStrideMs = 100_000_000L
    private const val MessageStrideMs = 1_000L
    private const val BaseEpochMs = 1_700_000_000_000L

    private val SmallGroupSizeRange = 3..12
    private val LargeGroupSizeRange = 50..300

    data class SeededContext(
        val hotConversationId: ConversationIDEntity,
        val markReadConversationId: ConversationIDEntity,
        val markReadSenderId: UserIDEntity,
        val markReadNextMessageEpochMs: Long,
        val seedDuration: Duration,
    )

    private data class ConversationSeedSpec(
        val id: ConversationIDEntity,
        val type: ConversationEntity.Type,
        val name: String?,
        val members: List<UserIDEntity>,
        val messageCount: Int,
        val lastMessageDate: Instant,
    )

    private data class Scenario(
        val users: List<UserEntity>,
        val conversations: List<ConversationSeedSpec>,
        val globalConversationIds: List<ConversationIDEntity>,
        val hotConversationId: ConversationIDEntity,
        val markReadConversationId: ConversationIDEntity,
        val markReadSenderId: UserIDEntity,
        val readConversationDates: Map<ConversationIDEntity, Instant>,
        val validationUnreadConversationId: ConversationIDEntity,
        val expectedMemberCount: Int,
    )

    suspend fun seed(db: UserDatabaseBuilder): SeededContext {
        val scenario = buildScenario()
        val timeMark = TimeSource.Monotonic.markNow()

        db.userDAO.upsertUsers(scenario.users)
        db.conversationDAO.insertConversations(scenario.conversations.map { it.toConversationEntity() })
        scenario.conversations.forEach { spec ->
            db.memberDAO.insertMembersWithQualifiedId(
                memberList = spec.members.map(::toMember),
                conversationID = spec.id
            )
        }
        seedMessages(db, scenario)

        val readStatus = db.conversationDAO.updateReadDatesAndGetHasUnreadEvents(scenario.readConversationDates)
        check(readStatus.size == scenario.readConversationDates.size) {
            "Expected ${scenario.readConversationDates.size} conversations to be marked read, got ${readStatus.size}"
        }

        validateSeed(db, scenario)

        return SeededContext(
            hotConversationId = scenario.hotConversationId,
            markReadConversationId = scenario.markReadConversationId,
            markReadSenderId = scenario.markReadSenderId,
            markReadNextMessageEpochMs = scenario.readConversationDates.getValue(scenario.markReadConversationId).toEpochMilliseconds() + MessageStrideMs,
            seedDuration = timeMark.elapsedNow(),
        )
    }

    suspend fun insertUnreadMessageForMarkRead(
        db: UserDatabaseBuilder,
        conversationId: ConversationIDEntity,
        senderId: UserIDEntity,
        messageEpochMs: Long,
    ): Instant {
        val timestamp = Instant.fromEpochMilliseconds(messageEpochMs)
        val message = MessageEntity.Regular(
            id = "mark-read-${messageEpochMs}",
            conversationId = conversationId,
            date = timestamp,
            senderUserId = senderId,
            status = MessageEntity.Status.SENT,
            visibility = MessageEntity.Visibility.VISIBLE,
            content = MessageEntityContent.Text("mark read probe $messageEpochMs"),
            senderClientId = "benchmark-client",
            editStatus = MessageEntity.EditStatus.NotEdited,
            senderName = senderId.value,
            readCount = 0,
        )
        db.messageDAO.insertOrIgnoreMessage(message)
        return timestamp
    }

    private suspend fun validateSeed(db: UserDatabaseBuilder, scenario: Scenario) {
        val userCount = db.userDAO.getAllUsersDetails().first().size
        check(userCount == TotalUsers) { "Expected $TotalUsers users, got $userCount" }

        val conversationCount = db.conversationDAO.getAllConversations().first().size
        check(conversationCount == TotalConversations) { "Expected $TotalConversations conversations, got $conversationCount" }

        val memberCount = db.memberDAO.getAllMembers().size
        check(memberCount == scenario.expectedMemberCount) {
            "Expected ${scenario.expectedMemberCount} member rows, got $memberCount"
        }

        val messageCount = db.messageDAO.countMessagesForBackup(MessageEntity.ContentType.entries)
        check(messageCount == TotalMessages.toLong()) { "Expected $TotalMessages messages, got $messageCount" }

        scenario.globalConversationIds.forEach { conversationId ->
            val members = db.memberDAO.getConversationMembers(conversationId)
            check(members.size == TotalUsers) {
                "Expected global conversation ${conversationId.value} to have $TotalUsers members, got ${members.size}"
            }
        }

        val oneOnOneCount = db.conversationDAO.getAllConversationDetails(
            fromArchive = false,
            filter = ConversationFilterEntity.ONE_ON_ONE
        ).first().size
        check(oneOnOneCount == OneOnOneConversationCount) {
            "Expected $OneOnOneConversationCount active one-on-ones, got $oneOnOneCount"
        }

        val conversationDetails = db.conversationDAO.getAllConversationDetailsWithEvents(
            fromArchive = false,
            onlyInteractionEnabled = false,
            newActivitiesOnTop = true
        ).first()
        check(conversationDetails.size == TotalConversations) {
            "Expected $TotalConversations conversation detail rows, got ${conversationDetails.size}"
        }
        check(conversationDetails.all { it.lastMessage != null }) {
            "Every seeded conversation should have a last message"
        }

        val readConversations = conversationDetails.count { it.unreadEvents.unreadEvents.isEmpty() }
        val unreadConversations = conversationDetails.size - readConversations
        check(readConversations > 0 && unreadConversations > 0) {
            "Expected a mix of read/unread conversations, got read=$readConversations unread=$unreadConversations"
        }

        val hotFirstPage = loadMessagePage(
            db = db,
            conversationId = scenario.hotConversationId,
            offset = 0
        )
        check(hotFirstPage.data.isNotEmpty()) { "Hot conversation first page should not be empty" }

        val hotDeepPage = loadMessagePage(
            db = db,
            conversationId = scenario.hotConversationId,
            offset = MessageDeepOffset
        )
        check(hotDeepPage.data.isNotEmpty()) { "Hot conversation deep page should not be empty" }

        val markReadResult = db.conversationDAO.updateReadDateAndGetHasUnreadEvents(
            scenario.validationUnreadConversationId,
            scenario.conversations.first { it.id == scenario.validationUnreadConversationId }.lastMessageDate
        )
        check(!markReadResult) { "Expected unread events to be cleared after marking validation conversation as read" }
    }

    private suspend fun loadMessagePage(
        db: UserDatabaseBuilder,
        conversationId: ConversationIDEntity,
        offset: Int,
    ): PagingSourceLoadResultPage<Int, MessageEntity> {
        val pagingSource = db.messageDAO.platformExtensions.getPagerForConversation(
            conversationId = conversationId,
            visibilities = listOf(MessageEntity.Visibility.VISIBLE),
            pagingConfig = PagingConfig(PageSize),
            startingOffset = offset.toLong()
        )

        val result = pagingSource.loadRefreshPage(PageSize)
        check(result is PagingSourceLoadResultPage<Int, MessageEntity>) {
            "Expected a page result for message load, got $result"
        }
        return result
    }

    private suspend fun seedMessages(db: UserDatabaseBuilder, scenario: Scenario) {
        val random = Random(Seed)
        val chunk = ArrayList<MessageEntity>(2_000)

        scenario.conversations.forEach { spec ->
            repeat(spec.messageCount) { index ->
                chunk += generateMessage(spec, index, random)
                if (chunk.size >= 2_000) {
                    db.messageDAO.insertOrIgnoreMessages(chunk.toList())
                    chunk.clear()
                }
            }
        }

        if (chunk.isNotEmpty()) {
            db.messageDAO.insertOrIgnoreMessages(chunk.toList())
        }
    }

    private fun generateMessage(
        spec: ConversationSeedSpec,
        index: Int,
        random: Random,
    ): MessageEntity {
        val timestamp = Instant.fromEpochMilliseconds(spec.messageStartEpochMs() + index * MessageStrideMs)
        val sender = chooseSender(spec, random)
        val id = "${spec.id.value}-msg-$index"
        val senderName = sender.value
        return when (random.nextInt(100)) {
            in 0..84 -> MessageEntity.Regular(
                id = id,
                conversationId = spec.id,
                date = timestamp,
                senderUserId = sender,
                status = MessageEntity.Status.SENT,
                visibility = MessageEntity.Visibility.VISIBLE,
                content = MessageEntityContent.Text("benchmark text ${spec.id.value} $index"),
                senderClientId = "client-${sender.value}",
                editStatus = MessageEntity.EditStatus.NotEdited,
                senderName = senderName,
                readCount = 0,
            )

            in 85..94 -> MessageEntity.Regular(
                id = id,
                conversationId = spec.id,
                date = timestamp,
                senderUserId = sender,
                status = MessageEntity.Status.SENT,
                visibility = MessageEntity.Visibility.VISIBLE,
                content = MessageEntityContent.Asset(
                    assetSizeInBytes = 1_024L + index,
                    assetName = "asset-$id.bin",
                    assetMimeType = "application/octet-stream",
                    assetOtrKey = byteArrayOf(1, 2, 3),
                    assetSha256Key = byteArrayOf(3, 2, 1),
                    assetId = "asset-$id",
                    assetToken = "token-$id",
                    assetDomain = Domain,
                    assetEncryptionAlgorithm = "aes-gcm",
                    assetWidth = null,
                    assetHeight = null,
                    assetDurationMs = null,
                    assetNormalizedLoudness = null,
                ),
                senderClientId = "client-${sender.value}",
                editStatus = MessageEntity.EditStatus.NotEdited,
                senderName = senderName,
                readCount = 0,
            )

            in 95..97 -> MessageEntity.Regular(
                id = id,
                conversationId = spec.id,
                date = timestamp,
                senderUserId = sender,
                status = MessageEntity.Status.SENT,
                visibility = MessageEntity.Visibility.VISIBLE,
                content = MessageEntityContent.Knock(hotKnock = index % 2 == 0),
                senderClientId = "client-${sender.value}",
                editStatus = MessageEntity.EditStatus.NotEdited,
                senderName = senderName,
                readCount = 0,
            )

            else -> MessageEntity.System(
                id = id,
                content = MessageEntityContent.MissedCall,
                conversationId = spec.id,
                date = timestamp,
                senderUserId = sender,
                status = MessageEntity.Status.SENT,
                expireAfterMs = null,
                selfDeletionEndDate = null,
                readCount = 0,
                visibility = MessageEntity.Visibility.VISIBLE,
                senderName = senderName,
            )
        }
    }

    private fun chooseSender(
        spec: ConversationSeedSpec,
        random: Random,
    ): UserIDEntity {
        val remoteMembers = spec.members.drop(1)
        return if (remoteMembers.isEmpty()) {
            spec.members.first()
        } else if (spec.type == ConversationEntity.Type.ONE_ON_ONE) {
            if (random.nextInt(100) < 90) remoteMembers.first() else spec.members.first()
        } else {
            if (random.nextInt(100) < 95) remoteMembers[random.nextInt(remoteMembers.size)] else spec.members.first()
        }
    }

    private fun buildScenario(): Scenario {
        val random = Random(Seed)
        val selfUserId = qualifiedId("self")
        val remoteUsers = (1 until TotalUsers).map { qualifiedId("user-$it") }

        val conversationMembers = ArrayList<Pair<ConversationIDEntity, List<UserIDEntity>>>(TotalConversations)
        val globalConversationIds = ArrayList<ConversationIDEntity>(GlobalConversationCount)

        repeat(GlobalConversationCount) { index ->
            val id = conversationId("global-${index + 1}")
            globalConversationIds += id
            conversationMembers += id to listOf(selfUserId) + remoteUsers
        }

        repeat(OneOnOneConversationCount) { index ->
            val remoteUser = remoteUsers[index]
            conversationMembers += conversationId("one-on-one-${index + 1}") to listOf(selfUserId, remoteUser)
        }

        repeat(SmallGroupConversationCount) { index ->
            val totalMembers = random.nextInt(SmallGroupSizeRange.first, SmallGroupSizeRange.last + 1)
            val remotes = remoteUsers.shuffled(random).take(totalMembers - 1)
            conversationMembers += conversationId("small-group-${index + 1}") to listOf(selfUserId) + remotes
        }

        repeat(LargeGroupConversationCount) { index ->
            val totalMembers = random.nextInt(LargeGroupSizeRange.first, LargeGroupSizeRange.last + 1)
            val remotes = remoteUsers.shuffled(random).take(totalMembers - 1)
            conversationMembers += conversationId("large-group-${index + 1}") to listOf(selfUserId) + remotes
        }

        check(conversationMembers.size == TotalConversations) {
            "Expected $TotalConversations conversations, got ${conversationMembers.size}"
        }

        val hotConversationId = globalConversationIds.first()
        val markReadConversationId = conversationMembers.last().first
        val markReadMembers = conversationMembers.last().second
        val markReadSenderId = markReadMembers.first { it != selfUserId }

        val messageCounts = computeMessageCounts(conversationMembers, hotConversationId)

        val users = buildUsers(
            selfUserId = selfUserId,
            remoteUsers = remoteUsers,
            oneOnOneConversationByRemote = conversationMembers
                .drop(GlobalConversationCount)
                .take(OneOnOneConversationCount)
                .associate { pair -> pair.second.last() to pair.first }
        )

        val conversations = conversationMembers.mapIndexed { ordinal, (conversationId, members) ->
            val messageCount = messageCounts.getValue(conversationId)
            val startEpochMs = conversationStartEpochMs(ordinal)
            val lastMessageDate = Instant.fromEpochMilliseconds(startEpochMs + (messageCount - 1) * MessageStrideMs)
            ConversationSeedSpec(
                id = conversationId,
                type = if (members.size == 2 && members.first() == selfUserId) ConversationEntity.Type.ONE_ON_ONE else ConversationEntity.Type.GROUP,
                name = when {
                    conversationId.value.startsWith("one-on-one-") -> null
                    else -> conversationId.value.replace('-', ' ')
                },
                members = members,
                messageCount = messageCount,
                lastMessageDate = lastMessageDate,
            )
        }

        val readConversationIds = buildReadConversationIds(
            conversations = conversations,
            hotConversationId = hotConversationId,
            markReadConversationId = markReadConversationId,
            random = random
        )

        return Scenario(
            users = users,
            conversations = conversations,
            globalConversationIds = globalConversationIds,
            hotConversationId = hotConversationId,
            markReadConversationId = markReadConversationId,
            markReadSenderId = markReadSenderId,
            readConversationDates = readConversationIds.associateWith { conversationId ->
                conversations.first { it.id == conversationId }.lastMessageDate
            },
            validationUnreadConversationId = conversations.first { it.id != hotConversationId && it.id !in readConversationIds }.id,
            expectedMemberCount = conversations.sumOf { it.members.size }
        )
    }

    private fun buildUsers(
        selfUserId: UserIDEntity,
        remoteUsers: List<UserIDEntity>,
        oneOnOneConversationByRemote: Map<UserIDEntity, ConversationIDEntity>,
    ): List<UserEntity> = buildList(TotalUsers) {
        add(newUser(selfUserId, "Self User"))
        remoteUsers.forEachIndexed { index, userId ->
            add(
                newUser(
                    id = userId,
                    name = "Benchmark User ${index + 1}",
                    activeOneOnOneConversationId = oneOnOneConversationByRemote[userId]
                )
            )
        }
    }

    private fun buildReadConversationIds(
        conversations: List<ConversationSeedSpec>,
        hotConversationId: ConversationIDEntity,
        markReadConversationId: ConversationIDEntity,
        random: Random,
    ): Set<ConversationIDEntity> {
        val candidates = conversations
            .map { it.id }
            .filter { it != hotConversationId && it != markReadConversationId }
            .shuffled(random)
            .take(ReadConversationCount - 1)
            .toMutableSet()
        candidates += markReadConversationId
        return candidates
    }

    private fun computeMessageCounts(
        conversationMembers: List<Pair<ConversationIDEntity, List<UserIDEntity>>>,
        hotConversationId: ConversationIDEntity,
    ): Map<ConversationIDEntity, Int> {
        val counts = conversationMembers.associate { it.first to 1 }.toMutableMap()
        counts[hotConversationId] = HotConversationMessageCount

        val remainingIds = conversationMembers.map { it.first }.filter { it != hotConversationId }
        val remainingMessages = TotalMessages - HotConversationMessageCount - (TotalConversations - 1)
        val weights = remainingIds.associateWith { conversationId ->
            conversationMembers.first { it.first == conversationId }.second.size.toDouble()
        }
        val totalWeight = weights.values.sum()

        val fractionalRemainders = ArrayList<Pair<ConversationIDEntity, Double>>(remainingIds.size)
        var assigned = 0
        remainingIds.forEach { conversationId ->
            val exactShare = remainingMessages * (weights.getValue(conversationId) / totalWeight)
            val wholeShare = floor(exactShare).toInt()
            counts[conversationId] = counts.getValue(conversationId) + wholeShare
            fractionalRemainders += conversationId to (exactShare - wholeShare)
            assigned += wholeShare
        }

        val leftover = remainingMessages - assigned
        fractionalRemainders
            .sortedByDescending { it.second }
            .take(leftover)
            .forEach { (conversationId, _) ->
                counts[conversationId] = counts.getValue(conversationId) + 1
            }

        check(counts.values.sum() == TotalMessages) {
            "Expected $TotalMessages total messages, got ${counts.values.sum()}"
        }
        return counts
    }

    private fun ConversationSeedSpec.toConversationEntity(): ConversationEntity =
        ConversationEntity(
            id = id,
            name = name,
            type = type,
            teamId = TeamId,
            protocolInfo = ConversationEntity.ProtocolInfo.Proteus,
            creatorId = "self",
            lastNotificationDate = null,
            lastModifiedDate = lastMessageDate,
            lastReadDate = Instant.fromEpochMilliseconds(messageStartEpochMs() - MessageStrideMs),
            access = listOf(ConversationEntity.Access.INVITE),
            accessRole = listOf(ConversationEntity.AccessRole.TEAM_MEMBER),
            receiptMode = ConversationEntity.ReceiptMode.ENABLED,
            messageTimer = null,
            userMessageTimer = null,
            archived = false,
            archivedInstant = null,
            mlsVerificationStatus = ConversationEntity.VerificationStatus.NOT_VERIFIED,
            proteusVerificationStatus = ConversationEntity.VerificationStatus.NOT_VERIFIED,
            legalHoldStatus = ConversationEntity.LegalHoldStatus.DISABLED,
            isChannel = false,
            channelAccess = null,
            channelAddPermission = null,
            wireCell = null,
            historySharingRetentionSeconds = 0,
        )

    private fun ConversationSeedSpec.messageStartEpochMs(): Long =
        lastMessageDate.toEpochMilliseconds() - (messageCount - 1) * MessageStrideMs

    private fun conversationStartEpochMs(ordinal: Int): Long =
        BaseEpochMs + (TotalConversations - ordinal).toLong() * ConversationStrideMs

    private fun qualifiedId(value: String): QualifiedIDEntity = QualifiedIDEntity(value, Domain)

    private fun conversationId(value: String): ConversationIDEntity = ConversationIDEntity(value, Domain)

    private fun newUser(
        id: UserIDEntity,
        name: String,
        activeOneOnOneConversationId: ConversationIDEntity? = null,
    ) = UserEntity(
        id = id,
        name = name,
        handle = id.value,
        email = "${id.value}@benchmark.invalid",
        phone = null,
        accentId = 1,
        team = TeamId,
        connectionStatus = ConnectionEntity.State.ACCEPTED,
        previewAssetId = null,
        completeAssetId = null,
        availabilityStatus = UserAvailabilityStatusEntity.NONE,
        userType = UserTypeEntity.STANDARD,
        botService = null,
        deleted = false,
        hasIncompleteMetadata = false,
        expiresAt = null,
        defederated = false,
        supportedProtocols = setOf(SupportedProtocolEntity.PROTEUS),
        activeOneOnOneConversationId = activeOneOnOneConversationId
    )

    private fun toMember(userId: UserIDEntity): MemberEntity = MemberEntity(
        user = userId,
        role = MemberEntity.Role.Member
    )
}
