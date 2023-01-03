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
    detektPlugins("com.wire:detekt-rules:1.0.0-SNAPSHOT") {
        isChanging = true
    }
}

configurations {
    all {
        resolutionStrategy {
            eachDependency {
                if (requested.group == "com.wire" && requested.name == "detekt-rules") {
                    cacheChangingModulesFor(0, "SECONDS") // disable cache for jar dependency
                }
            }
        }
    }
}

detekt {
    buildUponDefaultConfig = true
    // activate all available (even unstable) rules.
    // allRules = false
    config = files("$rootDir/detekt/detekt.yml")
    source = files("$rootDir")
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

    exclude(
        "buildSrc/**",
        "**/build/**",
        "**/test/**",
        "**/*Test/**",
        "**/protobuf/**",
    )
}
