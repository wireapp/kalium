package com.wire.kalium.logic.data.message

import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.logic.kaliumLogger
import com.wire.kalium.protobuf.decodeFromByteArray
import com.wire.kalium.protobuf.encodeToByteArray
import com.wire.kalium.protobuf.messages.Asset
import com.wire.kalium.protobuf.messages.Asset.Original
import com.wire.kalium.protobuf.messages.Calling
import com.wire.kalium.protobuf.messages.EncryptionAlgorithm
import com.wire.kalium.protobuf.messages.GenericMessage
import com.wire.kalium.protobuf.messages.Text
import pbandk.ByteArr

interface ProtoContentMapper {
    fun encodeToProtobuf(protoContent: ProtoContent): PlainMessageBlob
    fun decodeFromProtobuf(encodedContent: PlainMessageBlob): ProtoContent
}

class ProtoContentMapperImpl : ProtoContentMapper {

    override fun encodeToProtobuf(protoContent: ProtoContent): PlainMessageBlob {
        val (messageUid, messageContent) = protoContent

        val content = when (messageContent) {
            is MessageContent.Text -> {
                GenericMessage.Content.Text(Text(content = messageContent.value))
            }
            is MessageContent.Calling -> {
                GenericMessage.Content.Calling(Calling(content = messageContent.value))
            }
            is MessageContent.Asset -> {
                with(messageContent.value) {
                    GenericMessage.Content.Asset(
                        Asset(
                            original = Original(
                                mimeType = mimeType,
                                size = size.toLong(),
                                name = name,
                                metaData = when (metadata) {
                                    is AssetContent.AssetMetadata.Image -> Original.MetaData.Image(
                                        Asset.ImageMetaData(
                                            width = metadata.width,
                                            height = metadata.height,
                                        )
                                    )
                                    else -> null
                                }
                            ),
                            status = Asset.Status.Uploaded(
                                uploaded = Asset.RemoteData(
                                    otrKey = ByteArr(remoteData.otrKey),
                                    sha256 = ByteArr(remoteData.sha256),
                                    assetId = remoteData.assetId,
                                    assetToken = remoteData.assetToken,
                                    assetDomain = remoteData.assetDomain,
                                    encryption = when (remoteData.encryptionAlgorithm) {
                                        AssetContent.RemoteData.EncryptionAlgorithm.AES_CBC -> EncryptionAlgorithm.AES_CBC
                                        AssetContent.RemoteData.EncryptionAlgorithm.AES_GCM -> EncryptionAlgorithm.AES_GCM
                                        else -> EncryptionAlgorithm.AES_CBC
                                    }
                                )
                            ),
                        )
                    )
                }
            }
            else -> {
                throw IllegalArgumentException("Unexpected message content type: $messageContent")
            }
        }

        val message = GenericMessage(messageUid, content)
        return PlainMessageBlob(message.encodeToByteArray())
    }

    override fun decodeFromProtobuf(encodedContent: PlainMessageBlob): ProtoContent {
        val genericMessage = GenericMessage.decodeFromByteArray(encodedContent.data)

        kaliumLogger.d("Received message $genericMessage")
        val content = when (val protoContent = genericMessage.content) {
            is GenericMessage.Content.Text -> MessageContent.Text(protoContent.value.content)
            is GenericMessage.Content.Asset -> MessageContent.Asset(
                MapperProvider.assetMapper().fromProtoAssetMessageToAssetContent(protoContent.value)
            )
            is GenericMessage.Content.Availability -> MessageContent.Unknown
            is GenericMessage.Content.ButtonAction -> MessageContent.Unknown
            is GenericMessage.Content.ButtonActionConfirmation -> MessageContent.Unknown
            is GenericMessage.Content.Calling -> MessageContent.Calling(value = protoContent.value.content)
            is GenericMessage.Content.Cleared -> MessageContent.Unknown
            is GenericMessage.Content.ClientAction -> MessageContent.Unknown
            is GenericMessage.Content.Composite -> MessageContent.Unknown
            is GenericMessage.Content.Confirmation -> MessageContent.Unknown
            is GenericMessage.Content.DataTransfer -> MessageContent.Unknown
            is GenericMessage.Content.Deleted -> MessageContent.Unknown
            is GenericMessage.Content.Edited -> MessageContent.Unknown
            is GenericMessage.Content.Ephemeral -> MessageContent.Unknown
            is GenericMessage.Content.External -> MessageContent.Unknown
            is GenericMessage.Content.Hidden -> MessageContent.Unknown
            is GenericMessage.Content.Image -> MessageContent.Unknown // Deprecated in favor of GenericMessage.Content.Asset
            is GenericMessage.Content.Knock -> MessageContent.Unknown
            is GenericMessage.Content.LastRead -> MessageContent.Unknown
            is GenericMessage.Content.Location -> MessageContent.Unknown
            is GenericMessage.Content.Reaction -> MessageContent.Unknown
            else -> {
                kaliumLogger.w("Null content when parsing protobuf. Message UUID = $genericMessage.")
                MessageContent.Unknown
            }
        }

        return ProtoContent(genericMessage.messageId, content)
    }
}
