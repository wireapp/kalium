package com.wire.kalium.api.tools.json.api.conversation

import com.wire.kalium.api.tools.json.ValidJsonProvider
import com.wire.kalium.network.api.conversation.MemberUpdateDTO
import com.wire.kalium.network.api.conversation.MutedStatus

object MemberUpdateRequestJson {

    val valid = ValidJsonProvider(
        MemberUpdateDTO(
            null, null, null, null, "2022-04-11T14:15:48.044Z", MutedStatus.ALL_ALLOWED
        )
    ) {
        """
        |{
        |   "otr_muted_ref": "${it.otrMutedRef}",
        |   "otr_muted_status": ${it.otrMutedStatus}
        |}
        """.trimIndent()
    }
}
