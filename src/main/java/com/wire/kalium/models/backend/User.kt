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
package com.wire.kalium.models.backend

import com.wire.kalium.tools.UUIDSerializer
import kotlinx.serialization.Serializable
import java.util.*

@Serializable
data class User(
        @Serializable(with = UUIDSerializer::class) val id: UUID,
        val name: String,
        val accent_id: Int,
        val handle: String,
        var service: Service? = null, // why null ? see API.kt line. Dejan: Participant can be human (has no service) or bot (has service)
        val assets: ArrayList<AssetKey>,
        val email: String //maybe we can get nulls here
)
