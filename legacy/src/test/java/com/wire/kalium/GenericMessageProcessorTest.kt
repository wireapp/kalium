package com.wire.kalium

import com.google.protobuf.ByteString
import com.waz.model.Messages
import com.waz.model.Messages.Asset.*
import com.waz.model.Messages.GenericMessage
import com.wire.kalium.models.inbound.AudioPreviewMessage
import com.wire.kalium.models.inbound.MessageBase
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import java.util.*


class GenericMessageProcessorTest {
    //@Test
    fun testLinkPreview() {
        val handler = MessageHandler()
        val processor = GenericMessageProcessor(getTestClient(), handler)
        val eventId = UUID.randomUUID()
        val messageId = UUID.randomUUID()
        val from = UUID.randomUUID()
        val convId = UUID.randomUUID()
        val sender = UUID.randomUUID().toString()
        val time = Date().toString()
        val image = ImageMetaData.newBuilder()
            .setHeight(HEIGHT)
            .setWidth(WIDTH)
        val original = Original.newBuilder()
            .setSize(SIZE.toLong())
            .setMimeType(MIME_TYPE)
            .setImage(image)
        val uploaded = RemoteData.newBuilder()
            .setAssetId(ASSET_KEY)
            .setAssetToken(ASSET_TOKEN)
            .setOtrKey(ByteString.EMPTY)
            .setSha256(ByteString.EMPTY)
        val asset = Messages.Asset.newBuilder()
            .setOriginal(original)
            .setUploaded(uploaded)
        val linkPreview = Messages.LinkPreview.newBuilder()
            .setTitle(TITLE)
            .setSummary(SUMMARY)
            .setUrl(URL)
            .setUrlOffset(URL_OFFSET)
            .setImage(asset)
        val text = Messages.Text.newBuilder()
            .setContent(CONTENT)
            .addLinkPreview(linkPreview)
        val builder = GenericMessage.newBuilder()
            .setMessageId(messageId.toString())
            .setText(text)
        val msgBase = MessageBase(eventId, messageId, convId, sender, from, time)
        processor.process(msgBase, builder.build())
    }

    //@Test
    fun testAudioOrigin() {
        val handler = MessageHandler()
        val processor = GenericMessageProcessor(getTestClient(), handler)
        val eventId = UUID.randomUUID()
        val messageId = UUID.randomUUID()
        val from = UUID.randomUUID()
        val convId = UUID.randomUUID()
        val sender = UUID.randomUUID().toString()
        val time = Date().toString()
        val levels = ByteArray(100)
        Random().nextBytes(levels)
        val audioMeta = AudioMetaData.newBuilder()
            .setDurationInMillis(DURATION.toLong())
            .setNormalizedLoudness(ByteString.copyFrom(levels))
        val original = Original.newBuilder()
            .setSize(SIZE.toLong())
            .setName(NAME)
            .setMimeType(AUDIO_MIME_TYPE)
            .setAudio(audioMeta)
        val asset = Messages.Asset.newBuilder()
            .setOriginal(original)
        val builder = GenericMessage.newBuilder()
            .setMessageId(messageId.toString())
            .setAsset(asset)
        val msgBase = MessageBase(eventId, messageId, convId, sender, from, time)
        processor.process(msgBase, builder.build())
    }

    //@Test
    fun testAudioUploaded() {
        val handler = MessageHandler()
        val processor = GenericMessageProcessor(getTestClient(), handler)
        val eventId = UUID.randomUUID()
        val messageId = UUID.randomUUID()
        val from = UUID.randomUUID()
        val convId = UUID.randomUUID()
        val sender = UUID.randomUUID().toString()
        val time = Date().toString()
        val levels = ByteArray(100)
        Random().nextBytes(levels)
        val uploaded = RemoteData.newBuilder()
            .setAssetId(ASSET_KEY)
            .setAssetToken(ASSET_TOKEN)
            .setOtrKey(ByteString.EMPTY)
            .setSha256(ByteString.EMPTY)
        val asset = Messages.Asset.newBuilder()
            .setUploaded(uploaded)
        val builder = GenericMessage.newBuilder()
            .setMessageId(messageId.toString())
            .setAsset(asset)
        val msgBase = MessageBase(eventId, messageId, convId, sender, from, time)
        processor.process(msgBase, builder.build())
    }

    private fun getTestClient(): IWireClient {
        TODO("Fake it? Mock it?")
    }

    private class MessageHandler : com.wire.kalium.MessageHandler {
        // TODO: LinkPreviewMessage
        /*
        override fun onLinkPreview(client: WireClient, msg: LinkPreviewMessage) {
            Assertions.assertEquals(TITLE, msg.getTitle())
            Assertions.assertEquals(SUMMARY, msg.getSummary())
            Assertions.assertEquals(URL, msg.getUrl())
            Assertions.assertEquals(URL_OFFSET, msg.getUrlOffset())
            Assertions.assertEquals(CONTENT, msg.getText())
            Assertions.assertEquals(WIDTH, msg.getWidth())
            Assertions.assertEquals(HEIGHT, msg.getHeight())
            Assertions.assertEquals(SIZE.toLong(), msg.getSize())
            Assertions.assertEquals(MIME_TYPE, msg.getMimeType())
            Assertions.assertEquals(ASSET_TOKEN, msg.getAssetToken())
        }
    */

        override fun onAudioPreview(client: IWireClient, msg: AudioPreviewMessage) {
            Assertions.assertEquals(AUDIO_MIME_TYPE, msg.mimeType)
        }
    }

    companion object {
        const val AUDIO_MIME_TYPE: String = "audio/x-m4a"
        const val NAME: String = "audio.m4a"
        const val DURATION = 27000
        private const val TITLE: String = "title"
        private const val SUMMARY: String = "summary"
        private const val URL: String = "https://wire.com"
        private const val CONTENT: String = "This is https://wire.com"
        private const val URL_OFFSET = 8
        private const val ASSET_KEY: String = "key"
        private const val ASSET_TOKEN: String = "token"
        private const val HEIGHT = 43
        private const val WIDTH = 84
        private const val SIZE = 123
        private const val MIME_TYPE: String = "image/png"
    }
}
