package com.wire.kalium.logic.data.id

import kotlinx.serialization.Serializable

@Serializable
data class QualifiedID(
    val value: String,
    val domain: String
)

typealias ConversationId = QualifiedID
