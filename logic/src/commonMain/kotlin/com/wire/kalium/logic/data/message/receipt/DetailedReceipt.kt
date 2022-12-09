package com.wire.kalium.logic.data.message.receipt

import com.wire.kalium.logic.data.message.UserSummary
import kotlinx.datetime.Instant

data class DetailedReceipt(
    val type: ReceiptType,
    val date: Instant,
    val userSummary: UserSummary
)

enum class ReceiptType {
    DELIVERED, READ
}
