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

@file:OptIn(com.wire.kalium.util.InternalKaliumApi::class)

package com.wire.kalium.logic.sync.receiving

import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.configuration.server.CommonApiVersionType
import com.wire.kalium.logic.configuration.server.ServerConfig
import com.wire.kalium.logic.data.user.UserId
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okio.FileSystem
import okio.IOException
import okio.Path.Companion.toPath
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSUUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull

class ApplePreparedAccountStoreTest {
    private val userId = UserId("prepared-user", "example.com")

    @Test
    fun `invalidated client cannot be republished but a new registration can`() = runTest {
        withStore { root, store, lease ->
            FileSystem.SYSTEM.createDirectories(appleAccountPath(root, userId).toPath())
            store.publish(lease, prepared())
            store.invalidate(lease, prepared().clientId)
            assertNull(ApplePreparedAccountStore(root, userId).read(lease))
            store.publish(lease, prepared())
            assertNull(store.read(lease))
            val newClient = prepared().copy(clientId = "new-client")
            store.publish(lease, newClient)
            assertEquals(newClient, store.read(lease))
        }
    }

    @Test
    fun `old host readiness cannot authorize new durable effects`() = runTest {
        withStore { root, store, lease ->
            val account = appleAccountPath(root, userId).toPath()
            FileSystem.SYSTEM.createDirectories(account)
            FileSystem.SYSTEM.write(account / "notification-extension-ready.json") {
                writeUtf8(Json { encodeDefaults = true }.encodeToString(prepared().copy(formatVersion = 1)))
            }
            assertNull(store.read(lease))
            // Preparing the existing registered client publishes the new contract;
            // no credentials, account storage, or registration is reset.
            store.publish(lease, prepared())
            assertEquals(prepared(), store.read(lease))
        }
    }

    @Test
    fun `failed replacement publish cannot uncover old readiness`() = runTest {
        withStore { root, store, lease ->
            val account = appleAccountPath(root, userId).toPath()
            FileSystem.SYSTEM.createDirectories(account)
            store.publish(lease, prepared())
            store.invalidate(lease, prepared().clientId)
            // Model an old readiness file left by a failed revoke.
            FileSystem.SYSTEM.write(account / "notification-extension-ready.json") {
                writeUtf8(Json { encodeDefaults = true }.encodeToString(prepared()))
            }
            assertFailsWith<IllegalArgumentException> {
                store.publish(lease, prepared().copy(clientId = "replacement".repeat(4000)))
            }
            assertNull(ApplePreparedAccountStore(root, userId).read(lease))
            val replacement = prepared().copy(clientId = "replacement")
            store.publish(lease, replacement)
            assertEquals(replacement, store.read(lease))
        }
    }

    @Test
    fun `invalidation barrier survives failed readiness unlink`() = runTest {
        withStore { root, store, lease ->
            FileSystem.SYSTEM.createDirectories((appleAccountPath(root, userId) + "/notification-extension-ready.json").toPath())
            assertFailsWith<IOException> { store.invalidate(lease, prepared().clientId) }
            assertNull(store.read(lease))
            assertFailsWith<IOException> { store.publish(lease, prepared()) }
        }
    }

    @Test
    fun `missing readiness can be read and revoked without creating account storage`() = runTest {
        withStore { root, store, lease ->
            assertNull(store.read(lease))
            store.revoke(lease)
            assertFalse(FileSystem.SYSTEM.exists(appleAccountPath(root, userId).toPath()))
        }
    }

    @Test
    fun `published readiness round trips and revokes without opening user SQLite`() = runTest {
        withStore { root, store, lease ->
            val accountPath = appleAccountPath(root, userId).toPath()
            FileSystem.SYSTEM.createDirectories(accountPath / "storage")
            val oldDatabase = accountPath / "storage" / "old-database"
            FileSystem.SYSTEM.write(oldDatabase) { writeUtf8("old schema") }
            store.publish(lease, prepared())
            assertEquals(prepared(), store.read(lease))
            store.revoke(lease)
            assertNull(store.read(lease))
            assertEquals("old schema", FileSystem.SYSTEM.read(oldDatabase) { readUtf8() })
        }
    }

    @Test
    fun `malformed missing-version and incompatible records remain unready`() = runTest {
        withStore { root, store, lease ->
            val accountPath = appleAccountPath(root, userId).toPath()
            FileSystem.SYSTEM.createDirectories(accountPath)
            val path = accountPath / "notification-extension-ready.json"
            val json = Json { encodeDefaults = true }
            val good = json.encodeToString(prepared())
            for (invalid in listOf(
                "{",
                good.replace("\"format_version\":${prepared().formatVersion},", ""),
                json.encodeToString(prepared().copy(formatVersion = prepared().formatVersion + 1)),
                json.encodeToString(prepared().copy(userId = UserId("other", userId.domain))),
                json.encodeToString(prepared().copy(coreCryptoVersion = "other-version")),
                json.encodeToString(prepared().copy(userSchemaVersion = -1)),
            )) {
                FileSystem.SYSTEM.write(path) { writeUtf8(invalid) }
                assertNull(store.read(lease))
            }
        }
    }

    @Test
    fun `revocation failure is reported`() = runTest {
        withStore { root, store, lease ->
            FileSystem.SYSTEM.createDirectories((appleAccountPath(root, userId) + "/notification-extension-ready.json").toPath())
            assertFailsWith<IOException> { store.revoke(lease) }
        }
    }

    @Test
    fun `unpublished temporary files are not readiness`() = runTest {
        withStore { root, store, lease ->
            val accountPath = appleAccountPath(root, userId).toPath()
            FileSystem.SYSTEM.createDirectories(accountPath)
            FileSystem.SYSTEM.write(accountPath / ".notification-extension-ready.json.interrupted.tmp") {
                writeUtf8(Json.encodeToString(prepared()))
            }
            assertNull(store.read(lease))
            store.publish(lease, prepared())
            assertEquals(prepared(), store.read(lease))
        }
    }

    @Test
    fun `released or unrelated lease cannot alter readiness`() = runTest {
        withStore { root, store, lease ->
            val other = ApplePreparedAccountStore(root, UserId("other", userId.domain))
            assertFailsWith<IllegalArgumentException> { other.revoke(lease) }
            assertFailsWith<IllegalArgumentException> { other.invalidate(lease, "client") }
            val otherRoot = ApplePreparedAccountStore("$root-other", userId)
            assertFailsWith<IllegalArgumentException> { otherRoot.revoke(lease) }
            lease.release()
            assertFailsWith<IllegalStateException> { store.read(lease) }
            assertFailsWith<IllegalStateException> { store.publish(lease, prepared()) }
            assertFailsWith<IllegalStateException> { store.revoke(lease) }
        }
    }

    private fun prepared() = PreparedAccount(
        userId, "abc123",
        ServerConfig("test", ServerConfig.DUMMY, ServerConfig.MetaData(false, CommonApiVersionType.Valid(8), userId.domain)),
        null, null,
    )

    private suspend fun withStore(block: suspend (String, ApplePreparedAccountStore, AccountRuntimeLease) -> Unit) {
        val root = "${NSTemporaryDirectory()}kalium-prepared-${NSUUID.UUID().UUIDString}"
        FileSystem.SYSTEM.createDirectories(root.toPath())
        val lease = assertIs<Either.Right<*>>(AppleAccountRuntimeOwner(root).tryAcquire(userId)).value as AccountRuntimeLease
        try {
            block(root, ApplePreparedAccountStore(root, userId), lease)
        } finally {
            lease.release()
            FileSystem.SYSTEM.deleteRecursively(root.toPath())
        }
    }
}
