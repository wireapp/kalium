package com.wire.kalium.logic.data.conversation

sealed class MemberChangeResult {
    object Unchanged : MemberChangeResult()
    data class Changed(val time: String) : MemberChangeResult()
}
