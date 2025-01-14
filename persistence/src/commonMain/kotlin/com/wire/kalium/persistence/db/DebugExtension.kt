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

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import com.wire.kalium.persistence.dao.MetadataDAO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class DebugExtension(
    private val sqlDriver: SqlDriver,
    private val isEncrypted: Boolean,
    private val metaDataDao: MetadataDAO,
) {

    suspend fun observeIsProfilingEnabled(): Flow<Boolean> =
        metaDataDao.valueByKeyFlow(KEY_CIPHER_PROFILE)
            .map { state ->
                state?.let { DBProfile.fromString(it) }.let {
                    it is DBProfile.ON
                }
            }

    /**
     * Changes the profiling of the database (cipher_profile) if the profile is specified and the database is encrypted
     * @param enabled true to enable profiling, false to disable
     */
    suspend fun changeProfiling(enabled: Boolean): Long? =
        if (isEncrypted) {
            val state = if (enabled) DBProfile.ON.Device else DBProfile.Off
            sqlDriver.executeQuery(
                identifier = null,
                sql = """PRAGMA cipher_profile= '${state.logTarget}';""",
                mapper = { cursor ->
                    cursor.next()
                    cursor.getLong(0).let { QueryResult.Value<Long?>(it) }
                },
                parameters = 0,
            ).value.also {
                updateMetadata(state)
            }

        } else {
            error("Cannot change profiling on unencrypted database")
        }

    private suspend fun updateMetadata(state: DBProfile) {
        metaDataDao.insertValue(
            value = state.logTarget,
            key = KEY_CIPHER_PROFILE
        )
    }

    private companion object {
        const val KEY_CIPHER_PROFILE = "cipher_profile"
    }
}

sealed interface DBProfile {
    val logTarget: String

    data object Off : DBProfile {
        override val logTarget: String = "off"

        override fun toString(): String {
            return "off"
        }
    }

    sealed interface ON : DBProfile {
        data object Device : ON {
            override val logTarget: String = "logcat"

            override fun toString(): String {
                return "logcat"
            }
        }

        data class CustomFile(override val logTarget: String) : ON {
            override fun toString(): String {
                return logTarget
            }
        }
    }

    companion object {
        fun fromString(value: String): DBProfile = when (value) {
            "off" -> Off
            "logcat" -> ON.Device
            else -> ON.CustomFile(value)
        }
    }
}
