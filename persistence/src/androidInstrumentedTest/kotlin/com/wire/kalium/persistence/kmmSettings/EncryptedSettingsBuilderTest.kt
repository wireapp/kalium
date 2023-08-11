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
package com.wire.kalium.persistence.kmmSettings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.russhwolf.settings.set
import com.wire.kalium.persistence.dao.UserIDEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Ignore
import kotlin.test.Test

@OptIn(ExperimentalCoroutinesApi::class)
class EncryptedSettingsBuilderTest {

    private val coroutineDispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(coroutineDispatcher)

    @BeforeTest
    fun before() {
        Dispatchers.setMain(coroutineDispatcher)
    }

    @Ignore
    @Test
    fun givenShouldEncryptDataIsTrue_whenEncryptingData_thenShouldEncryptWithoutFailing() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        (1..500).map {
            testScope.launch {
                EncryptedSettingsBuilder.build(
                    options = SettingOptions.UserSettings(
                        shouldEncryptData = true,
                        userIDEntity = UserIDEntity(
                            value = "userValue",
                            domain = "domainValue"
                        )
                    ),
                    param = EncryptedSettingsPlatformParam(context)
                ).also {
                    it["key$it"] = "value$it"
                }
            }
        }.joinAll()
        testScope.advanceUntilIdle()
    }

    @AfterTest
    fun after() {
        Dispatchers.resetMain()
    }
}
