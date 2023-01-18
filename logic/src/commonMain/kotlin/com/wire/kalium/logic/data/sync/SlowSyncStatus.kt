package com.wire.kalium.logic.data.sync

import com.wire.kalium.logic.CoreFailure

sealed interface SlowSyncStatus {

    object Pending : SlowSyncStatus

    object Complete : SlowSyncStatus

    data class Ongoing(val currentStep: SlowSyncStep) : SlowSyncStatus

    data class Failed(val failure: CoreFailure) : SlowSyncStatus
}

enum class SlowSyncStep {
    SELF_USER,
    FEATURE_FLAGS,
    CONVERSATIONS,
    CONNECTIONS,
    SELF_TEAM,
    CONTACTS,
    JOINING_MLS_CONVERSATIONS
}
