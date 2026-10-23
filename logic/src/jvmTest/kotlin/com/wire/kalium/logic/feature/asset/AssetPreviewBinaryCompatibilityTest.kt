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
package com.wire.kalium.logic.feature.asset

import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.QualifiedID
import com.wire.kalium.logic.data.message.AssetContent
import com.wire.kalium.logic.data.message.MessageEncryptionAlgorithm
import com.wire.kalium.logic.data.message.timeline.MessageListAsset
import com.wire.kalium.logic.data.user.AssetId
import com.wire.kalium.logic.feature.asset.upload.AssetPreviewUploadParams
import com.wire.kalium.logic.feature.asset.upload.AssetUploadParams
import okio.Path
import okio.Path.Companion.toPath
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertNull

class AssetPreviewBinaryCompatibilityTest {
    private val remote = AssetContent.RemoteData(
        otrKey = ByteArray(32) { 1 },
        sha256 = ByteArray(32) { 2 },
        assetId = "video-id",
        assetToken = null,
        assetDomain = "domain",
        encryptionAlgorithm = MessageEncryptionAlgorithm.AES_CBC,
    )
    private val preview = AssetContent.Preview("image/jpeg", 1024, 512, 288, remote.copy(assetId = "preview-id"))

    @Test
    @Suppress("DEPRECATION")
    fun legacyKotlinCallsRemainUnambiguousAndCopiesRetainPreview() {
        val content = AssetContent(100_000, "video.mp4", "video/mp4", null, remote)
        assertNull(content.preview)
        assertEquals(preview, content.copy(preview = preview).copy(name = "renamed.mp4").preview)

        val params = AssetUploadParams(
            ConversationId("conversation", "domain"), "video.mp4".toPath(),
            100_000, "video.mp4", "video/mp4", 1920, 1080, 1000, null,
        )
        val uploadPreview = AssetPreviewUploadParams("preview.jpg".toPath(), 1024, "image/jpeg", 512, 288)
        assertNull(params.preview)
        assertEquals(uploadPreview, params.copy(preview = uploadPreview).copy(assetName = "renamed.mp4").preview)

        val asset = MessageListAsset(AssetId("video-id", "domain"), "video.mp4", "video/mp4", 100_000, 1920, 1080, 1000, true)
        val previewId = AssetId("preview-id", "domain")
        assertNull(asset.previewAssetId)
        assertEquals(previewId, asset.copy(previewAssetId = previewId).copy(name = "renamed.mp4").previewAssetId)
    }

    @Test
    fun uploadParamsRetainLegacyConstructorAndBothCopyDescriptors() {
        val path = "video.mp4".toPath()
        val conversation = ConversationId("conversation", "domain")
        val original = AssetUploadParams(
            conversationId = conversation,
            assetDataPath = path,
            assetDataSize = 100_000,
            assetName = "video.mp4",
            assetMimeType = "video/mp4",
            assetWidth = 1920,
            assetHeight = 1080,
            audioLengthInMs = 1000,
            audioNormalizedLoudness = null,
            preview = AssetPreviewUploadParams("preview.jpg".toPath(), 1024, "image/jpeg", 512, 288),
        )
        assertLegacyDescriptors(
            original,
            arrayOf(
                QualifiedID::class.java,
                Path::class.java,
                Long::class.javaPrimitiveType!!,
                String::class.java,
                String::class.java,
                Int::class.javaObjectType,
                Int::class.javaObjectType,
                Long::class.javaPrimitiveType!!,
                ByteArray::class.java,
            ),
            arrayOf(conversation, path, 100_000L, "video.mp4", "video/mp4", 1920, 1080, 1000L, null),
            "getPreview",
        )
    }

    @Test
    fun assetContentRetainsLegacyConstructorAndBothCopyDescriptors() {
        val metadata = AssetContent.AssetMetadata.Video(1920, 1080, 1000)
        val content = AssetContent(100_000, "video.mp4", "video/mp4", metadata, remote, preview)
        assertLegacyDescriptors(
            content,
            contentParameterTypes(),
            arrayOf(100_000L, "video.mp4", "video/mp4", metadata, remote),
            "getPreview",
        )
    }

    @Test
    fun assetContentRetainsLegacyDefaultArgumentConstructor() {
        val parameterTypes = contentParameterTypes() + arrayOf(
            Int::class.javaPrimitiveType!!,
            Class.forName("kotlin.jvm.internal.DefaultConstructorMarker"),
        )
        val constructor = AssetContent::class.java.getDeclaredConstructor(*parameterTypes)
        val defaultNameAndMetadataMask = (1 shl 1) or (1 shl 3)
        val content = constructor.newInstance(
            100_000L,
            "ignored-name.mp4",
            "video/mp4",
            AssetContent.AssetMetadata.Video(1920, 1080, 1000),
            remote,
            defaultNameAndMetadataMask,
            null,
        )
        assertEquals(100_000L, content.sizeInBytes)
        assertNull(content.name)
        assertNull(content.metadata)
        assertNull(content.preview)
        assertEquals(remote, content.remoteData)
    }

    @Test
    fun timelineAssetRetainsLegacyConstructorAndBothCopyDescriptors() {
        val id = AssetId("video-id", "domain")
        val asset = MessageListAsset(id, "video.mp4", "video/mp4", 100_000, 1920, 1080, 1000, true, AssetId("preview-id", "domain"))
        assertLegacyDescriptors(
            asset,
            arrayOf(
                QualifiedID::class.java,
                String::class.java,
                String::class.java,
                Long::class.javaPrimitiveType!!,
                Int::class.javaObjectType,
                Int::class.javaObjectType,
                Long::class.javaObjectType,
                Boolean::class.javaPrimitiveType!!,
            ),
            arrayOf(id, "video.mp4", "video/mp4", 100_000L, 1920, 1080, 1000L, true),
            "getPreviewAssetId",
        )
    }

    private fun contentParameterTypes(): Array<Class<*>> = arrayOf(
        Long::class.javaPrimitiveType!!,
        String::class.java,
        String::class.java,
        AssetContent.AssetMetadata::class.java,
        AssetContent.RemoteData::class.java,
    )

    private fun <T : Any> assertLegacyDescriptors(
        original: T,
        parameterTypes: Array<Class<*>>,
        arguments: Array<Any?>,
        previewGetterName: String,
    ) {
        val type = original.javaClass
        val previewGetter = type.getDeclaredMethod(previewGetterName)
        val oldConstructor = type.getDeclaredConstructor(*parameterTypes)
        assertNull(previewGetter.invoke(oldConstructor.newInstance(*arguments)))

        val oldCopy = type.getDeclaredMethod("copy", *parameterTypes)
        assertEquals(type, oldCopy.returnType)
        val copied = oldCopy.invoke(original, *arguments)
        assertNotSame(original, copied)
        assertEquals(original, copied)
        assertEquals(previewGetter.invoke(original), previewGetter.invoke(copied))

        val defaultParameterTypes = buildList<Class<*>> {
            add(type)
            addAll(parameterTypes)
            add(Int::class.javaPrimitiveType!!)
            add(Any::class.java)
        }.toTypedArray()
        val oldDefaultCopy = type.getDeclaredMethod("copy\$default", *defaultParameterTypes)
        assertEquals(type, oldDefaultCopy.returnType)
        val defaultArguments = buildList<Any?> {
            add(original)
            addAll(arguments)
            add((1 shl parameterTypes.size) - 1)
            add(null)
        }.toTypedArray()
        val defaultCopy = oldDefaultCopy.invoke(null, *defaultArguments)
        assertNotSame(original, defaultCopy)
        assertEquals(original, defaultCopy)
        assertEquals(previewGetter.invoke(original), previewGetter.invoke(defaultCopy))
    }
}
