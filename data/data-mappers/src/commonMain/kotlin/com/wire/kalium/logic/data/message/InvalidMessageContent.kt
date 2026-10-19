/* Wire, Copyright (C) 2026 Wire Swiss GmbH, SPDX-License-Identifier: GPL-3.0-or-later */
package com.wire.kalium.logic.data.message

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.logger.kaliumLogger
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import pbandk.InvalidProtocolBufferException

/** Authenticated application data that cannot be parsed. Consume it without retrying or resetting crypto. */
data object InvalidMessageContent : CoreFailure.FeatureFailure()

/** Keep data exceptions inside the message boundary, before returning from the crypto transaction. */
fun ProtoContentMapper.decodeFromProtobufSafely(encodedContent: PlainMessageBlob): Either<InvalidMessageContent, ProtoContent> =
    try {
        Either.Right(decodeFromProtobuf(encodedContent))
    } catch (exception: CancellationException) {
        throw exception
    } catch (_: InvalidProtocolBufferException) {
        invalidMessageContent("protobuf")
    } catch (_: IllegalArgumentException) {
        // Includes invalid History timestamps. Only protobuf mapping is inside this boundary, not dispatch or storage.
        invalidMessageContent("protobuf field")
    }

fun MessageContent.Calling.decodeCallingValue(json: Json): Either<InvalidMessageContent, MessageContent.Calling.CallingValue> =
    try {
        Either.Right(json.decodeFromString<MessageContent.Calling.CallingValue>(value))
    } catch (exception: CancellationException) {
        throw exception
    } catch (_: SerializationException) {
        invalidMessageContent("calling JSON")
    }

private fun invalidMessageContent(format: String): Either.Left<InvalidMessageContent> {
    // Parser exception messages and causes can contain plaintext, keys or the complete JSON input.
    kaliumLogger.w("Invalid application message discarded: $format")
    return Either.Left(InvalidMessageContent)
}
