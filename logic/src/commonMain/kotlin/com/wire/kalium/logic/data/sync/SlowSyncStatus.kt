package com.wire.kalium.logic.data.sync

sealed interface SlowSyncStatus {

    object Pending : SlowSyncStatus

    object Complete : SlowSyncStatus

    data class Ongoing(val currentStep: SlowSyncStep) : SlowSyncStatus
}

enum class SlowSyncStep {
    SELF_USER,
    CONVERSATIONS,
    CONNECTIONS,
    SELF_TEAM,
    CONTACTS,
    JOINING_MLS_CONVERSATIONS,
}
