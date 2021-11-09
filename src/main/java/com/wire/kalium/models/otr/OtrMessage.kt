//
// Wire
// Copyright (C) 2016 Wire Swiss GmbH
//
// This program is free software: you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation, either version 3 of the License, or
// (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with this program. If not, see http://www.gnu.org/licenses/.
//
package com.wire.kalium.models.otr

import java.util.*

class OtrMessage(//clientId of the sender
        private val sender: String, private val recipients: Recipients
) {
    fun add(rec: Recipients) {
        recipients.add(rec)
    }

    operator fun get(userId: UUID, clientId: String): String {
        return recipients.get(userId, clientId)
    }

    fun size(): Int {
        var count = 0
        for (devs in recipients.values) {
            count += devs.size
        }
        return count
    }

    fun getSender(): String? {
        return sender
    }
}
