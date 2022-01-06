package com.wire.kalium.cli

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream

object CLIUtils {

    @Throws(IOException::class)
    fun getResource(name: String): ByteArray {
        val classLoader = CLIUtils::class.java.classLoader
        classLoader.getResourceAsStream(name).use { resourceAsStream -> return toByteArray(resourceAsStream) }
    }

    @Throws(IOException::class)
    fun toByteArray(input: InputStream?): ByteArray = input?.let { inputStream ->
        ByteArrayOutputStream().use { output ->
            var n: Int
            val buffer = ByteArray(1024 * 4)
            while (-1 != inputStream.read(buffer).also { n = it }) {
                output.write(buffer, 0, n)
            }
            return output.toByteArray()
        }
    } ?: ByteArray(16)
}
