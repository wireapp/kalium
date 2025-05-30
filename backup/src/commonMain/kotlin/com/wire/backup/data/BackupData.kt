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
@file:OptIn(ExperimentalObjCRefinement::class, ExperimentalObjCName::class)

package com.wire.backup.data

import kotlin.experimental.ExperimentalObjCName
import kotlin.experimental.ExperimentalObjCRefinement
import kotlin.js.JsExport
import kotlin.native.ObjCName
import kotlin.native.ShouldRefineInSwift

@JsExport
public class BackupData(
    public val metadata: BackupMetadata,
    @ShouldRefineInSwift
    public val users: Array<BackupUser>,
    @ShouldRefineInSwift
    public val conversations: Array<BackupConversation>,
    @ShouldRefineInSwift
    public val messages: Array<BackupMessage>
) {
    @ObjCName("users")
    public val userList: List<BackupUser> get() = users.toList()

    @ObjCName("conversations")
    public val conversationList: List<BackupConversation> get() = conversations.toList()

    @ObjCName("messages")
    public val messageList: List<BackupMessage> get() = messages.toList()
}

@JsExport
public data class BackupQualifiedId(
    val id: String,
    val domain: String,
) {
    override fun toString(): String = "$id@$domain"

    public companion object {
        private const val QUALIFIED_ID_COMPONENT_COUNT = 2

        public fun fromEncodedString(id: String): BackupQualifiedId? {
            val components = id.split("@")
            if (components.size != QUALIFIED_ID_COMPONENT_COUNT) return null
            return BackupQualifiedId(components[0], components[1])
        }
    }
}

@JsExport
public data class BackupUser(
    val id: BackupQualifiedId,
    val name: String,
    val handle: String,
)

@JsExport
public data class BackupConversation(
    val id: BackupQualifiedId,
    val name: String,
)

@JsExport
public data class BackupMessage(
    val id: String,
    val conversationId: BackupQualifiedId,
    val senderUserId: BackupQualifiedId,
    val senderClientId: String,
    val creationDate: BackupDateTime,
    val content: BackupMessageContent,
    @Deprecated("Used only by the Webteam in order to simplify debugging", ReplaceWith(""))
    val webPrimaryKey: Int? = null,
)

public expect class BackupDateTime

public expect fun BackupDateTime(timestampMillis: Long): BackupDateTime
public expect fun BackupDateTime.toLongMilliseconds(): Long

@JsExport
public sealed class BackupMessageContent {
    public data class Text(val text: String) : BackupMessageContent()

    public data class Asset(
        val mimeType: String,
        val size: Int,
        val name: String?,
        val otrKey: ByteArray,
        val sha256: ByteArray,
        val assetId: String,
        val assetToken: String?,
        val assetDomain: String?,
        val encryption: EncryptionAlgorithm?,
        val metaData: AssetMetadata?,
    ) : BackupMessageContent() {
        public enum class EncryptionAlgorithm {
            AES_GCM, AES_CBC
        }

        public sealed class AssetMetadata {
            public data class Image(
                val width: Int,
                val height: Int,
                val tag: String?
            ) : AssetMetadata()

            public data class Video(
                val width: Int?,
                val height: Int?,
                val duration: Long?,
            ) : AssetMetadata()

            public data class Audio(
                val normalization: ByteArray?,
                val duration: Long?,
            ) : AssetMetadata()

            public data class Generic(
                val name: String?,
            ) : AssetMetadata()
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other == null || this::class != other::class) return false

            other as Asset

            if (!otrKey.contentEquals(other.otrKey)) return false
            if (!sha256.contentEquals(other.sha256)) return false
            if (assetId != other.assetId) return false
            if (assetToken != other.assetToken) return false
            if (assetDomain != other.assetDomain) return false
            if (encryption != other.encryption) return false
            if (metaData != other.metaData) return false

            return true
        }

        override fun hashCode(): Int {
            var result = otrKey.contentHashCode()
            result = 31 * result + sha256.contentHashCode()
            result = 31 * result + assetId.hashCode()
            result = 31 * result + (assetToken?.hashCode() ?: 0)
            result = 31 * result + (assetDomain?.hashCode() ?: 0)
            result = 31 * result + (encryption?.hashCode() ?: 0)
            result = 31 * result + (metaData?.hashCode() ?: 0)
            return result
        }
    }

    public data class Location(
        val longitude: Float,
        val latitude: Float,
        val name: String?,
        val zoom: Int?,
    ) : BackupMessageContent()
}
