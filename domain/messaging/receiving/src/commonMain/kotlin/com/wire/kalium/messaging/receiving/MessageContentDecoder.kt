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

package com.wire.kalium.messaging.receiving

import com.wire.kalium.cryptography.utils.AES256Key
import com.wire.kalium.cryptography.utils.EncryptedData
import com.wire.kalium.cryptography.utils.decryptDataWithAES256

/**
 * Narrow protobuf decoding boundary used by receive-only cryptography.
 *
 * The full protobuf-to-application mapper remains outside this module. Milestone 3 can replace its
 * adapter with a dedicated codec without changing the cryptographic receiving contracts.
 */
public fun interface MessageContentDecoder<Content : Any> {
    public fun decode(serializedContent: ByteArray): DecodedMessageContent<Content>
}

/** Result of inspecting one decrypted GenericMessage protobuf. */
public sealed interface DecodedMessageContent<out Content : Any> {
    /** A protobuf containing application-readable content. */
    public data class Application<Content : Any>(public val content: Content) : DecodedMessageContent<Content>

    /** A protobuf containing the key required to decrypt the separately transported content. */
    public class ExternalInstructions(otrKey: ByteArray) : DecodedMessageContent<Nothing> {
        private val otrKeyBytes: ByteArray = otrKey.copyOf()
        public val otrKey: ByteArray get() = otrKeyBytes.copyOf()

        override fun equals(other: Any?): Boolean =
            this === other || other is ExternalInstructions && otrKeyBytes.contentEquals(other.otrKeyBytes)

        override fun hashCode(): Int = otrKeyBytes.contentHashCode()
    }
}

/**
 * Application content together with the exact protobuf bytes from which it was decoded.
 */
public class DecryptedApplicationMessage<Content : Any>(
    serializedContent: ByteArray,
    content: Content
) {
    private val serializedContentBytes: ByteArray = serializedContent.copyOf()
    public val serializedContent: ByteArray get() = serializedContentBytes.copyOf()
    public val content: Content = content

    override fun equals(other: Any?): Boolean =
        this === other || other is DecryptedApplicationMessage<*> &&
                serializedContentBytes.contentEquals(other.serializedContentBytes) && content == other.content

    override fun hashCode(): Int = 31 * serializedContentBytes.contentHashCode() + content.hashCode()
}

/** Result of resolving direct or separately transported Proteus application content. */
public sealed interface MessageContentResolution<out Content : Any> {
    public data class Success<Content : Any>(
        public val message: DecryptedApplicationMessage<Content>
    ) : MessageContentResolution<Content>

    /** The external-content envelope was absent or recursively contained another envelope. */
    public data class InvalidExternalContent(
        public val cause: IllegalArgumentException
    ) : MessageContentResolution<Nothing>
}

/** Resolves direct and Proteus external content without application persistence or side effects. */
public interface MessageContentResolver {
    public fun <Content : Any> resolveProteusContent(
        decryptedMessage: ByteArray,
        encryptedExternalContent: ByteArray?,
        decoder: MessageContentDecoder<Content>
    ): MessageContentResolution<Content>
}

public class MessageContentResolverImpl : MessageContentResolver {
    override fun <Content : Any> resolveProteusContent(
        decryptedMessage: ByteArray,
        encryptedExternalContent: ByteArray?,
        decoder: MessageContentDecoder<Content>
    ): MessageContentResolution<Content> =
        when (val decodedContent = decoder.decode(decryptedMessage.copyOf())) {
            is DecodedMessageContent.Application -> MessageContentResolution.Success(
                DecryptedApplicationMessage(decryptedMessage, decodedContent.content)
            )

            is DecodedMessageContent.ExternalInstructions -> encryptedExternalContent?.let { externalContent ->
                decryptExternalContent(
                    externalInstructions = decodedContent,
                    encryptedExternalContent = externalContent,
                    decoder = decoder
                )
            } ?: MessageContentResolution.InvalidExternalContent(
                IllegalArgumentException("Null external content when processing external message instructions.")
            )
        }

    private fun <Content : Any> decryptExternalContent(
        externalInstructions: DecodedMessageContent.ExternalInstructions,
        encryptedExternalContent: ByteArray,
        decoder: MessageContentDecoder<Content>
    ): MessageContentResolution<Content> {
        val decryptedExternalContent = decryptDataWithAES256(
            EncryptedData(encryptedExternalContent),
            AES256Key(externalInstructions.otrKey)
        ).data
        return when (val decodedExternalContent = decoder.decode(decryptedExternalContent.copyOf())) {
            is DecodedMessageContent.Application -> MessageContentResolution.Success(
                DecryptedApplicationMessage(decryptedExternalContent, decodedExternalContent.content)
            )

            is DecodedMessageContent.ExternalInstructions -> MessageContentResolution.InvalidExternalContent(
                IllegalArgumentException("матрёшка! External message can't contain another external message inside!")
            )
        }
    }
}
