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
package com.wire.kalium.backend.models

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import java.util.*

/**
 * TODO: Remove Jackson, remove lateinits, replace vars with vals
 */
@JsonIgnoreProperties(ignoreUnknown = true)
class Payload {
    /**
     * TODO: Replace String with something type-safe.
     *   Maybe an Enum? What are the possible values of this status? Bad discoverability too.
     *   Currently known: pending, accepted
     **/
    @JsonProperty
    lateinit var type: String

    @JsonProperty("conversation")
    lateinit var convId: UUID

    @JsonProperty
    lateinit var from: UUID

    @JsonProperty
    lateinit var time: String

    @JsonProperty
    lateinit var data: Data

    @JsonProperty
    var team: UUID? = null

    // User Mode
    @JsonProperty
    lateinit var connection: Connection

    @JsonProperty
    var user: User? = null

    @JsonIgnoreProperties(ignoreUnknown = true)
    class Data {
        @JsonProperty
        lateinit var sender: String

        @JsonProperty
        lateinit var recipient: String

        @JsonProperty
        lateinit var text: String

        @JsonProperty("user_ids")
        lateinit var userIds: MutableList<UUID>

        @JsonProperty
        var name: String? = null

        // User Mode
        @JsonProperty
        var id: String? = null

        @JsonProperty
        var key: String? = null

        @JsonProperty
        var user: UUID? = null

        @JsonProperty
        var creator: UUID? = null

        @JsonProperty
        var members: Members? = null
    }

    // User Mode
    @JsonIgnoreProperties(ignoreUnknown = true)
    class Connection {
        /**
         * TODO: Replace String with something type-safe.
         *   Maybe an Enum? What are the possible values of this status? Bad discoverability too.
         *   Currently known: pending, accepted
         **/
        @JsonProperty
        lateinit var status: String

        @JsonProperty
        lateinit var from: UUID

        @JsonProperty
        lateinit var to: UUID

        @JsonProperty("conversation")
        lateinit var convId: UUID
    }

    // User Mode
    @JsonIgnoreProperties(ignoreUnknown = true)
    class User {
        @JsonProperty
        var id: UUID? = null

        @JsonProperty
        var name: String? = null

        @JsonProperty("accent_id")
        var accent = 0

        @JsonProperty
        var handle: String? = null

        @JsonProperty
        var email: String? = null
    }

    // User Mode
    @JsonIgnoreProperties(ignoreUnknown = true)
    class Members {
        @JsonProperty
        lateinit var others: MutableList<Member>
    }
}
