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

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
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
@Serializable
public data class BackupQualifiedId(
    @SerialName("id")
    val id: String,
    @SerialName("domain")
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
@Serializable
public data class BackupUser(
    @SerialName("id")
    val id: BackupQualifiedId,
    @SerialName("name")
    val name: String,
    @SerialName("handle")
    val handle: String,
)

@JsExport
@Serializable
public data class BackupConversation(
    @SerialName("id")
    val id: BackupQualifiedId,
    @SerialName("name")
    val name: String,
    val lastModifiedTime: BackupDateTime? = null,
)

@JsExport
@Serializable
public data class BackupMessage(
    @SerialName("id")
    val id: String,
    @SerialName("conversationId")
    val conversationId: BackupQualifiedId,
    @SerialName("senderUserId")
    val senderUserId: BackupQualifiedId,
    @SerialName("senderClientId")
    val senderClientId: String,
    @SerialName("creationDate")
    val creationDate: BackupDateTime,
    @SerialName("content")
    val content: BackupMessageContent,
    @SerialName("webPrimaryKey")
    @Deprecated("Used only by the Webteam in order to simplify debugging", ReplaceWith(""))
    val webPrimaryKey: Int? = null,
    @SerialName("lastEditTime")
    val lastEditTime: BackupDateTime? = null,
)

@Serializable(BackupDateTimeSerializer::class)
public expect class BackupDateTime

public expect fun BackupDateTime(timestampMillis: Long): BackupDateTime
public expect fun BackupDateTime.toLongMilliseconds(): Long

@JsExport
@Serializable
public sealed class BackupMessageContent {

    @Serializable
    public data class Text(val text: String) : BackupMessageContent()

    @Serializable
    public data class Asset(
        @SerialName("mimeType")
        val mimeType: String,
        @SerialName("size")
        val size: Int,
        @SerialName("name")
        val name: String?,
        @Serializable(with = ByteArrayStringSerializer::class)
        @SerialName("otrKey")
        val otrKey: ByteArray,
        @Serializable(with = ByteArrayStringSerializer::class)
        @SerialName("sha256")
        val sha256: ByteArray,
        @SerialName("assetId")
        val assetId: String,
        @SerialName("assetToken")
        val assetToken: String?,
        @SerialName("assetDomain")
        val assetDomain: String?,
        @SerialName("encryption")
        val encryption: EncryptionAlgorithm?,
        @SerialName("metaData")
        val metaData: AssetMetadata?,
    ) : BackupMessageContent() {

        @Serializable
        public enum class EncryptionAlgorithm {
            AES_GCM, AES_CBC
        }

        @Serializable
        public sealed class AssetMetadata {

            @Serializable
            public data class Image(
                @SerialName("width")
                val width: Int,
                @SerialName("height")
                val height: Int,
                @SerialName("tag")
                val tag: String?
            ) : AssetMetadata()

            @Serializable
            public data class Video(
                @SerialName("width")
                val width: Int?,
                @SerialName("height")
                val height: Int?,
                @SerialName("duration")
                val duration: Long?,
            ) : AssetMetadata()

            @Serializable
            public data class Audio(
                @Serializable(with = ByteArrayStringSerializer::class)
                @SerialName("normalization")
                val normalization: ByteArray?,
                @SerialName("duration")
                val duration: Long?,
            ) : AssetMetadata()

            @Serializable
            public data class Generic(
                @SerialName("name")
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

    @Serializable
    public data class Location(
        @SerialName("longitude")
        val longitude: Float,
        @SerialName("latitude")
        val latitude: Float,
        @SerialName("name")
        val name: String?,
        @SerialName("zoom")
        val zoom: Int?,
    ) : BackupMessageContent()
}
