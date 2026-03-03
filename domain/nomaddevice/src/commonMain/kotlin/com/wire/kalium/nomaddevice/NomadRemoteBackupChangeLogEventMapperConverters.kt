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

package com.wire.kalium.nomaddevice

import com.wire.kalium.network.api.authenticated.nomaddevice.Conversation
import com.wire.kalium.network.api.authenticated.nomaddevice.ReadReceiptEntry
import com.wire.kalium.network.api.authenticated.nomaddevice.ReadReceiptsPayload
import com.wire.kalium.network.api.authenticated.nomaddevice.ReactionByUser
import com.wire.kalium.network.api.authenticated.nomaddevice.ReactionsPayload
import com.wire.kalium.network.api.model.QualifiedID
import com.wire.kalium.persistence.dao.QualifiedIDEntity
import com.wire.kalium.persistence.dao.message.MessageEntity
import com.wire.kalium.persistence.dao.message.attachment.MessageAttachmentEntity
import com.wire.kalium.persistence.dao.reaction.MessageReactionsSyncEntity
import com.wire.kalium.persistence.dao.reaction.UserReactionsSyncEntity
import com.wire.kalium.persistence.dao.receipt.MessageReadReceiptsSyncEntity
import com.wire.kalium.persistence.dao.receipt.UserReadReceiptSyncEntity
import com.wire.kalium.protobuf.nomaddevice.NomadDeviceAsset
import com.wire.kalium.protobuf.nomaddevice.NomadDeviceAttachment
import com.wire.kalium.protobuf.nomaddevice.NomadDeviceAudioMetaData
import com.wire.kalium.protobuf.nomaddevice.NomadDeviceGenericMetaData
import com.wire.kalium.protobuf.nomaddevice.NomadDeviceImageMetaData
import com.wire.kalium.protobuf.nomaddevice.NomadDeviceMention
import com.wire.kalium.protobuf.nomaddevice.NomadDeviceQualifiedId
import com.wire.kalium.protobuf.nomaddevice.NomadDeviceVideoMetaData
import pbandk.ByteArr

internal fun MessageReactionsSyncEntity.toReactionPayload() = ReactionsPayload(
    reactionsByUser = reactionsByUser.map { it.toReactionByUser() }
)

internal fun UserReactionsSyncEntity.toReactionByUser() = ReactionByUser(
    userId = userId.toApiQualifiedId(),
    emojis = emojis.sorted()
)

internal fun MessageReadReceiptsSyncEntity.toReadReceiptsPayload() = ReadReceiptsPayload(
    readReceipts = receipts.map { it.toReadReceiptEntry() }
)

internal fun UserReadReceiptSyncEntity.toReadReceiptEntry() = ReadReceiptEntry(
    userId = userId.toApiQualifiedId(),
    date = date.toString()
)

internal fun QualifiedIDEntity.toApiConversation(): Conversation = Conversation(id = value, domain = domain)

internal fun QualifiedIDEntity.toApiQualifiedId() = QualifiedID(value = value, domain = domain)

internal fun QualifiedIDEntity.toNomadDeviceQualifiedId(): NomadDeviceQualifiedId =
    NomadDeviceQualifiedId(value = value, domain = domain)

internal fun List<MessageEntity.Mention>.toNomadDeviceMentions(): List<NomadDeviceMention> =
    map { mention ->
        NomadDeviceMention(
            userId = mention.userId.toNomadDeviceQualifiedId(),
            start = mention.start,
            length = mention.length
        )
    }

@Suppress("CyclomaticComplexMethod")
internal fun List<MessageAttachmentEntity>.toNomadDeviceAttachments(): List<NomadDeviceAttachment> =
    filter { !it.cellAsset }.map { attachment ->
        NomadDeviceAttachment(
            content = NomadDeviceAttachment.Content.Asset(
                NomadDeviceAsset(
                    mimeType = attachment.mimeType,
                    size = attachment.assetSize ?: 0L,
                    name = attachment.assetPath,
                    otrKey = ByteArr(byteArrayOf()),
                    sha256 = ByteArr(byteArrayOf()),
                    assetId = attachment.assetId,
                    metaData = when {
                        attachment.assetDuration != null && (attachment.assetWidth != null || attachment.assetHeight != null) ->
                            NomadDeviceAsset.MetaData.Video(
                                NomadDeviceVideoMetaData(
                                    width = attachment.assetWidth,
                                    height = attachment.assetHeight,
                                    durationInMillis = attachment.assetDuration
                                )
                            )

                        attachment.assetDuration != null ->
                            NomadDeviceAsset.MetaData.Audio(
                                NomadDeviceAudioMetaData(durationInMillis = attachment.assetDuration)
                            )

                        attachment.assetWidth != null || attachment.assetHeight != null ->
                            NomadDeviceAsset.MetaData.Image(
                                NomadDeviceImageMetaData(
                                    width = attachment.assetWidth ?: 0,
                                    height = attachment.assetHeight ?: 0
                                )
                            )

                        attachment.assetPath != null ->
                            NomadDeviceAsset.MetaData.Generic(
                                NomadDeviceGenericMetaData(name = attachment.assetPath)
                            )

                        else -> null
                    }
                )
            )
        )
    }
