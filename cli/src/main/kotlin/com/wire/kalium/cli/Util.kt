//
// Wire
// Copyright (C) 2016 Wire Swiss GmbH
//
// This program is free software: you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation, either version 3 of the License, or
// (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with this program. If not, see http://www.gnu.org/licenses/.
//
package com.wire.kalium.cli

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.math.BigInteger
import java.nio.charset.StandardCharsets
import java.security.InvalidKeyException
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.util.Base64
import java.util.regex.Pattern
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

object Util {
    private val pattern = Pattern.compile("(?<=@)([a-zA-Z0-9\\_]{3,})")
    private val HMAC_SHA_1: String = "HmacSHA1"

    @Throws(Exception::class)
    fun encrypt(key: ByteArray, dataToSend: ByteArray, iv: ByteArray): ByteArray {
        val c = Cipher.getInstance("AES/CBC/PKCS5Padding")
        val secretKeySpec = SecretKeySpec(key, "AES")
        c.init(Cipher.ENCRYPT_MODE, secretKeySpec, IvParameterSpec(iv))
        val bytes = c.doFinal(dataToSend)
        val os = ByteArrayOutputStream()
        os.write(iv)
        os.write(bytes)
        return os.toByteArray()
    }

    @Throws(Exception::class)
    fun decrypt(key: ByteArray, encrypted: ByteArray): ByteArray {
        val `is` = ByteArrayInputStream(encrypted)
        val iv = ByteArray(16)
        `is`.read(iv)
        val bytes = toByteArray(`is`)
        val vec = IvParameterSpec(iv)
        val skeySpec = SecretKeySpec(key, "AES")
        val cipher = Cipher.getInstance("AES/CBC/PKCS5PADDING")
        cipher.init(Cipher.DECRYPT_MODE, skeySpec, vec)
        return cipher.doFinal(bytes)
    }

    @Throws(NoSuchAlgorithmException::class)
    fun calcMd5(bytes: ByteArray): String = bytes.let {
        val md = MessageDigest.getInstance("MD5")
        md.update(bytes, 0, it.size)
        val hash = md.digest()
        val byteArray = Base64.getEncoder().encode(hash)
        return String(byteArray)
    }


    @Throws(NoSuchAlgorithmException::class, InvalidKeyException::class)
    fun getHmacSHA1(payload: String, secret: String): String {
        val hmac = Mac.getInstance(HMAC_SHA_1)
        hmac.init(SecretKeySpec(secret.toByteArray(StandardCharsets.UTF_8), HMAC_SHA_1))
        val bytes = hmac.doFinal(payload.toByteArray(StandardCharsets.UTF_8))
        return String.format("%040x", BigInteger(1, bytes))
    }

    @Throws(IOException::class)
    fun toByteArray(input: InputStream): ByteArray = input.let { inputStream ->
        ByteArrayOutputStream().use { output ->
            var n: Int
            val buffer = ByteArray(1024 * 4)
            while (-1 != inputStream.read(buffer).also { n = it }) {
                output.write(buffer, 0, n)
            }
            return output.toByteArray()
        }
    }


    @Throws(IOException::class)
    fun getResource(name: String): ByteArray {
        val classLoader = Util::class.java.classLoader
        classLoader.getResourceAsStream(name).use { resourceAsStream -> return toByteArray(resourceAsStream) }
    }

    fun mentionLen(txt: String): Int {
        val matcher = pattern.matcher(txt)
        return if (matcher.find()) {
            matcher.group().length + 1
        } else 0
    }

    fun mentionStart(txt: String): Int {
        val matcher = pattern.matcher(txt)
        return if (matcher.find()) {
            matcher.start() - 1
        } else 0
    }
}
