/* Wire, Copyright (C) 2026 Wire Swiss GmbH, SPDX-License-Identifier: GPL-3.0-or-later */
package com.wire.kalium.logic.sync.receiving

import com.wire.kalium.common.error.commonizeMLSException
import com.wire.kalium.common.functional.Either
import com.wire.kalium.cryptography.CommitBundle
import com.wire.kalium.cryptography.MLSTransporter
import com.wire.kalium.cryptography.MlsCoreCryptoContext
import com.wire.kalium.logic.data.conversation.MLSCommitBundleMapperImpl
import com.wire.kalium.logic.data.conversation.sendMLSCommitBundle
import com.wire.kalium.network.api.authenticated.notification.EventContentDTO
import com.wire.kalium.network.api.base.authenticated.message.MLSMessageApi
import com.wire.kalium.network.tools.KtxSerializer
import com.wire.kalium.persistence.dao.MetadataDAO
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.put
import okio.ByteString.Companion.toByteString

/** Permits commit transport only while an explicit existing-member external join is running. */
internal class ReceivedMLSRejoin(
    private val api: MLSMessageApi,
    private val metadata: MetadataDAO,
    private val diagnostics: ReceivingDiagnostics? = null,
) : MLSTransporter {
    private var joining = false
    private var unexpectedResponseSeen = false

    @Suppress("TooGenericExceptionCaught") // Normalize native errors without logging their messages.
    suspend fun join(context: MlsCoreCryptoContext, groupId: String, groupInfo: ByteArray): ULong {
        check(!joining) { "An external rejoin is already running" }
        joining = true
        return try {
            check(!unexpectedResponseSeen && metadata.valueByKey(UNEXPECTED_RESPONSE) == null) {
                "A previous external join returned unsupported events"
            }
            check(context.joinByExternalCommit(groupInfo) == groupId) { "External join changed the MLS group" }
            context.conversationEpoch(groupId)
        } catch (failure: CancellationException) {
            throw failure
        } catch (failure: Exception) {
            Either.Left(commonizeMLSException(failure).failure).orThrow()
        } finally {
            joining = false
        }
    }

    override suspend fun sendCommitBundle(commitBundle: CommitBundle) {
        check(joining) { "Only external rejoin transport is supported in NSE" }
        check(!unexpectedResponseSeen) { "A previous external join returned unsupported events" }
        diagnostics?.stage = "MLS_REJOIN_SEND"
        val events = sendMLSCommitBundle(api, commitBundle)
        // The external-commit branch is expected to return no events. Retain every
        // returned event before refusing later joins if this contract changes.
        if (events.isNotEmpty()) {
            unexpectedResponseSeen = true
            diagnostics?.unsupportedEvent()
            val body = MLSCommitBundleMapperImpl().toDTO(commitBundle).value
            val retained = buildJsonObject {
                put("bundleHash", body.toByteString().sha256().hex())
                put(
                    "events",
                    buildJsonArray {
                        events.forEach { event ->
                            // Unknown has a `type` property that collides with the sealed
                            // serializer's discriminator. Its model contains only that type.
                            add(
                                if (event is EventContentDTO.Unknown) {
                                    buildJsonObject { put("type", event.type) }
                                } else {
                                    KtxSerializer.json.encodeToJsonElement<EventContentDTO>(event)
                                }
                            )
                        }
                    }
                )
            }
            metadata.insertValue(KtxSerializer.json.encodeToString(retained), UNEXPECTED_RESPONSE)
            error("External rejoin returned unsupported events")
        }
    }

    private companion object {
        const val UNEXPECTED_RESPONSE = "nse_mls_rejoin_unexpected_response_v1"
    }
}
