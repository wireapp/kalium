package com.wire.kalium.logic.data.call

import com.wire.kalium.logic.data.id.toConversationId
import com.wire.kalium.logic.feature.call.Call
import com.wire.kalium.logic.feature.call.CallStatus
import com.wire.kalium.logic.feature.call.calls
import kotlinx.coroutines.flow.update

//TODO create unit test for this one in a different PR
class UpdateCallStatusById {
    fun updateCallStatus(conversationId: String, status: CallStatus) {
        calls.update {
            mutableListOf<Call>().apply {
                addAll(it)

                val callIndex = it.indexOfFirst { call -> call.conversationId.toString() == conversationId }
                if (callIndex == -1) {
                    add(
                        Call(
                            conversationId = conversationId.toConversationId(),
                            status = status
                        )
                    )
                } else {
                    this[callIndex] = this[callIndex].copy(
                        status = status
                    )
                }
            }
        }
    }
}

