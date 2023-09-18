/*
 * Wire
 * Copyright (C) 2023 Wire Swiss GmbH
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
package com.wire.kalium.logic.sync

import com.wire.kalium.logic.data.conversation.TypingIndicatorRepository

class TypingIndicatorCleanupWorker(
    private val typingIndicatorRepository: TypingIndicatorRepository
) : DefaultWorker {
    override suspend fun doWork(): Result {

        // get expired in-mem map
        // delete by ttl expired entries

        // schedule from userworker every 60 secs
        // can it be that we need to avoid this and do it better by foreground? or just assume navigate out


        TODO("Not yet implemented")
    }
}
