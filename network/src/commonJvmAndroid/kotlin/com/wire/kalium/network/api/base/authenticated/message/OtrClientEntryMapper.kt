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

package com.wire.kalium.network.api.base.authenticated.message

import com.wire.kalium.protobuf.otr.ClientEntry
import pbandk.ByteArr

class OtrClientEntryMapper {

    private val clientIdMapper = OtrClientIdMapper()
    fun toOtrClientEntry(clientPayload: Map.Entry<String, ByteArray>): ClientEntry = ClientEntry(
        client = clientIdMapper.toOtrClientId(clientPayload.key),
        text = ByteArr(clientPayload.value),
    )
}
