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

package scripts

import com.wire.kalium.plugins.libs
import getLocalProperty
import io.gitlab.arturbosch.detekt.Detekt

plugins {
    id("io.gitlab.arturbosch.detekt")
}

dependencies {
    val detektVersion = libs.findVersion("detekt").get()
    detekt("io.gitlab.arturbosch.detekt:detekt-cli:$detektVersion")
    detektPlugins("io.gitlab.arturbosch.detekt:detekt-formatting:$detektVersion")
    detektPlugins("io.gitlab.arturbosch.detekt:detekt-rules-libraries:$detektVersion")
    detektPlugins("com.wire:detekt-rules:1.0.0-1.23.6") {
        isChanging = true
    }
}

detekt {
    buildUponDefaultConfig = true
    // activate all available (even unstable) rules.
    // allRules = false
    config.setFrom(files("$rootDir/detekt/detekt.yml"))
    source.setFrom(files("$rootDir"))
    // a way of suppressing issues before introducing detekt
    baseline = file("$rootDir/detekt/baseline.xml")

    // dynamic prop to enable and disable autocorrect, enabled locally via local.properties file
    val autoFixEnabled = getLocalProperty("detektAutofix", "false")
    autoCorrect = autoFixEnabled.toBoolean()
    println("> Detekt autoCorrect: $autoFixEnabled")
}

tasks.withType<Detekt> {
    reports.html.required.set(true) // observe findings in your browser with structure and code snippets
    reports.xml.required.set(true)
    reports.txt.required.set(false)

    // general detekt ignore patterns of files, instead of by rule
    exclude(
        "buildSrc/**",
        "**/build/**",
        "**/test/**",
        "**/*Test/**",
        "**/protobuf/**",
    )
}

// configurations.matching { it.name == "detekt" }.all {
//     resolutionStrategy.eachDependency {
//         if (requested.group == "org.jetbrains.kotlin") {
//             useVersion("1.9.23")
//         }
//     }
// }
