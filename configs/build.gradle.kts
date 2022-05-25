import Customization.defaultBuildtimeConfiguration
import com.codingfeline.buildkonfig.compiler.FieldSpec.Type.STRING
import com.codingfeline.buildkonfig.compiler.FieldSpec.Type.BOOLEAN
import com.codingfeline.buildkonfig.compiler.FieldSpec.Type.INT
import  configModels.Configs


plugins {
    Plugins.multiplatform(this)
    Plugins.buildKonfigId(this)

}

version = "1.0"

kotlin {
    jvm {
        compilations.all {
            kotlinOptions.jvmTarget = "1.8"
        }
        testRuns["test"].executionTask.configure {
            useJUnit()
        }
    }

    sourceSets {
        val commonMain by getting

    }
}

buildkonfig {
    val buildtimeConfiguration = defaultBuildtimeConfiguration(rootDir = rootDir)


    packageName = "com.wire.kalium"
    objectName = "KaliumConfig"

    // default config is required
    defaultConfigs {
        buildConfigField(
            INT,
            Configs.maxAccounts.name, "${buildtimeConfiguration?.configuration?.get(Configs.maxAccounts.name)}"
        )

        buildConfigField(
            STRING,
            Configs.backendUrl.name, "${buildtimeConfiguration?.configuration?.get(Configs.backendUrl.name)}"
        )
        buildConfigField(
            STRING,
            Configs.websocketUrl.name, "${buildtimeConfiguration?.configuration?.get(Configs.websocketUrl.name)}"
        )

        buildConfigField(
            BOOLEAN,
            Configs.allow_account_creation.name,
            "${buildtimeConfiguration?.configuration?.get(Configs.allow_account_creation.name)}"
        )
        buildConfigField(
            BOOLEAN,
            Configs.allowSSO.name, "${buildtimeConfiguration?.configuration?.get(Configs.allowSSO.name)}"
        )

        buildConfigField(
            STRING,
            Configs.supportEmail.name, "${buildtimeConfiguration?.configuration?.get(Configs.supportEmail.name)}"
        )

        buildConfigField(
            STRING,
            Configs.firebasePushSenderId.name,
            "${buildtimeConfiguration?.configuration?.get(Configs.firebasePushSenderId.name)}"
        )
        buildConfigField(
            STRING,
            Configs.firebaseAppId.name,
            "${buildtimeConfiguration?.configuration?.get(Configs.firebaseAppId.name)}"
        )

        buildConfigField(
            BOOLEAN,
            Configs.submitCrashReports.name,
            "${buildtimeConfiguration?.configuration?.get(Configs.submitCrashReports.name)}"
        )

        buildConfigField(
            BOOLEAN,
            Configs.allowMarketingCommunication.name,
            "${buildtimeConfiguration?.configuration?.get(Configs.allowMarketingCommunication.name)}"
        )

        buildConfigField(
            BOOLEAN,
            Configs.enableBlacklist.name,
            "${buildtimeConfiguration?.configuration?.get(Configs.enableBlacklist.name)}"
        )

        buildConfigField(
            STRING,
            Configs.blacklistHost.name,
            "${buildtimeConfiguration?.configuration?.get(Configs.blacklistHost.name)}"
        )

        buildConfigField(
            BOOLEAN,
            Configs.allowChangeOfEmail.name,
            "${buildtimeConfiguration?.configuration?.get(Configs.allowChangeOfEmail.name)}"
        )

        buildConfigField(
            STRING,
            Configs.teamsUrl.name, "${buildtimeConfiguration?.configuration?.get(Configs.teamsUrl.name)}"
        )
        buildConfigField(
            STRING,
            Configs.accountsUrl.name, "${buildtimeConfiguration?.configuration?.get(Configs.accountsUrl.name)}"
        )
        buildConfigField(
            STRING,
            Configs.websiteUrl.name, "${buildtimeConfiguration?.configuration?.get(Configs.websiteUrl.name)}"
        )
        buildConfigField(
            STRING,
            Configs.custom_url_scheme.name,
            "${buildtimeConfiguration?.configuration?.get(Configs.custom_url_scheme.name)}"
        )

        buildConfigField(
            INT,
            Configs.new_password_minimum_length.name,
            "${buildtimeConfiguration?.configuration?.get(Configs.new_password_minimum_length.name)}"
        )
        buildConfigField(
            INT,
            Configs.new_password_maximum_length.name,
            "${buildtimeConfiguration?.configuration?.get(Configs.new_password_maximum_length.name)}"
        )

        buildConfigField(
            STRING,
            Configs.http_proxy_url.name,
            "${buildtimeConfiguration?.configuration?.get(Configs.http_proxy_url.name)}"
        )

        buildConfigField(
            STRING,
            Configs.http_proxy_port.name,
            "${buildtimeConfiguration?.configuration?.get(Configs.http_proxy_port.name)}"
        )

        buildConfigField(
            BOOLEAN,
            Configs.block_on_jailbreak_or_root.name,
            "${buildtimeConfiguration?.configuration?.get(Configs.block_on_jailbreak_or_root.name)}"
        )

        buildConfigField(
            BOOLEAN,
            Configs.force_hide_screen_content.name,
            "${buildtimeConfiguration?.configuration?.get(Configs.force_hide_screen_content.name)}"
        )

        buildConfigField(
            BOOLEAN,
            Configs.force_app_lock.name,
            "${buildtimeConfiguration?.configuration?.get(Configs.force_app_lock.name)}"
        )

        buildConfigField(
            INT,
            Configs.app_lock_timeout.name,
            "${buildtimeConfiguration?.configuration?.get(Configs.app_lock_timeout.name)}"
        )

        buildConfigField(
            BOOLEAN,
            Configs.block_on_password_policy.name,
            "${buildtimeConfiguration?.configuration?.get(Configs.block_on_password_policy.name)}"
        )

        buildConfigField(
            BOOLEAN,
            Configs.wipe_on_cookie_invalid.name,
            "${buildtimeConfiguration?.configuration?.get(Configs.wipe_on_cookie_invalid.name)}"
        )

        buildConfigField(
            BOOLEAN,
            Configs.force_private_keyboard.name,
            "${buildtimeConfiguration?.configuration?.get(Configs.force_private_keyboard.name)}"
        )

        buildConfigField(
            INT,
            Configs.password_max_attempts.name,
            "${buildtimeConfiguration?.configuration?.get(Configs.password_max_attempts.name)}"
        )

        buildConfigField(
            BOOLEAN,
            Configs.file_restriction_enabled.name,
            "${buildtimeConfiguration?.configuration?.get(Configs.file_restriction_enabled.name)}"
        )

        buildConfigField(
            STRING,
            Configs.file_restriction_list.name,
            "${buildtimeConfiguration?.configuration?.get(Configs.file_restriction_list.name)}"
        )

        buildConfigField(
            STRING,
            Configs.countly_server_url.name,
            "${buildtimeConfiguration?.configuration?.get(Configs.countly_server_url.name)}"
        )

        buildConfigField(
            STRING,
            Configs.countly_app_key.name,
            "${buildtimeConfiguration?.configuration?.get(Configs.countly_app_key.name)}"
        )


        buildConfigField(
            BOOLEAN,
            Configs.force_constant_bitrate_calls.name,
            "${buildtimeConfiguration?.configuration?.get(Configs.force_constant_bitrate_calls.name)}"
        )

        buildConfigField(
            BOOLEAN,
            Configs.web_link_preview.name,
            "${buildtimeConfiguration?.configuration?.get(Configs.web_link_preview.name)}"
        )

        buildConfigField(
            BOOLEAN,
            Configs.keep_websocket_on.name,
            "${buildtimeConfiguration?.configuration?.get(Configs.keep_websocket_on.name)}"
        )

        val certificatePinMap = buildtimeConfiguration?.configuration?.get(Configs.certificatePin.name) as Map<String, String>
        buildConfigField(
            STRING,
            "${Configs.certificatePin.name}_${Configs.domain.name}",
            "${certificatePinMap.get(Configs.domain.name)}"
        )

        buildConfigField(
            STRING,
            "${Configs.certificatePin.name}_${Configs.certificate.name}",
            "${certificatePinMap.get(Configs.certificate.name)}"
        )
        val prodMap = buildtimeConfiguration?.configuration?.get(Configs.prod.name) as Map<String, String>
        buildConfigField(
            STRING,
            "${Configs.prod.name}_${Configs.applicationId.name}",
            "${prodMap[Configs.applicationId.name]}"
        )
        buildConfigField(
            STRING,
            "${Configs.prod.name}_${Configs.userId.name}",
            "${prodMap[Configs.userId.name]}"
        )

        buildConfigField(
            STRING,
            "${Configs.prod.name}${Configs._comment.name}",
            "${prodMap[Configs._comment.name]}"
        )

        buildConfigField(
            STRING,
            "${Configs.prod.name}_${Configs.appName.name}",
            "${prodMap[Configs.appName.name]}"
        )

        buildConfigField(
            STRING,
            "${Configs.prod.name}_${Configs.launcherIcon.name}",
            "${prodMap[Configs.launcherIcon.name]}"
        )
        buildConfigField(
            BOOLEAN,
            "${Configs.prod.name}_${Configs.developer_features_enabled.name}",
            "${prodMap[Configs.developer_features_enabled.name]}"
        )

        buildConfigField(
            BOOLEAN,
            "${Configs.prod.name}_${Configs.safe_logging.name}",
            "${prodMap[Configs.safe_logging.name]}"
        )

        buildConfigField(
            BOOLEAN,
            "${Configs.prod.name}_${Configs.logging_enabled.name}",
            "${prodMap[Configs.logging_enabled.name]}"
        )


        val devMap = buildtimeConfiguration?.configuration?.get(Configs.dev.name) as Map<String, String>
        buildConfigField(
            STRING,
            "${Configs.dev.name}_${Configs.applicationId.name}",
            "${devMap[Configs.applicationId.name]}"
        )
        buildConfigField(
            STRING,
            "${Configs.dev.name}_${Configs.userId.name}",
            "${devMap[Configs.userId.name]}"
        )

        buildConfigField(
            STRING,
            "${Configs.dev.name}${Configs._comment.name}",
            "${devMap[Configs._comment.name]}"
        )

        buildConfigField(
            STRING,
            "${Configs.dev.name}_${Configs.appName.name}",
            "${devMap[Configs.appName.name]}"
        )

        buildConfigField(
            STRING,
            "${Configs.dev.name}_${Configs.launcherIcon.name}",
            "${devMap[Configs.launcherIcon.name]}"
        )
        buildConfigField(
            BOOLEAN,
            "${Configs.dev.name}_${Configs.developer_features_enabled.name}",
            "${devMap[Configs.developer_features_enabled.name]}"
        )

        buildConfigField(
            BOOLEAN,
            "${Configs.dev.name}_${Configs.safe_logging.name}",
            "${devMap[Configs.safe_logging.name]}"
        )

        buildConfigField(
            BOOLEAN,
            "${Configs.dev.name}_${Configs.logging_enabled.name}",
            "${devMap[Configs.logging_enabled.name]}"
        )


        val candidateMap = buildtimeConfiguration?.configuration?.get(Configs.candidate.name) as Map<String, String>
        buildConfigField(
            STRING,
            "${Configs.candidate.name}_${Configs.applicationId.name}",
            "${candidateMap[Configs.applicationId.name]}"
        )
        buildConfigField(
            STRING,
            "${Configs.candidate.name}_${Configs.userId.name}",
            "${candidateMap[Configs.userId.name]}"
        )

        buildConfigField(
            STRING,
            "${Configs.candidate.name}${Configs._comment.name}",
            "${candidateMap[Configs._comment.name]}"
        )

        buildConfigField(
            STRING,
            "${Configs.candidate.name}_${Configs.appName.name}",
            "${candidateMap[Configs.appName.name]}"
        )

        buildConfigField(
            STRING,
            "${Configs.candidate.name}_${Configs.launcherIcon.name}",
            "${candidateMap[Configs.launcherIcon.name]}"
        )
        buildConfigField(
            BOOLEAN,
            "${Configs.candidate.name}_${Configs.developer_features_enabled.name}",
            "${candidateMap[Configs.developer_features_enabled.name]}"
        )

        buildConfigField(
            BOOLEAN,
            "${Configs.candidate.name}_${Configs.safe_logging.name}",
            "${candidateMap[Configs.safe_logging.name]}"
        )

        buildConfigField(
            BOOLEAN,
            "${Configs.candidate.name}_${Configs.logging_enabled.name}",
            "${candidateMap[Configs.logging_enabled.name]}"
        )


        val internalMap = buildtimeConfiguration?.configuration?.get(Configs.internal.name) as Map<String, String>
        buildConfigField(
            STRING,
            "${Configs.internal.name}_${Configs.applicationId.name}",
            "${internalMap[Configs.applicationId.name]}"
        )
        buildConfigField(
            STRING,
            "${Configs.internal.name}_${Configs.userId.name}",
            "${internalMap[Configs.userId.name]}"
        )

        buildConfigField(
            STRING,
            "${Configs.internal.name}${Configs._comment.name}",
            "${internalMap[Configs._comment.name]}"
        )

        buildConfigField(
            STRING,
            "${Configs.internal.name}_${Configs.appName.name}",
            "${internalMap[Configs.appName.name]}"
        )

        buildConfigField(
            STRING,
            "${Configs.internal.name}_${Configs.launcherIcon.name}",
            "${internalMap[Configs.launcherIcon.name]}"
        )
        buildConfigField(
            BOOLEAN,
            "${Configs.internal.name}_${Configs.developer_features_enabled.name}",
            "${internalMap[Configs.developer_features_enabled.name]}"
        )

        buildConfigField(
            BOOLEAN,
            "${Configs.internal.name}_${Configs.safe_logging.name}",
            "${internalMap[Configs.safe_logging.name]}"
        )

        buildConfigField(
            BOOLEAN,
            "${Configs.internal.name}_${Configs.logging_enabled.name}",
            "${internalMap[Configs.logging_enabled.name]}"
        )


        val experimentalMap = buildtimeConfiguration?.configuration?.get(Configs.experimental.name) as Map<String, String>
        buildConfigField(
            STRING,
            "${Configs.experimental.name}_${Configs.applicationId.name}",
            "${experimentalMap[Configs.applicationId.name]}"
        )
        buildConfigField(
            STRING,
            "${Configs.experimental.name}_${Configs.userId.name}",
            "${experimentalMap[Configs.userId.name]}"
        )

        buildConfigField(
            STRING,
            "${Configs.experimental.name}${Configs._comment.name}",
            "${experimentalMap[Configs._comment.name]}"
        )

        buildConfigField(
            STRING,
            "${Configs.experimental.name}_${Configs.appName.name}",
            "${experimentalMap[Configs.appName.name]}"
        )

        buildConfigField(
            STRING,
            "${Configs.experimental.name}_${Configs.launcherIcon.name}",
            "${experimentalMap[Configs.launcherIcon.name]}"
        )
        buildConfigField(
            BOOLEAN,
            "${Configs.experimental.name}_${Configs.developer_features_enabled.name}",
            "${experimentalMap[Configs.developer_features_enabled.name]}"
        )

        buildConfigField(
            BOOLEAN,
            "${Configs.experimental.name}_${Configs.safe_logging.name}",
            "${experimentalMap[Configs.safe_logging.name]}"
        )

        buildConfigField(
            BOOLEAN,
            "${Configs.experimental.name}_${Configs.logging_enabled.name}",
            "${experimentalMap[Configs.logging_enabled.name]}"
        )


        val fdroidMap = buildtimeConfiguration?.configuration?.get(Configs.fdroid.name) as Map<String, String>
        buildConfigField(
            STRING,
            "${Configs.fdroid.name}_${Configs.applicationId.name}",
            "${fdroidMap[Configs.applicationId.name]}"
        )
        buildConfigField(
            STRING,
            "${Configs.fdroid.name}_${Configs.userId.name}",
            "${fdroidMap[Configs.userId.name]}"
        )

        buildConfigField(
            STRING,
            "${Configs.fdroid.name}${Configs._comment.name}",
            "${fdroidMap[Configs._comment.name]}"
        )

        buildConfigField(
            STRING,
            "${Configs.fdroid.name}_${Configs.appName.name}",
            "${fdroidMap[Configs.appName.name]}"
        )

        buildConfigField(
            STRING,
            "${Configs.fdroid.name}_${Configs.launcherIcon.name}",
            "${fdroidMap[Configs.launcherIcon.name]}"
        )
        buildConfigField(
            BOOLEAN,
            "${Configs.fdroid.name}_${Configs.developer_features_enabled.name}",
            "${fdroidMap[Configs.developer_features_enabled.name]}"
        )

        buildConfigField(
            BOOLEAN,
            "${Configs.fdroid.name}_${Configs.safe_logging.name}",
            "${fdroidMap[Configs.safe_logging.name]}"
        )

        buildConfigField(
            BOOLEAN,
            "${Configs.fdroid.name}_${Configs.logging_enabled.name}",
            "${fdroidMap[Configs.logging_enabled.name]}"
        )
    }


    // we can add targeted configs for each platform separately
//    targetConfigs {
//        android{
//
//        }
//    }
}


