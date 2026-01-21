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
package com.wire.kalium.persistence.db

import app.cash.sqldelight.Query
import app.cash.sqldelight.db.SqlDriver

/**
 * SqlDriver wrapper that can mute invalidations.
 *
 * Important: SQLDelight itself will call driver.notifyListeners(...) after commit (transaction cleanup).
 * By intercepting notifyListeners here, we effectively "mute" query invalidations globally without touching DAOs.
 */
class MutedSqlDriver(
    private val delegate: SqlDriver,
    val invalidationController: DbInvalidationController,
) : SqlDriver by delegate {

    override fun notifyListeners(vararg queryKeys: String) {
        invalidationController.onNotify(queryKeys)
    }

    override fun addListener(vararg queryKeys: String, listener: Query.Listener) {
        delegate.addListener(*queryKeys, listener = listener)
    }

    override fun removeListener(vararg queryKeys: String, listener: Query.Listener) {
        delegate.removeListener(*queryKeys, listener = listener)
    }

    override fun close() {
        delegate.close()
    }
}
