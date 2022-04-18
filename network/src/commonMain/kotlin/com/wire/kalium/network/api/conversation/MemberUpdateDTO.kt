package com.wire.kalium.network.api.conversation

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class MemberUpdateDTO(
    @SerialName("hidden") val hidden: Boolean? = null,
    @SerialName("hidden_ref") val hiddenRef: String? = null,
    @SerialName("otr_archived") val otrArchived: Boolean? = null,
    @SerialName("otr_archived_ref") val otrArchivedRef: String? = null,
    @SerialName("otr_muted_ref") val otrMutedRef: String? = null,
    @SerialName("otr_muted_status") @Serializable(with = MutedStatusSerializer::class) val otrMutedStatus: MutedStatus? = null
) {

    enum class MutedStatus {
        /**
         * 0 -> All notifications are displayed
         */
        ALL_ALLOWED,

        /**
         * 1 -> Only mentions are displayed (normal messages muted)
         */
        ONLY_MENTIONS_ALLOWED,

        /**
         * 2 -> Only normal notifications are displayed (mentions are muted) -- legacy, not used
         */
        MENTIONS_MUTED,

        /**
         * 3 -> No notifications are displayed
         */
        ALL_MUTED;

        companion object {
            fun fromOrdinal(ordinal: Int): MutedStatus? = values().firstOrNull { ordinal == it.ordinal }
        }
    }
}


