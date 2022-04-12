package com.wire.kalium.network.api.conversation

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class MemberUpdateRequest(
    @SerialName("hidden") val hidden: Boolean? = null,
    @SerialName("hidden_ref") val hiddenRef: String? = null,
    @SerialName("otr_archived") val otrArchived: Boolean? = null,
    @SerialName("otr_archived_ref") val otrArchivedRef: String? = null,
    @SerialName("otr_muted_ref") val otrMutedRef: String? = null,
    @Serializable(with = MutedStatusSerializer::class) val otrMutedStatus: MutedStatus? = null
) {

    /**
     * ```
     * 0 -> All notifications are displayed
     * 1 -> Only mentions are displayed (normal messages muted)
     * 2 -> Only normal notifications are displayed (mentions are muted) -- legacy, not used
     * 3 -> No notifications are displayed
     * ```
     */
    enum class MutedStatus {
        ALL_ALLOWED,
        ONLY_MENTIONS_ALLOWED,
        MENTIONS_MUTED,
        ALL_MUTED;

        companion object {
            fun fromOrdinal(ordinal: Int): MutedStatus? = values().firstOrNull { ordinal == it.ordinal }
        }
    }
}


