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

package com.wire.kalium.network.api.model.sync

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * Request to upload a batch of database operations to the server.
 */
@Serializable
data class SyncOperationRequest(
    @SerialName("batch_id") val batchId: String,
    @SerialName("device_id") val deviceId: String,
    @SerialName("operations") val operations: List<OperationDTO>
)

/**
 * Single database operation (INSERT/UPDATE/DELETE) to be synced.
 */
@Serializable
data class OperationDTO(
    @SerialName("sequence_id") val sequenceId: Long,
    @SerialName("table") val table: String,
    @SerialName("operation") val operation: String,
    @SerialName("row_key") val rowKey: JsonElement,
    @SerialName("row_data") val rowData: JsonElement?,
    @SerialName("timestamp") val timestamp: String
)

/**
 * Response from server after processing a batch of sync operations.
 */
@Serializable
data class SyncOperationResponse(
    @SerialName("status") val status: String,
    @SerialName("batch_id") val batchId: String,
    @SerialName("accepted_sequences") val acceptedSequences: List<Long>,
    @SerialName("rejected_sequences") val rejectedSequences: List<RejectionDTO> = emptyList()
)

/**
 * Details about a rejected operation.
 */
@Serializable
data class RejectionDTO(
    @SerialName("sequence_id") val sequenceId: Long,
    @SerialName("reason") val reason: String
)
