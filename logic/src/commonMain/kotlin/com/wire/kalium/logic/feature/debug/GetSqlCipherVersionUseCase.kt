/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.wire.kalium.logic.feature.debug

import com.wire.kalium.userstorage.di.UserStorage
import com.wire.kalium.util.DebugKaliumApi

/** Reads the SQLCipher version through the active user database connection. */
@DebugKaliumApi("Debug-only API for inspecting the active SQLCipher version.")
public class GetSqlCipherVersionUseCase internal constructor(
    private val userStorage: UserStorage,
) {
    public operator fun invoke(): String? = userStorage.database.debugExtension.sqlCipherVersion()
}
