/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
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
package com.wire.kalium.cryptography

import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

/**
 * Requires native rollback confirmation before a failed CoreCrypto transaction restores its callback error.
 *
 * Intended for isolated notification processing that uses callback errors to discard speculative work.
 * Native rollback failures take precedence over callback errors within this coroutine context.
 * Transactions outside this context retain the regular CoreCrypto error behavior.
 */
object RequireConfirmedCryptoRollback : AbstractCoroutineContextElement(Key) {
    object Key : CoroutineContext.Key<RequireConfirmedCryptoRollback>
}
