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

package com.wire.kalium.persistence.dao.message

import com.wire.kalium.persistence.dao.QualifiedIDEntity
import com.wire.kalium.persistence.dao.UserAssetIdEntity
import com.wire.kalium.persistence.dao.UserDetailsEntity
import com.wire.kalium.persistence.dao.UserIDEntity
import com.wire.kalium.persistence.dao.asset.AssetTransferStatusEntity
import com.wire.kalium.persistence.dao.conversation.ConversationEntity
import com.wire.kalium.persistence.dao.reaction.ReactionsEntity
import kotlinx.datetime.Instant
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Serializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@Suppress("LongParameterList")
sealed interface MessageEntity {
    val id: String
    val content: MessageEntityContent
    val conversationId: QualifiedIDEntity
    val date: Instant
    val senderUserId: QualifiedIDEntity
    val status: Status
    val readCount: Long
    val visibility: Visibility
    val isSelfMessage: Boolean
    val expireAfterMs: Long?
    val selfDeletionEndDate: Instant?
    val sender: UserDetailsEntity?

    data class Regular(
        override val id: String,
        override val conversationId: QualifiedIDEntity,
        override val date: Instant,
        override val senderUserId: QualifiedIDEntity,
        override val status: Status,
        override val visibility: Visibility = Visibility.VISIBLE,
        override val content: MessageEntityContent.Regular,
        override val isSelfMessage: Boolean = false,
        override val readCount: Long,
        override val expireAfterMs: Long? = null,
        override val selfDeletionEndDate: Instant? = null,
        override val sender: UserDetailsEntity? = null,
        val senderName: String?,
        val senderClientId: String,
        val editStatus: EditStatus,
        val reactions: ReactionsEntity = ReactionsEntity.EMPTY,
        val expectsReadConfirmation: Boolean = false,
        val deliveryStatus: DeliveryStatusEntity = DeliveryStatusEntity.CompleteDelivery,
    ) : MessageEntity

    data class System(
        override val id: String,
        override val content: MessageEntityContent.System,
        override val conversationId: QualifiedIDEntity,
        override val date: Instant,
        override val senderUserId: QualifiedIDEntity,
        override val status: Status,
        override val expireAfterMs: Long?,
        override val selfDeletionEndDate: Instant?,
        override val readCount: Long,
        override val visibility: Visibility = Visibility.VISIBLE,
        override val isSelfMessage: Boolean = false,
        override val sender: UserDetailsEntity? = null,
        val senderName: String?,
    ) : MessageEntity

    enum class Status {
        /**
         * The message was stored locally and is ready to be sent.
         */
        PENDING,

        /**
         * The message was sent to the backend.
         */
        SENT,

        /**
         * The message was delivered but not read.
         */
        DELIVERED,

        /**
         * The message was marked as read locally.
         */
        READ,

        /**
         * The message failed to be sent, general errors, e.g. self backend not available, etc.
         */
        FAILED,

        /**
         * The message failed to be sent because the conversation owner is not available.
         * Note that this is currently only relevant for federated conversations.
         */
        FAILED_REMOTELY
    }

    sealed class EditStatus {
        data object NotEdited : EditStatus()
        data class Edited(val lastDate: Instant) : EditStatus()

        override fun toString(): String {
            return when (this) {
                is NotEdited -> "NOT_EDITED"
                is Edited -> "EDITED_AT: ${this.lastDate}"
            }
        }
    }

    enum class ConfirmationType {
        READ, DELIVERED, UNRECOGNIZED
    }

    @Serializable
    enum class ContentType {
        TEXT, ASSET, KNOCK, MEMBER_CHANGE, MISSED_CALL, RESTRICTED_ASSET,
        CONVERSATION_RENAMED, UNKNOWN, FAILED_DECRYPTION, REMOVED_FROM_TEAM, CRYPTO_SESSION_RESET,
        NEW_CONVERSATION_RECEIPT_MODE, CONVERSATION_RECEIPT_MODE_CHANGED, HISTORY_LOST, HISTORY_LOST_PROTOCOL_CHANGED,
        CONVERSATION_MESSAGE_TIMER_CHANGED, CONVERSATION_CREATED, MLS_WRONG_EPOCH_WARNING, CONVERSATION_DEGRADED_MLS,
        CONVERSATION_DEGRADED_PROTEUS, CONVERSATION_VERIFIED_MLS, CONVERSATION_VERIFIED_PROTEUS, COMPOSITE, FEDERATION,
        CONVERSATION_PROTOCOL_CHANGED, CONVERSATION_PROTOCOL_CHANGED_DURING_CALL,
        CONVERSATION_STARTED_UNVERIFIED_WARNING, LOCATION, LEGAL_HOLD
    }

    enum class MemberChangeType {
        /**
         * A member(s) was added to the conversation.
         */
        ADDED,

        /**
         * A member(s) was removed from the conversation.
         */
        REMOVED,

        /**
         * A member(s) was added to the conversation while the conversation was being created.
         * Note: This is only valid for the creator of the conversation, local-only.
         */
        CREATION_ADDED,

        /**
         * A member(s) was not added to the conversation due to the federation error.
         * Note: This is only valid for the creator of the conversation, local-only.
         */
        FAILED_TO_ADD_FEDERATION,

        /**
         * A member(s) was not added to the conversation due to the legal hold error.
         * Note: This is only valid for the creator of the conversation, local-only.
         */
        FAILED_TO_ADD_LEGAL_HOLD,

        /**
         * A member(s) was not added to the conversation due to the other unknown error.
         * Note: This is only valid for the creator of the conversation, local-only.
         */
        FAILED_TO_ADD_UNKNOWN,

        /**
         * Member(s) removed from the conversation, due to some backend stopped to federate between them, or us.
         */
        FEDERATION_REMOVED,

        /**
         * A member(s) was removed from the team.
         */
        REMOVED_FROM_TEAM;
    }

    enum class FederationType {
        DELETE, CONNECTION_REMOVED
    }

    enum class LegalHoldType {
        ENABLED_FOR_MEMBERS, DISABLED_FOR_MEMBERS, ENABLED_FOR_CONVERSATION, DISABLED_FOR_CONVERSATION
    }

    enum class Visibility {
        VISIBLE, DELETED, HIDDEN;

        val isVisible get() = this == VISIBLE
    }

    @Serializable
    data class Mention(
        @SerialName("start") val start: Int,
        @SerialName("length") val length: Int,
        @SerialName("userId") val userId: QualifiedIDEntity
    )

    data class LinkPreview(
        val url: String,
        val urlOffset: Int,
        val permanentUrl: String,
        val title: String,
        val summary: String,
        val imageAssetKey: String?,
        val imageAssetDomain: String?,
        val imageAssetDataPath: String?,
        val imageAssetDataSize: Long?,
        val imageAssetToken: String?,
        val imageAssetMimeType: String?,
        val imageAssetHeight: Int?,
        val imageAssetWidth: Int?,
        val imageAssetEncryptionAlgorithm: String?,
        val downloadedDate: Long?
    )
}

sealed class MessageEntityContent {

    sealed class Regular : MessageEntityContent()

    sealed class System : MessageEntityContent()
    sealed class Signaling : MessageEntityContent()

    data class Text(
        val messageBody: String,
        val linkPreview: List<MessageEntity.LinkPreview> = listOf(),
        val mentions: List<MessageEntity.Mention> = listOf(),
        /**
         * ID of a message being quoted.
         * When persisting the content, this is the ID that will be used for quotes.
         *
         * TODO(refactor): Consider removing this
         *                 Only exists to make it easier to insert into the DB
         *                 Otherwise we'd need to pass a full QuotedMessage object
         */
        val quotedMessageId: String? = null,
        val isQuoteVerified: Boolean? = null,
        /**
         * Details of the message being quoted.
         * Unused when inserting into the DB.
         */
        val quotedMessage: QuotedMessage? = null,
    ) : Regular() {
        data class QuotedMessage(
            val id: String,
            val senderId: QualifiedIDEntity,
            val isQuotingSelfUser: Boolean,
            /**
             * Indicates that the hash of the quote
             * matches the hash of the original message
             */
            val isVerified: Boolean,
            val senderName: String?,
            val dateTime: String,
            val editTimestamp: String?,
            val visibility: MessageEntity.Visibility,
            val contentType: MessageEntity.ContentType,
            val textBody: String?,
            val assetMimeType: String?,
            val assetName: String?,
            val locationName: String?,
        )
    }

    data class Asset(
        val assetSizeInBytes: Long,
        // TODO: Make it not-nullable, fallback to message ID or something else if it comes without a name from the protobuf models
        val assetName: String? = null,
        val assetMimeType: String,

        // remote data fields
        val assetOtrKey: ByteArray,
        val assetSha256Key: ByteArray,
        val assetId: String,
        val assetToken: String? = null,
        val assetDomain: String? = null,
        val assetEncryptionAlgorithm: String?,

        // metadata fields
        val assetWidth: Int? = null,
        val assetHeight: Int? = null,
        val assetDurationMs: Long? = null,
        val assetNormalizedLoudness: ByteArray? = null,
    ) : Regular()

    data class Knock(val hotKnock: Boolean) : Regular()
    data class Location(
        val latitude: Float,
        val longitude: Float,
        val name: String? = null,
        val zoom: Int? = null,
    ) : Regular()

    data class Unknown(
        val typeName: String? = null,
        val encodedData: ByteArray? = null
    ) : Regular()

    data class FailedDecryption(
        val encodedData: ByteArray? = null,
        val isDecryptionResolved: Boolean,
        val senderUserId: QualifiedIDEntity,
        val senderClientId: String?,
    ) : Regular()

    data object MLSWrongEpochWarning : System()

    data class MemberChange(
        val memberUserIdList: List<QualifiedIDEntity>,
        val memberChangeType: MessageEntity.MemberChangeType
    ) : System()

    data class RestrictedAsset(
        val mimeType: String,
        val assetSizeInBytes: Long,
        val assetName: String,
    ) : Regular()

    data class Composite(
        val text: Text?,
        val buttonList: List<ButtonEntity>
    ) : Regular()

    data object MissedCall : System()
    data object CryptoSessionReset : System()
    data class ConversationRenamed(val conversationName: String) : System()

    @Deprecated("not maintained and will be deleted")
    data class TeamMemberRemoved(val userName: String) : System()
    data class NewConversationReceiptMode(val receiptMode: Boolean) : System()
    data class ConversationReceiptModeChanged(val receiptMode: Boolean) : System()
    data class ConversationMessageTimerChanged(val messageTimer: Long?) : System()
    data class ConversationProtocolChanged(val protocol: ConversationEntity.Protocol) : System()
    data object ConversationProtocolChangedDuringACall : System()
    data object HistoryLostProtocolChanged : System()
    data object HistoryLost : System()
    data object ConversationCreated : System()
    data object ConversationDegradedMLS : System()
    data object ConversationVerifiedMLS : System()
    data object ConversationDegradedProteus : System()
    data object ConversationVerifiedProteus : System()
    data object ConversationStartedUnverifiedWarning : System()
    data class Federation(val domainList: List<String>, val type: MessageEntity.FederationType) : System()
    data class LegalHold(val memberUserIdList: List<QualifiedIDEntity>, val type: MessageEntity.LegalHoldType) : System()
}

/**
 * Simplified model of [MessageEntity]
 * used everywhere where there is no need to have all the fields
 * for example in conversation list or notifications
 */
data class MessagePreviewEntity(
    val id: String,
    val conversationId: QualifiedIDEntity,
    val content: MessagePreviewEntityContent,
    val date: String,
    val visibility: MessageEntity.Visibility,
    val isSelfMessage: Boolean,
    val senderUserId: QualifiedIDEntity,
)

data class NotificationMessageEntity(
    val id: String,
    val contentType: MessageEntity.ContentType,
    val isSelfDelete: Boolean,
    val senderUserId: QualifiedIDEntity,
    val senderImage: UserAssetIdEntity?,

    val date: Instant,
    val senderName: String?,
    val text: String?,
    val assetMimeType: String?,
    val isQuotingSelf: Boolean,
    val conversationId: QualifiedIDEntity,
    val conversationName: String?,
    val mutedStatus: ConversationEntity.MutedStatus,
    val conversationType: ConversationEntity.Type,
)

sealed class MessagePreviewEntityContent {

    data class Deleted(val senderName: String?) : MessagePreviewEntityContent()
    data class Text(val senderName: String?, val messageBody: String) : MessagePreviewEntityContent()

    data class Composite(val senderName: String?, val messageBody: String?) : MessagePreviewEntityContent()

    data class Asset(val senderName: String?, val type: AssetTypeEntity) : MessagePreviewEntityContent()

    data class MentionedSelf(val senderName: String?, val messageBody: String) : MessagePreviewEntityContent()

    data class QuotedSelf(val senderName: String?, val messageBody: String) : MessagePreviewEntityContent()

    data class MissedCall(val senderName: String?) : MessagePreviewEntityContent()

    data class Knock(val senderName: String?) : MessagePreviewEntityContent()
    data class Location(val senderName: String?) : MessagePreviewEntityContent()

    data class MembersAdded(
        val senderName: String?,
        val otherUserIdList: List<UserIDEntity>,
        val isContainSelfUserId: Boolean,
    ) : MessagePreviewEntityContent()

    data class ConversationMembersRemoved(
        val senderName: String?,
        val otherUserIdList: List<UserIDEntity>,
        val isContainSelfUserId: Boolean,
    ) : MessagePreviewEntityContent()

    data class TeamMembersRemoved(
        val senderName: String?,
        val otherUserIdList: List<UserIDEntity>,
        val isContainSelfUserId: Boolean,
    ) : MessagePreviewEntityContent()

    data class MembersFailedToAdded(
        val senderName: String?,
        val otherUserIdList: List<UserIDEntity>,
        val isContainSelfUserId: Boolean,
    ) : MessagePreviewEntityContent()

    data class MembersCreationAdded(
        val senderName: String?,
        val otherUserIdList: List<UserIDEntity>,
        val isContainSelfUserId: Boolean,
    ) : MessagePreviewEntityContent()

    data class FederatedMembersRemoved(
        val otherUserIdList: List<UserIDEntity>,
        val isContainSelfUserId: Boolean,
    ) : MessagePreviewEntityContent()

    data class MemberJoined(val senderName: String?) : MessagePreviewEntityContent()

    data class MemberLeft(val senderName: String?) : MessagePreviewEntityContent()

    data class ConversationNameChange(val adminName: String?) : MessagePreviewEntityContent()

    @Deprecated("not maintained and will be deleted")
    @Suppress("ClassNaming")
    data class TeamMemberRemoved_Legacy(val userName: String?) : MessagePreviewEntityContent()
    data class Ephemeral(val isGroupConversation: Boolean) : MessagePreviewEntityContent()
    data object CryptoSessionReset : MessagePreviewEntityContent()
    data object ConversationVerifiedMls : MessagePreviewEntityContent()
    data object ConversationVerificationDegradedMls : MessagePreviewEntityContent()
    data object ConversationVerifiedProteus : MessagePreviewEntityContent()
    data object ConversationVerificationDegradedProteus : MessagePreviewEntityContent()
    object Unknown : MessagePreviewEntityContent()

}

enum class AssetTypeEntity {
    IMAGE,
    VIDEO,
    AUDIO,
    GENERIC_ASSET
}

typealias UnreadContentCountEntity = Map<MessageEntity.ContentType, Int>

/**
 * The type of the failure that happened when trying to deliver a message to a recipient.
 */
enum class RecipientFailureTypeEntity {
    /**
     * The message was not *attempted* to be delivered because there is no known clients for the recipient.
     * It will never be delivered for these recipients.
     */
    NO_CLIENTS_TO_DELIVER,

    /**
     * The message was not delivered "now" because of a communication error while the backend tried to deliver it.
     * It might be delivered later.
     */
    MESSAGE_DELIVERY_FAILED
}

sealed class DeliveryStatusEntity {
    data class PartialDelivery(
        val recipientsFailedWithNoClients: List<UserIDEntity>,
        val recipientsFailedDelivery: List<UserIDEntity>
    ) : DeliveryStatusEntity()

    data object CompleteDelivery : DeliveryStatusEntity()
}

data class MessageAssetStatusEntity(
    val id: String,
    val conversationId: QualifiedIDEntity,
    val transferStatus: AssetTransferStatusEntity
)

@Serializable
class ButtonEntity(
    @SerialName("text") val text: String,
    @SerialName("id") val id: String,
    @Serializable(with = BooleanIntSerializer::class)
    @SerialName("is_selected") val isSelected: Boolean
)

@OptIn(ExperimentalSerializationApi::class)
@Serializer(Boolean::class)
class BooleanIntSerializer : KSerializer<Boolean> {
    override val descriptor = PrimitiveSerialDescriptor("common_api_version", PrimitiveKind.INT)
    override fun serialize(encoder: Encoder, value: Boolean) {
        encoder.encodeInt(if (value) 1 else 0)
    }

    override fun deserialize(decoder: Decoder): Boolean = decoder.decodeInt() == 1
}
