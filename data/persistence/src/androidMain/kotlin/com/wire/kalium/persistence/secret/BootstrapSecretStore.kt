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

package com.wire.kalium.persistence.secret

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyInfo
import android.security.keystore.KeyProperties
import android.util.Base64
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec

interface BootstrapSecretStore {
    fun getGlobalDbPassphrase(): ByteArray?
    fun getOrCreateGlobalDbPassphrase(): ByteArray
    fun putGlobalDbPassphrase(passphrase: ByteArray)
    fun clearGlobalDbPassphrase()
    fun <T> withGlobalDbPassphrase(block: (ByteArray) -> T): T?
}

class AndroidBootstrapSecretStore internal constructor(
    context: Context,
    private val cipher: BootstrapSecretCipher,
    private val secureRandom: SecureRandom,
    fileName: String
) : BootstrapSecretStore {

    constructor(context: Context) : this(
        context = context,
        cipher = AndroidKeyStoreBootstrapSecretCipher(context),
        secureRandom = SecureRandom(),
        fileName = BOOTSTRAP_SECRET_FILE_NAME
    )

    private val secretFile = File(context.applicationContext.filesDir, fileName)

    override fun getGlobalDbPassphrase(): ByteArray? =
        readBootstrapFile()?.let { fileContent ->
            require(fileContent.version == BOOTSTRAP_SECRET_VERSION) {
                "Unsupported bootstrap secret version ${fileContent.version}"
            }
            require(fileContent.purpose == GLOBAL_DB_PASSPHRASE_AAD) {
                "Unexpected bootstrap secret purpose"
            }
            cipher.decrypt(fileContent.encryptedSecret(), GLOBAL_DB_PASSPHRASE_AAD.encodeToByteArray())
        }

    override fun getOrCreateGlobalDbPassphrase(): ByteArray =
        getGlobalDbPassphrase() ?: ByteArray(GLOBAL_DB_PASSPHRASE_SIZE_BYTES)
            .also(secureRandom::nextBytes)
            .also(::putGlobalDbPassphrase)

    override fun putGlobalDbPassphrase(passphrase: ByteArray) {
        require(passphrase.size == GLOBAL_DB_PASSPHRASE_SIZE_BYTES) {
            "Global DB passphrase must be $GLOBAL_DB_PASSPHRASE_SIZE_BYTES bytes"
        }
        val encrypted = cipher.encrypt(passphrase, GLOBAL_DB_PASSPHRASE_AAD.encodeToByteArray())
        secretFile.writeText(
            Json.encodeToString(BootstrapSecretFile.serializer(), encrypted.fileContent()),
            Charsets.UTF_8
        )
    }

    override fun clearGlobalDbPassphrase() {
        if (secretFile.exists()) {
            secretFile.delete()
        }
    }

    override fun <T> withGlobalDbPassphrase(block: (ByteArray) -> T): T? {
        val passphrase = getGlobalDbPassphrase() ?: return null
        return try {
            block(passphrase)
        } finally {
            passphrase.fill(0)
        }
    }

    private fun readBootstrapFile(): BootstrapSecretFile? =
        if (secretFile.exists()) {
            Json.decodeFromString(BootstrapSecretFile.serializer(), secretFile.readText(Charsets.UTF_8))
        } else {
            null
        }

    private fun BootstrapSecretFile.encryptedSecret(): EncryptedBootstrapSecret =
        EncryptedBootstrapSecret(
            iv = iv.decodeBase64(),
            cipherText = cipherText.decodeBase64(),
            isStrongBoxBacked = isStrongBoxBacked
        )

    private fun EncryptedBootstrapSecret.fileContent(): BootstrapSecretFile =
        BootstrapSecretFile(
            version = BOOTSTRAP_SECRET_VERSION,
            purpose = GLOBAL_DB_PASSPHRASE_AAD,
            keyAlias = cipher.keyAlias,
            algorithm = BOOTSTRAP_CIPHER_TRANSFORMATION,
            iv = iv.encodeBase64(),
            cipherText = cipherText.encodeBase64(),
            isStrongBoxBacked = isStrongBoxBacked
        )
}

internal interface BootstrapSecretCipher {
    val keyAlias: String
    fun encrypt(plainText: ByteArray, aad: ByteArray): EncryptedBootstrapSecret
    fun decrypt(encryptedSecret: EncryptedBootstrapSecret, aad: ByteArray): ByteArray
}

internal data class EncryptedBootstrapSecret(
    val iv: ByteArray,
    val cipherText: ByteArray,
    val isStrongBoxBacked: Boolean
)

internal class AndroidKeyStoreBootstrapSecretCipher(
    context: Context,
    override val keyAlias: String = BOOTSTRAP_KEY_ALIAS
) : BootstrapSecretCipher {

    private val appContext = context.applicationContext

    override fun encrypt(plainText: ByteArray, aad: ByteArray): EncryptedBootstrapSecret {
        val key = getOrCreateKey()
        val cipher = Cipher.getInstance(BOOTSTRAP_CIPHER_TRANSFORMATION).apply {
            init(Cipher.ENCRYPT_MODE, key.secretKey)
            updateAAD(aad)
        }
        val iv = cipher.iv
        return EncryptedBootstrapSecret(
            iv = iv,
            cipherText = cipher.doFinal(plainText),
            isStrongBoxBacked = key.isStrongBoxBacked
        )
    }

    override fun decrypt(encryptedSecret: EncryptedBootstrapSecret, aad: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(BOOTSTRAP_CIPHER_TRANSFORMATION).apply {
            init(Cipher.DECRYPT_MODE, getOrCreateKey().secretKey, GCMParameterSpec(GCM_TAG_SIZE_BITS, encryptedSecret.iv))
            updateAAD(aad)
        }
        return cipher.doFinal(encryptedSecret.cipherText)
    }

    private fun getOrCreateKey(): LoadedSecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
        keyStore.getKey(keyAlias, null)?.let {
            val secretKey = it as SecretKey
            return LoadedSecretKey(secretKey, secretKey.isStrongBoxBacked())
        }

        return if (shouldRequestStrongBox()) {
            runCatching { LoadedSecretKey(generateKey(strongBoxBacked = true), isStrongBoxBacked = true) }
                .getOrElse { LoadedSecretKey(generateKey(strongBoxBacked = false), isStrongBoxBacked = false) }
        } else {
            LoadedSecretKey(generateKey(strongBoxBacked = false), isStrongBoxBacked = false)
        }
    }

    private fun generateKey(strongBoxBacked: Boolean): SecretKey {
        val keySpec = KeyGenParameterSpec.Builder(
            keyAlias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true)
            .apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    setIsStrongBoxBacked(strongBoxBacked)
                }
            }
            .build()

        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEY_STORE)
            .apply { init(keySpec) }
            .generateKey()
    }

    private fun shouldRequestStrongBox(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
            appContext.packageManager.hasSystemFeature(PackageManager.FEATURE_STRONGBOX_KEYSTORE)

    private fun SecretKey.isStrongBoxBacked(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            runCatching {
                val keyFactory = SecretKeyFactory.getInstance(algorithm, ANDROID_KEY_STORE)
                val keyInfo = keyFactory.getKeySpec(this, KeyInfo::class.java) as KeyInfo
                keyInfo.securityLevel == KeyProperties.SECURITY_LEVEL_STRONGBOX
            }.getOrDefault(false)

    private data class LoadedSecretKey(
        val secretKey: SecretKey,
        val isStrongBoxBacked: Boolean
    )
}

@Serializable
private data class BootstrapSecretFile(
    @SerialName("version") val version: Int,
    @SerialName("purpose") val purpose: String,
    @SerialName("key_alias") val keyAlias: String,
    @SerialName("algorithm") val algorithm: String,
    @SerialName("iv") val iv: String,
    @SerialName("cipher_text") val cipherText: String,
    @SerialName("is_strong_box_backed") val isStrongBoxBacked: Boolean
)

private fun ByteArray.encodeBase64(): String = Base64.encodeToString(this, Base64.NO_WRAP)

private fun String.decodeBase64(): ByteArray = Base64.decode(this, Base64.NO_WRAP)

internal const val BOOTSTRAP_SECRET_FILE_NAME = "bootstrap-secrets.json"
private const val BOOTSTRAP_SECRET_VERSION = 1
private const val GLOBAL_DB_PASSPHRASE_SIZE_BYTES = 32
private const val GCM_TAG_SIZE_BITS = 128
private const val ANDROID_KEY_STORE = "AndroidKeyStore"
private const val BOOTSTRAP_KEY_ALIAS = "kalium.bootstrap.v1"
private const val BOOTSTRAP_CIPHER_TRANSFORMATION = "AES/GCM/NoPadding"
private const val GLOBAL_DB_PASSPHRASE_AAD = "kalium/bootstrap/global-db-passphrase/v1"
