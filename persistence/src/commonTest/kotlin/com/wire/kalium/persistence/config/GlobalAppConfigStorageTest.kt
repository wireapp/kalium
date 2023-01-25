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

package com.wire.kalium.persistence.config

import com.russhwolf.settings.MapSettings
import com.russhwolf.settings.Settings
import com.wire.kalium.persistence.kmmSettings.KaliumPreferences
import com.wire.kalium.persistence.kmmSettings.KaliumPreferencesSettings
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class GlobalAppConfigStorageTest {

    private val settings: Settings = MapSettings()

    private val kaliumPreferences: KaliumPreferences = KaliumPreferencesSettings(settings)
    private lateinit var globalAppConfigStorage: GlobalAppConfigStorage

    @BeforeTest
    fun setUp() {
        globalAppConfigStorage = GlobalAppConfigStorageImpl(kaliumPreferences)
    }

    @AfterTest
    fun clear() {
        settings.clear()
    }

    @Test
    fun givenEnableLogging_whenCAllPersistItSaveAndThenCanRestoreTheValueLocally() = runTest {
        globalAppConfigStorage.enableLogging(true)
        assertEquals(true, globalAppConfigStorage.isLoggingEnables())

        globalAppConfigStorage.enableLogging(false)
        assertEquals(false, globalAppConfigStorage.isLoggingEnables())
    }

}
