package com.wire.kalium.network.api.base.authenticated.conversation.model

import com.wire.kalium.network.api.base.authenticated.conversation.ReceiptMode
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable

data class ConversationReceiptModeDTO constructor(
    @SerialName("receipt_mode")
    val receiptMode: ReceiptMode
)
