/*
 * Wire
 * Copyright (C) 2024 Wire Swiss GmbH
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

package com.wire.kalium.testservice.models

import com.fasterxml.jackson.annotation.JsonIgnore
import com.wire.kalium.logic.CoreLogic

data class Instance(
    val backend: String,
    val clientId: String?,
    val instanceId: String,
    val name: String?,
    @JsonIgnore
    val coreLogic: CoreLogic,
    @JsonIgnore
    val instancePath: String?,
    @JsonIgnore
    val password: String,
    val startupTime: Long,
    val startTime: Long
)
