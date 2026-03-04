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

import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * On Android host tests, Robolectric is required to provide the Android
 * framework environment (Context, ApplicationProvider, etc.).
 *
 * JUnit 4 inherits `@RunWith` annotations, so any commonTest class that
 * extends this class will automatically use RobolectricTestRunner.
 */
@RunWith(RobolectricTestRunner::class)
actual open class RobolectricTest
