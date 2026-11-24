/* Wire, Copyright (C) 2026 Wire Swiss GmbH, SPDX-License-Identifier: GPL-3.0-or-later */
package com.wire.kalium.persistence.protection

import java.io.File
import java.net.URLClassLoader
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertTrue

internal fun runStorageProcessProbe(probe: Class<*>, root: File, vararg arguments: String) {
    val classpath = generateSequence(probe.classLoader) { it.parent }
        .filterIsInstance<URLClassLoader>()
        .flatMap { it.urLs.asSequence() }
        .map { File(it.toURI()).path }
        .toList() + System.getProperty("java.class.path").split(File.pathSeparator)
    val java = File(System.getProperty("java.home"), "bin/java").path
    val log = File.createTempFile("process-", ".log", root)
    val process = ProcessBuilder(java, "-cp", classpath.distinct().joinToString(File.pathSeparator), probe.name, *arguments)
        .redirectErrorStream(true)
        .redirectOutput(log)
        .start()
    try {
        assertTrue(process.waitFor(30, TimeUnit.SECONDS), "Storage process timed out")
        assertEquals(0, process.exitValue(), log.readText().takeLast(3000))
    } finally {
        if (process.isAlive) process.destroyForcibly().waitFor()
        log.delete()
    }
}
