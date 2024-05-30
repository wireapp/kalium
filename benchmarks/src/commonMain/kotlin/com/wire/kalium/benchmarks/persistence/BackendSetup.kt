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
package com.wire.kalium.benchmarks.persistence

import org.json.JSONObject
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.DataOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.io.UnsupportedEncodingException
import java.net.HttpCookie
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.UUID
import javax.ws.rs.core.MediaType

class BackendSetup(private val backendUrl: String, private val basicAuth: BasicAuth) {

    data class ClientUser(
        val name: String,
        val email: String,
        val password: String,
        var id: String?
    )

    fun createUser(): ClientUser {
        var user = generateRandomClientUser()
        user = registerNewUser(user)
        val activationCode = getActivationCodeForEmail(user.email)
        activateRegisteredEmailByBackdoorCode(user.email, activationCode)
        return user;
    }

    private fun generateRandomClientUser(): ClientUser {
        val name = "Benchmark"
        val password = "Aqa123456!"
        val email = "benchmark" + UUID.randomUUID().toString().replace("[^A-Za-z0-9]".toRegex(), "") + "@wire.com"
        return ClientUser(name, email, password, id = null)
    }

    private fun registerNewUser(user: ClientUser): ClientUser {
        val c: HttpURLConnection = buildDefaultRequest("register", MediaType.APPLICATION_JSON)
        val requestBody = JSONObject()
        requestBody.put("email", user.email)
        requestBody.put("name", user.name)
        requestBody.put("password", user.password)
        val response: String = httpPost(c, requestBody.toString())
        val `object`: JSONObject = JSONObject(response)
        user.id = `object`.getString("id")
        val cookiesHeader = c.getHeaderField("Set-Cookie")
        val cookies = HttpCookie.parse(cookiesHeader)
        // val cookie: AccessCookie = AccessCookie("zuid", cookies)
        // user.setAccessCredentials(AccessCredentials(null, cookie))
        return user
    }

    fun getActivationCodeForEmail(email: String?): String {
        val c: HttpURLConnection = buildDefaultRequestOnBackdoor(
            String.format("i/users/activation-code?email=%s", uriEncode(email)), MediaType.APPLICATION_JSON
        )
        val output: String = httpGet(c)
        return JSONObject(output).getString("code")
    }

    private fun activateRegisteredEmailByBackdoorCode(email: String, code: String) {
        val c: HttpURLConnection = buildDefaultRequest("activate", MediaType.APPLICATION_JSON)
        val requestBody = JSONObject()
        requestBody.put("email", email)
        requestBody.put("code", code)
        requestBody.put("dryrun", false)
        httpPost(c, requestBody.toString())
    }

    private fun buildDefaultRequestOnBackdoor(path: String, mediaType: String): HttpURLConnection {
        val url = URL(backendUrl + path);
        var c: HttpURLConnection? = null
        try {
            c = url.openConnection() as HttpURLConnection
        } catch (e: IOException) {
            throw RuntimeException(e)
        }
        c.setRequestProperty("Content-Type", mediaType)
        c.setRequestProperty("Accept", mediaType)
        c.setRequestProperty("Authorization", basicAuth.encoded)
        return c
    }

    private fun buildDefaultRequest(path: String, mediaType: String): HttpURLConnection {
        val url = URL(backendUrl + path);
        var c: HttpURLConnection? = null
        try {
            c = url.openConnection() as HttpURLConnection
        } catch (e: IOException) {
            throw RuntimeException(e)
        }
        c!!.setRequestProperty("Content-Type", mediaType)
        c!!.setRequestProperty("Accept", mediaType)
        return c
    }

    // TODO: The request code needs to be put into separate class
    private fun httpGet(c: HttpURLConnection): String {
        var response = ""
        try {
            c.requestMethod = "GET"
            response = readStream(c.inputStream)
            return response
        } catch (e: IOException) {
            response = readStream(c.errorStream)
            return response
        } finally {
            c.disconnect()
        }
    }

    private fun httpPost(c: HttpURLConnection, requestBody: String): String {
        var response = ""
        try {
            c.requestMethod = "POST"
            c.doOutput = true
            writeStream(requestBody, c.outputStream)
            response = readStream(c.inputStream)
            return response
        } catch (e: IOException) {
            try {
                response = readStream(c.errorStream)
            } catch (ex: IOException) {
            }
            return response
        } finally {
            c.disconnect()
        }
    }

    @Throws(IOException::class)
    private fun readStream(`is`: InputStream?): String {
        if (`is` != null) {
            BufferedReader(InputStreamReader(`is`)).use { `in` ->
                var inputLine: String?
                val content = StringBuilder()
                while ((`in`.readLine().also { inputLine = it }) != null) {
                    content.append(inputLine)
                }
                return content.toString()
            }
        }
        return ""
    }

    @Throws(IOException::class)
    private fun writeStream(data: String, os: OutputStream) {
        val wr = DataOutputStream(os)
        val writer = BufferedWriter(OutputStreamWriter(wr, StandardCharsets.UTF_8))
        try {
            writer.write(data)
        } finally {
            writer.close()
            wr.close()
        }
    }

    fun uriEncode(s: String?): String {
        try {
            return URLEncoder.encode(s, "utf-8")
        } catch (e: UnsupportedEncodingException) {
            throw IllegalArgumentException(e)
        }
    }
}
