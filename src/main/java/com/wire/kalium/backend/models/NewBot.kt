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
class NewBot {
    @JsonProperty
    var id: @NotNull UUID? = null

    @JsonProperty
    var client: @NotNull String? = null

    @JsonProperty
    var token: @NotNull String? = null

    @JsonProperty
    var locale: String? = null

    @JsonProperty
    var origin: @NotNull User? = null

    @JsonProperty
    var conversation: @NotNull Conversation? = null
}
