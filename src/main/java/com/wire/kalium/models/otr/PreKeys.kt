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

import com.wire.kalium.tools.UUIDSerializer
import kotlinx.serialization.Serializable
import java.util.*

@Serializable
class ClientKey : HashMap<String, PreKey>() {

}

@Serializable
class PreKeys() : HashMap<UUID, ClientKey>() {

    constructor(array: ArrayList<PreKey>, clientId: String, userId: UUID) : this() {
        val devs = ClientKey()
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

@Serializable
data class ClientPrekey(
        val client: String,
        val prekey: PreKey
)

@Serializable
data class AllUserPrekeys(
        @Serializable(with = UUIDSerializer::class) val user: UUID,
        val clients: List<ClientPrekey>
)

typealias UsersPrekeysCollection = Map<UUID, Map<String, PreKey>>
