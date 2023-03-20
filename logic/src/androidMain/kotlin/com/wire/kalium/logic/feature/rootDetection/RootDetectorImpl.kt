package com.wire.kalium.logic.feature.rootDetection

import java.io.File

actual class RootDetectorImpl actual constructor() : RootDetector {
    override fun isSystemRooted(): Boolean {
        val releaseTagsExist = getSystemProperty("ro.build.tags").contains("release-keys")
        val otacertsExist = File("/etc/security/otacerts.zip").exists()
        val canRunSu = runCommand("su")

        return !releaseTagsExist || !otacertsExist || canRunSu
    }

    private fun getSystemProperty(key: String): List<String>  {
        val result = Class.forName("android.os.SystemProperties")
            .getMethod("get", List::class.java as Class<out List<String>>)
            .invoke(null, key) as? List<String>

        return result ?: emptyList()
    }

    private fun runCommand(command: String): Boolean =
        try {
            Runtime.getRuntime().exec(command).exitValue() == 0
        } catch (e: Exception) {
            false
        }
}
