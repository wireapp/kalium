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

package com.wire.kalium.persistence.db

/**
 * Defines the storage mode for the database, determining encryption and synchronization behavior.
 */
sealed class DatabaseStorageMode {

    /**
     * Standard unencrypted SQLite database.
     * Use this for development/testing or when encryption is not required.
     */
    data object Unencrypted : DatabaseStorageMode()

    /**
     * SQLCipher-encrypted database.
     * Provides AES-256 encryption for data at rest.
     *
     * @property passphrase The encryption key for the database
     */
    data class Encrypted(val passphrase: ByteArray) : DatabaseStorageMode() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as Encrypted

            return passphrase.contentEquals(other.passphrase)
        }

        override fun hashCode(): Int = passphrase.contentHashCode()

    }

    /**
     * LiteSync-enabled database for multi-device synchronization.
     *
     * **Important**: LiteSync does not support encryption. Data is stored unencrypted.
     *
     * @property syncUri The LiteSync server URI (e.g., "tcp://192.168.1.100:1234")
     * @property nodeType The node type: PRIMARY for the main node, SECONDARY for replicas
     * @property onReady Optional callback invoked when the database sync is ready
     * @property onSync Optional callback invoked when a sync event occurs
     */
    data class LiteSync(
        val syncUri: String,
        val nodeType: LiteSyncNodeType = LiteSyncNodeType.SECONDARY,
        val onReady: (() -> Unit)? = null,
        val onSync: (() -> Unit)? = null
    ) : DatabaseStorageMode()
}

/**
 * Defines the role of a node in LiteSync replication.
 */
enum class LiteSyncNodeType(val value: String) {
    /**
     * Primary node that accepts writes and replicates to secondary nodes.
     * Only one primary node should exist in a replication cluster.
     */
    PRIMARY("primary"),

    /**
     * Secondary node that receives replicated data from the primary.
     * Multiple secondary nodes can exist.
     */
    SECONDARY("secondary")
}
