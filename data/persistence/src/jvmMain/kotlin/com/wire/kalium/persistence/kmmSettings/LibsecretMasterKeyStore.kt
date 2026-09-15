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

package com.wire.kalium.persistence.kmmSettings

import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.ptr.PointerByReference
import java.util.Base64
import java.util.UUID

/**
 * Keeps the settings master key in the Secret Service through libsecret: GNOME Keyring, KDE Wallet
 * (5.97 and later) or KeePassXC, whichever is running.
 *
 * There is no fallback. Without libsecret, or without a running and unlockable Secret Service,
 * every call throws [SettingsEncryptionException].
 *
 * @param collection where new keys go; `null` is the default collection, usually the login keyring.
 * Tests use the in-memory `session` collection.
 */
internal class LibsecretMasterKeyStore(private val collection: String? = null) : MasterKeyStore {

    override val name: String = "linux-secret-service"

    override fun store(key: ByteArray): String {
        val account = UUID.randomUUID().toString()
        withSchema { schema ->
            val error = PointerByReference()
            val stored = libsecret.secret_password_store_sync(
                schema,
                collection,
                LABEL,
                Base64.getEncoder().encodeToString(key),
                null,
                error,
                SERVICE_ATTRIBUTE,
                SERVICE,
                ACCOUNT_ATTRIBUTE,
                account,
                null
            )
            if (stored == G_FALSE) {
                throw SettingsEncryptionException("Can't store the settings master key in the Secret Service: ${error.message()}")
            }
        }
        return account
    }

    override fun load(reference: String): ByteArray = withSchema { schema ->
        val error = PointerByReference()
        val password = libsecret.secret_password_lookup_sync(
            schema,
            null,
            error,
            SERVICE_ATTRIBUTE,
            SERVICE,
            ACCOUNT_ATTRIBUTE,
            reference,
            null
        )
        when {
            password != null -> try {
                Base64.getDecoder().decode(password.getString(0))
            } finally {
                libsecret.secret_password_free(password)
            }

            error.value != null ->
                throw SettingsEncryptionException("Can't read the settings master key from the Secret Service: ${error.message()}")

            else -> throw SettingsEncryptionException("The settings master key is missing from the Secret Service")
        }
    }

    private fun <T> withSchema(block: (Pointer) -> T): T {
        val schema = libsecret.secret_schema_new(
            SCHEMA,
            SCHEMA_FLAGS_NONE,
            SERVICE_ATTRIBUTE,
            ATTRIBUTE_TYPE_STRING,
            ACCOUNT_ATTRIBUTE,
            ATTRIBUTE_TYPE_STRING,
            null
        ) ?: throw SettingsEncryptionException("Can't create the libsecret schema")
        try {
            return block(schema)
        } finally {
            libsecret.secret_schema_unref(schema)
        }
    }

    /** Returns the message of the GError and frees it. */
    private fun PointerByReference.message(): String {
        val error = value ?: return "unknown error"
        return try {
            error.getPointer(GERROR_MESSAGE_OFFSET)?.getString(0) ?: "unknown error"
        } finally {
            glib.g_error_free(error)
        }
    }

    private companion object {
        const val SCHEMA = "com.wire.kalium.Settings"
        const val LABEL = "Kalium settings master key"
        const val SERVICE_ATTRIBUTE = "service"
        const val ACCOUNT_ATTRIBUTE = "account"
        const val SERVICE = "com.wire.kalium.settings"
        const val SCHEMA_FLAGS_NONE = 0
        const val ATTRIBUTE_TYPE_STRING = 0
        const val G_FALSE = 0

        // GError is { guint32 domain; gint code; gchar *message; }
        const val GERROR_MESSAGE_OFFSET = 8L
    }
}

@Suppress("FunctionNaming", "LongParameterList")
internal interface LibSecret : Library {
    fun secret_schema_new(name: String, flags: Int, vararg attributes: Any?): Pointer?

    fun secret_schema_unref(schema: Pointer)

    fun secret_password_store_sync(
        schema: Pointer,
        collection: String?,
        label: String,
        password: String,
        cancellable: Pointer?,
        error: PointerByReference,
        vararg attributes: Any?
    ): Int

    fun secret_password_lookup_sync(
        schema: Pointer,
        cancellable: Pointer?,
        error: PointerByReference,
        vararg attributes: Any?
    ): Pointer?

    fun secret_password_free(password: Pointer)
}

@Suppress("FunctionNaming")
internal interface GLib : Library {
    fun g_error_free(error: Pointer)
}

private val libsecret: LibSecret by lazy { loadLibrary("secret-1", "libsecret-1.so.0") }
private val glib: GLib by lazy { loadLibrary("glib-2.0", "libglib-2.0.so.0") }

private inline fun <reified T : Library> loadLibrary(vararg names: String): T {
    for (name in names) {
        runCatching { Native.load(name, T::class.java) }.onSuccess { return it }
    }
    throw SettingsEncryptionException("Encrypted settings need libsecret on Linux, and ${names.first()} can't be loaded")
}
