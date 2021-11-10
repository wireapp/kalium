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
package com.wire.helium.models

import com.fasterxml.jackson.annotation.JsonProperty
import com.wire.xenon.models.otr.PreKey

class NewClient {
    @JsonProperty("lastkey")
    var lastkey: PreKey? = null

    @JsonProperty("prekeys")
    var prekeys: List<PreKey>? = null

    @JsonProperty
    var password: String? = null

    @JsonProperty("class")
    var clazz: String? = null

    @JsonProperty
    var type: String? = null

    @JsonProperty
    var label: String? = null

    @JsonProperty
    var sigkeys = Sig()

    class Sig {
        @JsonProperty
        var enckey: String? = null

        @JsonProperty
        var mackey: String? = null
    }
}