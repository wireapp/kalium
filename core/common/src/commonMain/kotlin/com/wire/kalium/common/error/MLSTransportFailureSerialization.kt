/*
 * Wire
 * Copyright (C) 2025 Wire Swiss GmbH
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
package com.wire.kalium.common.error

import com.wire.kalium.network.tools.KtxSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal sealed interface MLSTransportFailure {
    @Serializable
    data class MessageRejected(
        @SerialName("reason") val reason: NetworkFailure.MlsMessageRejectedFailure
    ) : MLSTransportFailure

    @Serializable
    data class Other(
        @SerialName("message") val message: String
    ) : MLSTransportFailure
}

object MLSTransportFailureSerialization {
    fun parseString(string: String): MLSFailure {
        return when (val failure = KtxSerializer.json.decodeFromString<MLSTransportFailure>(string)) {
            is MLSTransportFailure.MessageRejected -> MLSFailure.MessageRejected(failure.reason)
            is MLSTransportFailure.Other -> MLSFailure.Other(failure.message)
        }
    }

    fun serialize(failure: CoreFailure): String {
        val transportFailure = when (val failure = failure.wrapNetworkMlsFailureIfApplicable()) {
            is MLSFailure.MessageRejected -> {
                MLSTransportFailure.MessageRejected(failure.cause)
            }

            else -> MLSTransportFailure.Other(failure.toString())
        }
        return KtxSerializer.json.encodeToString(MLSTransportFailure.serializer(), transportFailure)
    }
}
