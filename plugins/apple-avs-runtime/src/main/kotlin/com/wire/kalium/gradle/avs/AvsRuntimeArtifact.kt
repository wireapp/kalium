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

package com.wire.kalium.gradle.avs

import java.util.Properties

internal object AvsRuntimeArtifact {
    private val metadata = Properties().apply {
        val input = checkNotNull(
            AvsRuntimeArtifact::class.java.getResourceAsStream("avs-runtime.properties")
        ) {
            "Baked Apple AVS runtime metadata is missing from the plugin"
        }
        input.use(::load)
    }

    val version: String = metadata.requiredProperty("version")

    /** Validated once here so every task and the cache can treat it as already normalized. */
    val archiveSha256: String = normalizedChecksum(metadata.requiredProperty("archiveSha256"))
    val macosMinimumVersion: String = metadata.requiredProperty("macosMinimumVersion")
    val archiveUrl: String =
        "https://github.com/wireapp/wire-avs/releases/download/$version/avs.xcframework.zip"
}

private fun Properties.requiredProperty(name: String): String =
    requireNotNull(getProperty(name)?.takeIf(String::isNotBlank)) {
        "Baked Apple AVS runtime metadata '$name' is missing"
    }
