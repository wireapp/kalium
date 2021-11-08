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

import java.util.UUID
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import javax.validation.constraints.NotNull

@JsonIgnoreProperties(ignoreUnknown = true)
class Payload {
    @JsonProperty
    var type: @NotNull String? = null

    @JsonProperty("conversation")
    var convId: UUID? = null

    @JsonProperty
    var from: @NotNull UUID? = null

    @JsonProperty
    var time: @NotNull String? = null

    @JsonProperty
    var data: @NotNull Data? = null

    @JsonProperty
    var team: UUID? = null

    // User Mode
    @JsonProperty
    var connection: Connection? = null

    @JsonProperty
    var user: User? = null

    @JsonIgnoreProperties(ignoreUnknown = true)
    class Data {
        @JsonProperty
        var sender: @NotNull String? = null

        @JsonProperty
        var recipient: @NotNull String? = null

        @JsonProperty
        var text: String? = null

        @JsonProperty("user_ids")
        var userIds: MutableList<UUID?>? = null

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
        @JsonProperty
        var status: String? = null

        @JsonProperty
        var from: UUID? = null

        @JsonProperty
        var to: UUID? = null

        @JsonProperty("conversation")
        var convId: UUID? = null
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
        var others: MutableList<Member?>? = null
    }
}
