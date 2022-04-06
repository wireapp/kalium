package com.wire.kalium.network.api.conversation

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class MemberUpdateRequest(
    @SerialName("hidden") val hidden: Boolean?,
    @SerialName("hidden_ref") val hiddenRef: String?,
    @SerialName("otr_archived") val otrArchived: Boolean?,
    @SerialName("otr_archived_ref") val otrArchivedRef: String?,
    @SerialName("otr_muted_ref") val otrMutedRef: String?,
    @Serializable(with = MutedStatusSerializer::class) val otrMutedStatus: MutedStatus?
) {
    enum class MutedStatus {
        ALL_MUTED, ONLY_MENTIONS_ALLOWED, ALL_ALLOWED;

        companion object {
            fun fromOrdinal(ordinal: Int): MutedStatus? = values().firstOrNull { ordinal == it.ordinal }
        }
    }
}


