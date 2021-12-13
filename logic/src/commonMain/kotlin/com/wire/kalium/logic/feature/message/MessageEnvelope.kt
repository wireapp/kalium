package com.wire.kalium.logic.feature.message

class MessageEnvelope(
    val senderClientId: String,
    val recipients: List<RecipientEntry>,
    val sendPushNotifications: Boolean = false,
    val dataBlob: ByteArray? = null
)

data class RecipientEntry(val userId: String, val clientPayloads: List<ClientPayload>)

data class ClientPayload(val clientId: String, val payload: ByteArray) {

    override fun equals(other: Any?): Boolean =
        this === other || (other is ClientPayload
                && other.clientId == clientId
                && other.payload.contentEquals(payload))

    override fun hashCode(): Int = HASH_MULTIPLIER * clientId.hashCode() + payload.contentHashCode()

    companion object {
        private const val HASH_MULTIPLIER = 31
    }
}
