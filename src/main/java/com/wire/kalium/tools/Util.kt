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
package com.wire.kalium.tools

import kotlin.Throws
import java.io.IOException
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec
import javax.crypto.spec.IvParameterSpec
import java.io.ByteArrayOutputStream
import java.io.ByteArrayInputStream
import java.security.NoSuchAlgorithmException
import java.security.spec.InvalidKeySpecException
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import java.security.spec.KeySpec
import javax.crypto.spec.PBEKeySpec
import java.io.BufferedReader
import java.io.FileReader
import java.io.FileInputStream
import java.io.BufferedWriter
import java.io.FileWriter
import java.security.MessageDigest
import java.math.BigInteger
import java.io.File
import java.io.InputStream
import java.net.URLConnection
import java.nio.charset.StandardCharsets
import java.security.InvalidKeyException
import java.util.Base64
import java.util.regex.Pattern
import javax.crypto.Mac

object Util {
    private val pattern = Pattern.compile("(?<=@)([a-zA-Z0-9\\_]{3,})")
    private val HMAC_SHA_1: String? = "HmacSHA1"
    @Throws(Exception::class)
    fun encrypt(key: ByteArray?, dataToSend: ByteArray?, iv: ByteArray?): ByteArray? {
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

    @Throws(NoSuchAlgorithmException::class, InvalidKeySpecException::class)
    fun genKey(password: CharArray?, salt: ByteArray?): SecretKey? {
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec: KeySpec = PBEKeySpec(password, salt, 65536, 256)
        val tmp = factory.generateSecret(spec)
        return SecretKeySpec(tmp.encoded, "AES")
    }

    @Throws(IOException::class)
    fun readLine(file: File?): String? {
        BufferedReader(FileReader(file)).use { br -> return br.readLine() }
    }

    @Throws(IOException::class)
    fun readFile(f: File?): String? {
        FileInputStream(f).use { fis ->
            val data = ByteArray(f.length() as Int)
            fis.read(data)
            return String(data, StandardCharsets.UTF_8)
        }
    }

    @Throws(IOException::class)
    fun writeLine(line: String?, file: File?) {
        BufferedWriter(FileWriter(file)).use { bw -> bw.write(line) }
    }

    @Throws(NoSuchAlgorithmException::class)
    fun calcMd5(bytes: ByteArray?): String? {
        val md = MessageDigest.getInstance("MD5")
        md.update(bytes, 0, bytes.size)
        val hash = md.digest()
        val byteArray = Base64.getEncoder().encode(hash)
        return String(byteArray)
    }

    fun digest(md: MessageDigest?, bytes: ByteArray?): String? {
        md.update(bytes, 0, bytes.size)
        val hash = md.digest()
        val byteArray = Base64.getEncoder().encode(hash)
        return String(byteArray)
    }

    @Throws(NoSuchAlgorithmException::class, InvalidKeyException::class)
    fun getHmacSHA1(payload: String?, secret: String?): String? {
        val hmac = Mac.getInstance(HMAC_SHA_1)
        hmac.init(SecretKeySpec(secret.toByteArray(StandardCharsets.UTF_8), HMAC_SHA_1))
        val bytes = hmac.doFinal(payload.toByteArray(StandardCharsets.UTF_8))
        return String.format("%040x", BigInteger(1, bytes))
    }

    @Throws(IOException::class)
    fun toByteArray(input: InputStream?): ByteArray? {
        ByteArrayOutputStream().use { output ->
            var n: Int
            val buffer = ByteArray(1024 * 4)
            while (-1 != input.read(buffer).also { n = it }) {
                output.write(buffer, 0, n)
            }
            return output.toByteArray()
        }
    }

    fun compareAuthorizations(auth1: String?, auth2: String?): Boolean {
        if (auth1 == null || auth2 == null) return false
        val token1 = extractToken(auth1)
        val token2 = extractToken(auth2)
        return token1 == token2
    }

    fun extractToken(auth: String?): String? {
        val split: Array<String?> = auth.split(" ").toTypedArray()
        return if (split.size == 1) split[0] else split[1]
    }

    @Throws(IOException::class)
    fun extractMimeType(imageData: ByteArray?): String? {
        ByteArrayInputStream(imageData).use { input ->
            val contentType = URLConnection.guessContentTypeFromStream(input)
            return contentType ?: "image/xyz"
        }
    }

    @Throws(IOException::class)
    fun getResource(name: String?): ByteArray? {
        val classLoader = Util::class.java.classLoader
        classLoader.getResourceAsStream(name).use { resourceAsStream -> return toByteArray(resourceAsStream) }
    }

    fun mentionLen(txt: String?): Int {
        val matcher = pattern.matcher(txt)
        return if (matcher.find()) {
            matcher.group().length + 1
        } else 0
    }

    fun mentionStart(txt: String?): Int {
        val matcher = pattern.matcher(txt)
        return if (matcher.find()) {
            matcher.start() - 1
        } else 0
    }
}
