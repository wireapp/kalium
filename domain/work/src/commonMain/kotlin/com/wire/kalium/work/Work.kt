/*
 * Wire
 * Copyright (C) 2025 Wire Swiss GmbH
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
package com.wire.kalium.work

import kotlin.jvm.JvmInline

public data class Work(val id: WorkId, val type: Type, val status: Status) {
    public sealed interface Type {
        public data object InitialSync : Type
    }

    public sealed interface Status {
        public data object InProgress : Status
        public data object Complete : Status
    }
}

@JvmInline
public value class WorkId(public val id: String) {
    public companion object {
        public val INITIAL_SYNC: WorkId = WorkId("INITIAL_SYNC_WORK_ID")
    }
}
