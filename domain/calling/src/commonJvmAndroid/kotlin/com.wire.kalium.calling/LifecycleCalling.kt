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

package com.wire.kalium.calling

import com.wire.kalium.calling.types.Handle

/** Optional JVM runtime contract. Implementations must keep callbacks alive until native shutdown acknowledges release. */
interface LifecycleCalling : Calling {
    /** Starts the runtime or throws a bounded, sticky startup failure. */
    fun initialize()

    /** Links the qualified owner to its native handle without assuming federation aliases. */
    fun bindAccount(handle: Handle, qualifiedAccount: String) = Unit

    /** Admits hardware only for an explicit, permission-bound participation action. */
    suspend fun prepareCallMedia(handle: Handle, conversationId: String, qualifiedConversationId: String, video: Boolean) = Unit

    /** Returns only after camera start/stop completion for the current call generation. */
    suspend fun setCallCamera(handle: Handle, conversationId: String, enabled: Boolean) = Unit

    /**
     * Returns only after native shutdown acknowledges this handle. A timeout must retain the handle and callback references
     * so another invocation can finish the same release; it must never unload a running native runtime.
     */
    suspend fun releaseHandle(handle: Handle)

    /** Returns only after the real native close callback for this conversation, or throws a bounded timeout. */
    suspend fun endCallAndAwait(handle: Handle, conversationId: String)
}
