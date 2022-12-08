package com.wire.kalium.plugins

import org.jetbrains.kotlin.gradle.targets.js.dsl.KotlinJsTargetDsl

fun KotlinJsTargetDsl.commonJsConfig(enableJsTests: Boolean) {
    browser {
//                     // Not needed for now, but if we include UI with CSS in the future, we can enable it
//                     commonWebpackConfig {
//                         cssSupport.enabled = true
//                     }
        testTask {
            enabled = enableJsTests
            useMocha {
                timeout = "5s"
            }
        }
    }
}
