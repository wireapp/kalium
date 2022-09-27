package com.wire.kalium.model.conversation

import com.wire.kalium.api.json.ValidJsonProvider
import com.wire.kalium.network.api.base.authenticated.conversation.MemberUpdateDTO
import com.wire.kalium.network.api.base.authenticated.conversation.MutedStatus

object MemberUpdateRequestJson {

    val valid = ValidJsonProvider(
        MemberUpdateDTO(
            null, null, null, null, "2022-04-11T14:15:48.044Z", MutedStatus.ALL_ALLOWED
        )
    ) {
        """
        |{
        |   "otr_muted_ref": "${it.otrMutedRef}",
        |   "otr_muted_status": ${it.otrMutedStatus?.ordinal}
        |}
        """.trimMargin()
    }
}
