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
package com.wire.kalium.models

import java.util.UUID

abstract class OriginMessage : MessageBase {
    private var mimeType: String? = null
    private var name: String? = null
    private var size: Long = 0

    constructor(eventId: UUID?, msgId: UUID?, convId: UUID?, clientId: String?, userId: UUID?, time: String?) : super(
        eventId,
        msgId,
        convId,
        clientId,
        userId,
        time
    ) {
    }

    constructor(msg: MessageBase?) : super(msg) {}

    fun getSize(): Long {
        return size
    }

    fun setSize(size: Long) {
        this.size = size
    }

    fun getMimeType(): String? {
        return mimeType
    }

    fun setMimeType(mimeType: String?) {
        this.mimeType = mimeType
    }

    fun getName(): String? {
        return name
    }

    fun setName(name: String?) {
        this.name = name
    }
}
