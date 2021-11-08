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

import java.util.UUID
import java.util.HashMap

class PreKeys : HashMap<UUID?, HashMap<String?, PreKey?>?> {
    constructor() {}
    constructor(array: ArrayList<PreKey?>?, clientId: String?, userId: UUID?) : super() {
        val devs = HashMap<String?, PreKey?>()
        for (key in array) {
            devs[clientId] = key
        }
        put(userId, devs)
    }

    fun count(): Int {
        var ret = 0
        for (cls in values) ret += cls.size
        return ret
    }
}
