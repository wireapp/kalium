/*
 * Wire
 * Copyright (C) 2023 Wire Swiss GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see http://www.gnu.org/licenses/.
 */

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
