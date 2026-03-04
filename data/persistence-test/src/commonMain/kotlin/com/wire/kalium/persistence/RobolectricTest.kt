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
package com.wire.kalium.persistence

/**
 * Base class for tests that need Android framework APIs (e.g., database access via
 * [TestUserDatabase] or [TestGlobalDatabase]).
 *
 * On Android host tests this resolves to an actual class annotated with
 * `@RunWith(RobolectricTestRunner::class)`, ensuring the Android environment is
 * available. On all other platforms it is an empty open class.
 */
expect open class RobolectricTest()
