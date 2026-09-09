/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
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
@file:Suppress("MagicNumber")

package com.wire.kalium.benchmarks.timeline

import androidx.paging.PagingConfig
import androidx.paging.PagingSource
import com.wire.kalium.logic.data.id.QualifiedID
import com.wire.kalium.logic.data.message.timeline.MessageTimeline
import com.wire.kalium.logic.data.message.timeline.MessageTimelinePage
import com.wire.kalium.logic.data.message.timeline.MessageTimelinePolicy
import com.wire.kalium.logic.data.message.timeline.MessageTimelineRequest
import com.wire.kalium.logic.data.message.timeline.MessageTimelineResult
import com.wire.kalium.logic.feature.message.OpenMessageTimelineUseCase
import com.wire.kalium.persistence.dao.ConnectionEntity
import com.wire.kalium.persistence.dao.QualifiedIDEntity
import com.wire.kalium.persistence.dao.SupportedProtocolEntity
import com.wire.kalium.persistence.dao.UserAvailabilityStatusEntity
import com.wire.kalium.persistence.dao.UserEntity
import com.wire.kalium.persistence.dao.UserIDEntity
import com.wire.kalium.persistence.dao.UserTypeEntity
import com.wire.kalium.persistence.dao.conversation.ConversationEntity
import com.wire.kalium.persistence.dao.message.MessageEntity
import com.wire.kalium.persistence.dao.message.MessageEntityContent
import com.wire.kalium.persistence.dao.message.MessageTimelineDAO
import com.wire.kalium.persistence.dao.message.MessageTimelineLoadResultEntity
import com.wire.kalium.persistence.dao.message.MessageTimelinePageEntity
import com.wire.kalium.persistence.dao.message.MessageTimelineRequestEntity
import com.wire.kalium.persistence.dao.message.MessageTimelineSession
import com.wire.kalium.persistence.dao.message.attachment.MessageAttachmentEntity
import com.wire.kalium.persistence.dao.receipt.ReceiptTypeEntity
import com.wire.kalium.persistence.db.UserDatabaseBuilder
import kotlin.time.Instant

/** Identical fixtures and measured operations for JVM SQLite and Android SQLCipher. */
@Suppress("TooManyFunctions")
class MessageTimelineScenario(
    private val db: UserDatabaseBuilder,
    private val self: UserIDEntity,
    private val messageCount: Int,
    private val depth: Int,
    private val pageSize: Int,
    private val profile: String,
) : AutoCloseable {
    private val conversationId = QualifiedIDEntity("timeline", self.domain)
    private val senders = (0 until 120).map { QualifiedIDEntity("user-$it", self.domain) }
    private lateinit var offsetSource: PagingSource<Int, MessageEntity>
    private lateinit var entityTimeline: MessageTimelineSession
    private lateinit var timeline: MessageTimeline
    private lateinit var entityRequest: MessageTimelineRequestEntity
    private lateinit var request: MessageTimelineRequest
    private lateinit var newerRequest: MessageTimelineRequest.Newer
    private lateinit var aroundRequest: MessageTimelineRequest.AroundMessage
    private var offset = 0

    /** Seeding, constructor access, cursor lookup, and fixture assertions are outside measured regions. */
    suspend fun setUp() {
        require(messageCount >= 10_000 && depth in 0 until messageCount && pageSize in listOf(20, 60))
        require(profile in PROFILES)
        db.userDAO.upsertUsers((listOf(self) + senders).map(::user))
        db.conversationDAO.insertConversations(listOf(conversation()))
        for (start in 0 until messageCount step 500) {
            val indices = start until minOf(start + 500, messageCount)
            db.messageDAO.insertOrIgnoreMessages(indices.map(::message), withUnreadEvents = false)
            seedReactionsAndReceipts(indices)
        }

        val eligible = (messageCount - 1 downTo 0).filter(::isEligible)
        // Depth counts stored rows, so sparse fixtures still have meaningful deep positions.
        offset = eligible.indexOfFirst { it <= messageCount - 1 - depth }
        check(offset >= 0 && eligible.size - offset > pageSize) { "The fixture must contain a full target page and an older cursor" }
        val expectedIds = eligible.drop(offset).take(pageSize).map(::id)
        offsetSource = createOffsetSource()
        entityTimeline = db.messageTimelineDAO.open(conversationId, includeDeleted = false)
        timeline = openTimeline()
        val targetId = expectedIds.first()
        aroundRequest = MessageTimelineRequest.AroundMessage(targetId)
        if (offset == 0) {
            request = MessageTimelineRequest.Latest
            entityRequest = MessageTimelineRequestEntity.Latest
        } else {
            val precedingId = id(eligible[offset - 1])
            val entityAnchor = entityPage(MessageTimelineRequestEntity.AroundMessage(precedingId), 1)
            entityRequest = MessageTimelineRequestEntity.Older(entityAnchor.cursors.getValue(precedingId))
            val anchor = page(MessageTimelineRequest.AroundMessage(precedingId), 1)
            request = MessageTimelineRequest.Older(anchor.items.single().cursor)
        }
        check(keysetListItems().items.map { it.id } == expectedIds)
        check(keysetPageEntities().rows.map { it.id } == expectedIds)
        val followingId = id(eligible[offset + pageSize])
        val following = page(MessageTimelineRequest.AroundMessage(followingId), 1)
        newerRequest = MessageTimelineRequest.Newer(following.items.single().cursor)
        check(newerListItems().items.map { it.id } == expectedIds)
        val old = offsetFullEntities()
        check(old.size == pageSize)
        // The old API does not specify the ID order within timestamp ties.
        if (profile != "TIES") check(old.map { it.id } == expectedIds)
        check(aroundMessage().items.any { it.id == targetId })
    }

    private suspend fun seedReactionsAndReceipts(indices: IntRange) {
        if (profile != "REACTIONS" && profile != "MIXED") return
        for (index in indices.filter { profile == "REACTIONS" || it % 10 == 0 }) {
            // Eight distinct users per message; shared emoji still require aggregation and self detection.
            for (sender in listOf(self) + senders.take(7)) {
                db.reactionDAO.updateReactions(id(index), conversationId, sender, date(index), setOf("👍", "❤️"))
            }
            db.receiptDAO.insertReceipts(senders.first(), conversationId, date(index), ReceiptTypeEntity.READ, listOf(id(index)))
        }
    }

    suspend fun offsetFullEntities(): List<MessageEntity> = when (
        val result = offsetSource.load(PagingSource.LoadParams.Refresh(offset, pageSize, false))
    ) {
        is PagingSource.LoadResult.Page -> result.data
        else -> error("Unexpected offset result: $result")
    }

    suspend fun keysetPageEntities(): MessageTimelinePageEntity = entityPage(entityRequest, pageSize)

    suspend fun keysetListItems(): MessageTimelinePage = page(request, pageSize)

    suspend fun newerListItems(): MessageTimelinePage = page(newerRequest, pageSize)

    suspend fun aroundMessage(): MessageTimelinePage = page(aroundRequest, pageSize)

    private suspend fun entityPage(request: MessageTimelineRequestEntity, limit: Int): MessageTimelinePageEntity = when (
        val result = entityTimeline.load(request, limit)
    ) {
        is MessageTimelineLoadResultEntity.Success -> result.page
        else -> error("Unexpected keyset entity result: $result")
    }

    private suspend fun page(request: MessageTimelineRequest, limit: Int): MessageTimelinePage = when (
        val result = timeline.load(request, limit)
    ) {
        is MessageTimelineResult.Success -> result.page
        else -> error("Unexpected timeline result: $result")
    }

    @Suppress("UNCHECKED_CAST")
    private fun createOffsetSource(): PagingSource<Int, MessageEntity> {
        val pager = db.messageDAO.platformExtensions.getPagerForConversation(
            conversationId,
            listOf(MessageEntity.Visibility.VISIBLE),
            PagingConfig(pageSize),
            offset.toLong()
        )
        return pager.javaClass.getDeclaredField("pagingSource").apply { isAccessible = true }.get(pager)
            as PagingSource<Int, MessageEntity>
    }

    private fun openTimeline(): MessageTimeline {
        // Access the internal composition constructor only during benchmark setup.
        val openConstructor = OpenMessageTimelineUseCase::class.java.declaredConstructors.single()
        val repository = openConstructor.parameterTypes.single().getDeclaredConstructor(
            MessageTimelineDAO::class.java,
            QualifiedID::class.java
        ).newInstance(db.messageTimelineDAO, QualifiedID(self.value, self.domain))
        val open = openConstructor.newInstance(repository) as OpenMessageTimelineUseCase
        return open(
            QualifiedID(conversationId.value, conversationId.domain),
            MessageTimelinePolicy.VISIBLE_ONLY
        )
    }

    override fun close() {
        if (::offsetSource.isInitialized) offsetSource.invalidate()
        if (::entityTimeline.isInitialized) entityTimeline.close()
        if (::timeline.isInitialized) timeline.close()
    }

    private fun isEligible(index: Int): Boolean = profile != "SPARSE" || index % 1_000 < 100

    private fun id(index: Int): String = index.toString().padStart(8, '0')

    private fun date(index: Int): Instant = Instant.fromEpochMilliseconds(
        1_700_000_000_000L + if (profile == "TIES") index / 128 else index
    )

    private fun message(index: Int): MessageEntity {
        val sender = senders[index % senders.size]
        val members = profile == "MEMBERS" || (profile == "MIXED" && index % 10 == 9)
        return if (members) {
            MessageEntity.System(
                id = id(index), conversationId = conversationId, date = date(index), senderUserId = sender,
                status = MessageEntity.Status.SENT, senderName = sender.value, readCount = 0,
                expireAfterMs = null, selfDeletionEndDate = null,
                content = MessageEntityContent.MemberChange(senders, MessageEntity.MemberChangeType.ADDED),
            )
        } else {
            MessageEntity.Regular(
                id = id(index), conversationId = conversationId, date = date(index), senderUserId = sender,
                senderClientId = "client", status = MessageEntity.Status.SENT,
                visibility = if (isEligible(index)) MessageEntity.Visibility.VISIBLE else MessageEntity.Visibility.HIDDEN,
                senderName = sender.value, readCount = 0, editStatus = MessageEntity.EditStatus.NotEdited,
                content = content(index),
            )
        }
    }

    private fun content(index: Int): MessageEntityContent.Regular = when {
        profile == "MULTIPART" || (profile == "MIXED" && index % 10 == 0) -> MessageEntityContent.Multipart(
            "Multipart body $index",
            attachments = (0..3).map { attachment ->
                MessageAttachmentEntity(
                    assetId = "cell-$index-$attachment", cellAsset = true, mimeType = "image/png", assetPath = "photo.png",
                    assetSize = 10_000, assetWidth = 640, assetHeight = 480, assetDuration = null,
                    assetTransferStatus = "NOT_DOWNLOADED", isEditSupported = true,
                )
            }
        )
        profile == "MIXED" && index % 10 == 1 -> MessageEntityContent.Asset(
            assetSizeInBytes = 10_000, assetName = "photo.png", assetMimeType = "image/png",
            assetId = "asset-$index", assetDomain = self.domain, assetToken = "benchmark-token",
            assetOtrKey = ByteArray(32) { 1 }, assetSha256Key = ByteArray(32) { 2 }, assetEncryptionAlgorithm = "AES_CBC",
            assetWidth = 640, assetHeight = 480,
        )
        profile == "MIXED" && index % 10 == 2 -> MessageEntityContent.Unknown("benchmark", ByteArray(16_384) { 3 })
        else -> MessageEntityContent.Text(
            messageBody = "Text with quotes \" and a newline\n".repeat(if (profile == "MIXED" && index % 10 == 3) 256 else 8),
            mentions = listOf(MessageEntity.Mention(0, 4, self)),
            linkPreview = listOf(MessageEntity.LinkPreview("https://wire.com", 0, "https://wire.com", "Wire", "Preview")),
            quotedMessageId = if (index > 0) id(0) else null,
            isQuoteVerified = true,
        )
    }

    private fun user(id: UserIDEntity) = UserEntity(
        id = id, name = id.value, handle = id.value, email = "${id.value}@benchmark.invalid", phone = null,
        accentId = 1, team = null, connectionStatus = ConnectionEntity.State.ACCEPTED,
        previewAssetId = null, completeAssetId = null, availabilityStatus = UserAvailabilityStatusEntity.NONE,
        userType = UserTypeEntity.STANDARD, botService = null, deleted = false, expiresAt = null, defederated = false,
        supportedProtocols = setOf(SupportedProtocolEntity.PROTEUS), activeOneOnOneConversationId = null,
    )

    private fun conversation() = ConversationEntity(
        id = conversationId, name = "Timeline benchmark", type = ConversationEntity.Type.GROUP, teamId = null,
        protocolInfo = ConversationEntity.ProtocolInfo.Proteus, creatorId = self.value,
        lastNotificationDate = null, lastModifiedDate = date(messageCount - 1), lastReadDate = date(0),
        access = listOf(ConversationEntity.Access.INVITE), accessRole = listOf(ConversationEntity.AccessRole.NON_TEAM_MEMBER),
        receiptMode = ConversationEntity.ReceiptMode.ENABLED, messageTimer = null, userMessageTimer = null,
        archived = false, archivedInstant = null,
        mlsVerificationStatus = ConversationEntity.VerificationStatus.NOT_VERIFIED,
        proteusVerificationStatus = ConversationEntity.VerificationStatus.NOT_VERIFIED,
        legalHoldStatus = ConversationEntity.LegalHoldStatus.DISABLED, channelAccess = null, channelAddPermission = null,
        wireCell = null, historySharingRetentionSeconds = 0,
    )

    companion object {
        val POSITIONS = listOf("10000:0", "10000:1000", "10000:9000", "100000:0", "100000:1000", "100000:10000", "100000:90000")
        val PROFILES = listOf("TEXT", "MIXED", "MULTIPART", "REACTIONS", "MEMBERS", "SPARSE", "TIES")
    }
}
