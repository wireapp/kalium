package com.wire.kalium.logic.feature.rootDetection

import android.annotation.SuppressLint
import java.io.File

actual class RootDetectorImpl actual constructor() : RootDetector {

    actual override fun isSystemRooted(): Boolean {
        // Lambdas so they can be executed lazily
        val releaseTagsExist = {
            // Fallback to true in case of failure
            getSystemProperty("ro.build.tags")?.contains("release-keys") ?: true
        }
        val otacertsExist = { File("/etc/security/otacerts.zip").exists() }
        val canRunSu = { runCommand("su") }

        // Return eagerly on the first root detection
        return !releaseTagsExist() || !otacertsExist() || canRunSu()
    }

    @SuppressLint("PrivateApi")
    /**
     * Attempts to read a system property based on the provided [key].
     * @return The property's value, or null if it wasn't possible to read it.
     */
    private fun getSystemProperty(key: String): String? = try {
        Class.forName("android.os.SystemProperties")
            .getMethod("get", String::class.java)
            .invoke(null, key) as? String
    } catch (nsm: NoSuchMethodException) {
        null
    }

    @Suppress("SwallowedException", "TooGenericExceptionCaught")
    private fun runCommand(command: String): Boolean =
        try {
            Runtime.getRuntime().exec(command).exitValue() == 0
        } catch (e: Exception) {
            false
        }
}
