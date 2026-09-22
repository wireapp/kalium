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
package com.wire.kalium.network.api.authenticated.featureConfigs

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class MLSMigrationConfigDTOTest {
    @Test
    fun givenManualMigrationFlag_whenDecoded_thenPreservesBothValues() {
        listOf(true, false).forEach { allowed ->
            val config = Json.decodeFromString<MLSMigrationConfigDTO>(
                """{"startTime":null,"finaliseRegardlessAfter":null,"allowManualMigration":$allowed}"""
            )
            assertEquals(allowed, config.allowManualMigration)
        }
    }

    @Test
    fun givenMissingManualMigrationFlag_whenDecoded_thenDefaultsToFalse() {
        val config = Json.decodeFromString<MLSMigrationConfigDTO>(
            """{"startTime":null,"finaliseRegardlessAfter":null}"""
        )
        assertFalse(config.allowManualMigration)
    }
}
