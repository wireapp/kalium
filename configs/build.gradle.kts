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
            Configs.MAX_ACCOUNTS.value, "${buildtimeConfiguration?.configuration?.get(Configs.MAX_ACCOUNTS.value)}"
        )

        buildConfigField(
            STRING,
            Configs.BACKEND_URL.value, "${buildtimeConfiguration?.configuration?.get(Configs.BACKEND_URL.value)}"
        )
        buildConfigField(
            STRING,
            Configs.WEBSITE_URL.value, "${buildtimeConfiguration?.configuration?.get(Configs.WEBSITE_URL.value)}"
        )

        buildConfigField(
            BOOLEAN,
            Configs.ALLOW_ACCOUNT_CREATION.value,
            "${buildtimeConfiguration?.configuration?.get(Configs.ALLOW_ACCOUNT_CREATION.value)}"
        )
        buildConfigField(
            BOOLEAN,
            Configs.ALLOW_SSO.value, "${buildtimeConfiguration?.configuration?.get(Configs.ALLOW_SSO.value)}"
        )

        buildConfigField(
            STRING,
            Configs.SUPPORT_EMAIL.value, "${buildtimeConfiguration?.configuration?.get(Configs.SUPPORT_EMAIL.value)}"
        )

        buildConfigField(
            STRING,
            Configs.FIREBASE_PUSH_SENDER_ID.value,
            "${buildtimeConfiguration?.configuration?.get(Configs.FIREBASE_PUSH_SENDER_ID.value)}"
        )
        buildConfigField(
            STRING,
            Configs.FIREBASE_APP_ID.value,
            "${buildtimeConfiguration?.configuration?.get(Configs.FIREBASE_APP_ID.value)}"
        )

        buildConfigField(
            BOOLEAN,
            Configs.SUBMIT_CRASH_REPORTS.value,
            "${buildtimeConfiguration?.configuration?.get(Configs.SUBMIT_CRASH_REPORTS.value)}"
        )

        buildConfigField(
            BOOLEAN,
            Configs.ALLOW_MARKETING_COMMUNICATION.value,
            "${buildtimeConfiguration?.configuration?.get(Configs.ALLOW_MARKETING_COMMUNICATION.value)}"
        )

        buildConfigField(
            BOOLEAN,
            Configs.ENABLE_BLACK_LIST.value,
            "${buildtimeConfiguration?.configuration?.get(Configs.ENABLE_BLACK_LIST.value)}"
        )

        buildConfigField(
            STRING,
            Configs.BLACKLIST_HOST.value,
            "${buildtimeConfiguration?.configuration?.get(Configs.BLACKLIST_HOST.value)}"
        )

        buildConfigField(
            BOOLEAN,
            Configs.ALLOW_CHANGE_OF_EMAIL.value,
            "${buildtimeConfiguration?.configuration?.get(Configs.ALLOW_CHANGE_OF_EMAIL.value)}"
        )

        buildConfigField(
            STRING,
            Configs.TEAMS_URL.value, "${buildtimeConfiguration?.configuration?.get(Configs.TEAMS_URL.value)}"
        )
        buildConfigField(
            STRING,
            Configs.ACCOUNT_URL.value, "${buildtimeConfiguration?.configuration?.get(Configs.ACCOUNT_URL.value)}"
        )
        buildConfigField(
            STRING,
            Configs.WEBSITE_URL.value, "${buildtimeConfiguration?.configuration?.get(Configs.WEBSITE_URL.value)}"
        )
        buildConfigField(
            STRING,
            Configs.CUSTOM_URL_SCHEME.value,
            "${buildtimeConfiguration?.configuration?.get(Configs.CUSTOM_URL_SCHEME.value)}"
        )

        buildConfigField(
            INT,
            Configs.NEW_PASSWORD_MINIMUM_LENGTH.value,
            "${buildtimeConfiguration?.configuration?.get(Configs.NEW_PASSWORD_MINIMUM_LENGTH.value)}"
        )
        buildConfigField(
            INT,
            Configs.NEW_PASSWORD_MAXIMUM_LENGTH.value,
            "${buildtimeConfiguration?.configuration?.get(Configs.NEW_PASSWORD_MAXIMUM_LENGTH.value)}"
        )

        buildConfigField(
            STRING,
            Configs.HTTP_PROXY_URL.value,
            "${buildtimeConfiguration?.configuration?.get(Configs.HTTP_PROXY_URL.value)}"
        )

        buildConfigField(
            STRING,
            Configs.HTTP_PROXY_PORT.value,
            "${buildtimeConfiguration?.configuration?.get(Configs.HTTP_PROXY_PORT.value)}"
        )

        buildConfigField(
            BOOLEAN,
            Configs.BLOCK_ON_JAILBREAK_OR_ROOT.value,
            "${buildtimeConfiguration?.configuration?.get(Configs.BLOCK_ON_JAILBREAK_OR_ROOT.value)}"
        )

        buildConfigField(
            BOOLEAN,
            Configs.FORCE_HIDE_SCREEN_CONTENT.value,
            "${buildtimeConfiguration?.configuration?.get(Configs.FORCE_HIDE_SCREEN_CONTENT.value)}"
        )

        buildConfigField(
            BOOLEAN,
            Configs.FORCE_APP_LOCK.value,
            "${buildtimeConfiguration?.configuration?.get(Configs.FORCE_APP_LOCK.value)}"
        )

        buildConfigField(
            INT,
            Configs.APP_LOCK_TIMEOUT.value,
            "${buildtimeConfiguration?.configuration?.get(Configs.APP_LOCK_TIMEOUT.value)}"
        )

        buildConfigField(
            BOOLEAN,
            Configs.BLOCK_ON_PASSWORD_POLICY.value,
            "${buildtimeConfiguration?.configuration?.get(Configs.BLOCK_ON_PASSWORD_POLICY.value)}"
        )

        buildConfigField(
            BOOLEAN,
            Configs.WIPE_ON_COOKIE_INVALID.value,
            "${buildtimeConfiguration?.configuration?.get(Configs.WIPE_ON_COOKIE_INVALID.value)}"
        )

        buildConfigField(
            BOOLEAN,
            Configs.FORCE_PRIVATE_KEYBOARD.value,
            "${buildtimeConfiguration?.configuration?.get(Configs.FORCE_PRIVATE_KEYBOARD.value)}"
        )

        buildConfigField(
            INT,
            Configs.PASSWORD_MAX_ATTEMPTS.value,
            "${buildtimeConfiguration?.configuration?.get(Configs.PASSWORD_MAX_ATTEMPTS.value)}"
        )

        buildConfigField(
            BOOLEAN,
            Configs.FILE_RESTRICTION_ENABLED.value,
            "${buildtimeConfiguration?.configuration?.get(Configs.FILE_RESTRICTION_ENABLED.value)}"
        )

        buildConfigField(
            STRING,
            Configs.FILE_RESTRICTION_LIST.value,
            "${buildtimeConfiguration?.configuration?.get(Configs.FILE_RESTRICTION_LIST.value)}"
        )

        buildConfigField(
            STRING,
            Configs.COUNTLY_SERVER_URL.value,
            "${buildtimeConfiguration?.configuration?.get(Configs.COUNTLY_SERVER_URL.value)}"
        )

        buildConfigField(
            STRING,
            Configs.COUNTLY_APP_KEY.value,
            "${buildtimeConfiguration?.configuration?.get(Configs.COUNTLY_APP_KEY.value)}"
        )


        buildConfigField(
            BOOLEAN,
            Configs.FORCE_CONSTANT_BITRATE_CALLS.value,
            "${buildtimeConfiguration?.configuration?.get(Configs.FORCE_CONSTANT_BITRATE_CALLS.value)}"
        )

        buildConfigField(
            BOOLEAN,
            Configs.WEB_LINK_PREVIEW.value,
            "${buildtimeConfiguration?.configuration?.get(Configs.WEB_LINK_PREVIEW.value)}"
        )

        buildConfigField(
            BOOLEAN,
            Configs.KEEP_WEB_SOCKET_ON.value,
            "${buildtimeConfiguration?.configuration?.get(Configs.KEEP_WEB_SOCKET_ON.value)}"
        )

        val certificatePinMap = buildtimeConfiguration?.configuration?.get(Configs.CERTIFICATE_PIN.value) as Map<*, *>
        buildConfigField(
            STRING,
            "${Configs.CERTIFICATE_PIN.value}_${Configs.DOMAIN.value}",
            "${certificatePinMap[Configs.DOMAIN.value]}"
        )

        buildConfigField(
            STRING,
            "${Configs.CERTIFICATE_PIN.value}_${Configs.CERTIFICATE.value}",
            "${certificatePinMap[Configs.CERTIFICATE.value]}"
        )
        val prodMap = buildtimeConfiguration.configuration.get(Configs.PROD.value) as Map<*, *>
        buildConfigField(
            STRING,
            "${Configs.PROD.value}_${Configs.APPLICATION_ID.value}",
            "${prodMap[Configs.APPLICATION_ID.value]}"
        )
        buildConfigField(
            STRING,
            "${Configs.PROD.value}_${Configs.USER_ID.value}",
            "${prodMap[Configs.USER_ID.value]}"
        )

        buildConfigField(
            STRING,
            "${Configs.PROD.value}${Configs.COMMENT.value}",
            "${prodMap[Configs.COMMENT.value]}"
        )

        buildConfigField(
            STRING,
            "${Configs.PROD.value}_${Configs.APP_NAME.value}",
            "${prodMap[Configs.APP_NAME.value]}"
        )

        buildConfigField(
            STRING,
            "${Configs.PROD.value}_${Configs.LAUNCHER_ICON.value}",
            "${prodMap[Configs.LAUNCHER_ICON.value]}"
        )
        buildConfigField(
            BOOLEAN,
            "${Configs.PROD.value}_${Configs.DEVELOPER_FEATURES_ENABLED.value}",
            "${prodMap[Configs.DEVELOPER_FEATURES_ENABLED.value]}"
        )

        buildConfigField(
            BOOLEAN,
            "${Configs.PROD.value}_${Configs.SAFE_LOGGING.value}",
            "${prodMap[Configs.SAFE_LOGGING.value]}"
        )

        buildConfigField(
            BOOLEAN,
            "${Configs.PROD.value}_${Configs.LOGGING_ENABLED.value}",
            "${prodMap[Configs.LOGGING_ENABLED.value]}"
        )


        val devMap = buildtimeConfiguration.configuration[Configs.DEV.value] as Map<*, *>
        buildConfigField(
            STRING,
            "${Configs.DEV.value}_${Configs.APPLICATION_ID.value}",
            "${devMap[Configs.APPLICATION_ID.value]}"
        )
        buildConfigField(
            STRING,
            "${Configs.DEV.value}_${Configs.USER_ID.value}",
            "${devMap[Configs.USER_ID.value]}"
        )

        buildConfigField(
            STRING,
            "${Configs.DEV.value}${Configs.COMMENT.value}",
            "${devMap[Configs.COMMENT.value]}"
        )

        buildConfigField(
            STRING,
            "${Configs.DEV.value}_${Configs.APP_NAME.value}",
            "${devMap[Configs.APP_NAME.value]}"
        )

        buildConfigField(
            STRING,
            "${Configs.DEV.value}_${Configs.LAUNCHER_ICON.value}",
            "${devMap[Configs.LAUNCHER_ICON.value]}"
        )
        buildConfigField(
            BOOLEAN,
            "${Configs.DEV.value}_${Configs.DEVELOPER_FEATURES_ENABLED.value}",
            "${devMap[Configs.DEVELOPER_FEATURES_ENABLED.value]}"
        )

        buildConfigField(
            BOOLEAN,
            "${Configs.DEV.value}_${Configs.SAFE_LOGGING.value}",
            "${devMap[Configs.SAFE_LOGGING.value]}"
        )

        buildConfigField(
            BOOLEAN,
            "${Configs.DEV.value}_${Configs.LOGGING_ENABLED.value}",
            "${devMap[Configs.LOGGING_ENABLED.value]}"
        )


        val candidateMap = buildtimeConfiguration.configuration[Configs.CANDIDATE.value] as Map<*, *>
        buildConfigField(
            STRING,
            "${Configs.CANDIDATE.value}_${Configs.APPLICATION_ID.value}",
            "${candidateMap[Configs.APPLICATION_ID.value]}"
        )
        buildConfigField(
            STRING,
            "${Configs.CANDIDATE.value}_${Configs.USER_ID.value}",
            "${candidateMap[Configs.USER_ID.value]}"
        )

        buildConfigField(
            STRING,
            "${Configs.CANDIDATE.value}${Configs.COMMENT.value}",
            "${candidateMap[Configs.COMMENT.value]}"
        )

        buildConfigField(
            STRING,
            "${Configs.CANDIDATE.value}_${Configs.APP_NAME.value}",
            "${candidateMap[Configs.APP_NAME.value]}"
        )

        buildConfigField(
            STRING,
            "${Configs.CANDIDATE.value}_${Configs.LAUNCHER_ICON.value}",
            "${candidateMap[Configs.LAUNCHER_ICON.value]}"
        )
        buildConfigField(
            BOOLEAN,
            "${Configs.CANDIDATE.value}_${Configs.DEVELOPER_FEATURES_ENABLED.value}",
            "${candidateMap[Configs.DEVELOPER_FEATURES_ENABLED.value]}"
        )

        buildConfigField(
            BOOLEAN,
            "${Configs.CANDIDATE.value}_${Configs.SAFE_LOGGING.value}",
            "${candidateMap[Configs.SAFE_LOGGING.value]}"
        )

        buildConfigField(
            BOOLEAN,
            "${Configs.CANDIDATE.value}_${Configs.LOGGING_ENABLED.value}",
            "${candidateMap[Configs.LOGGING_ENABLED.value]}"
        )


        val internalMap = buildtimeConfiguration.configuration[Configs.INTERNAL.value] as Map<*, *>
        buildConfigField(
            STRING,
            "${Configs.INTERNAL.value}_${Configs.APPLICATION_ID.value}",
            "${internalMap[Configs.APPLICATION_ID.value]}"
        )
        buildConfigField(
            STRING,
            "${Configs.INTERNAL.value}_${Configs.USER_ID.value}",
            "${internalMap[Configs.USER_ID.value]}"
        )

        buildConfigField(
            STRING,
            "${Configs.INTERNAL.value}${Configs.COMMENT.value}",
            "${internalMap[Configs.COMMENT.value]}"
        )

        buildConfigField(
            STRING,
            "${Configs.INTERNAL.value}_${Configs.APP_NAME.value}",
            "${internalMap[Configs.APP_NAME.value]}"
        )

        buildConfigField(
            STRING,
            "${Configs.INTERNAL.value}_${Configs.LAUNCHER_ICON.value}",
            "${internalMap[Configs.LAUNCHER_ICON.value]}"
        )
        buildConfigField(
            BOOLEAN,
            "${Configs.INTERNAL.value}_${Configs.DEVELOPER_FEATURES_ENABLED.value}",
            "${internalMap[Configs.DEVELOPER_FEATURES_ENABLED.value]}"
        )

        buildConfigField(
            BOOLEAN,
            "${Configs.INTERNAL.value}_${Configs.SAFE_LOGGING.value}",
            "${internalMap[Configs.SAFE_LOGGING.value]}"
        )

        buildConfigField(
            BOOLEAN,
            "${Configs.INTERNAL.value}_${Configs.LOGGING_ENABLED.value}",
            "${internalMap[Configs.LOGGING_ENABLED.value]}"
        )


        val experimentalMap = buildtimeConfiguration.configuration[Configs.EXPERIMENTAL.value] as Map<*, *>
        buildConfigField(
            STRING,
            "${Configs.EXPERIMENTAL.value}_${Configs.APPLICATION_ID.value}",
            "${experimentalMap[Configs.APPLICATION_ID.value]}"
        )
        buildConfigField(
            STRING,
            "${Configs.EXPERIMENTAL.value}_${Configs.USER_ID.value}",
            "${experimentalMap[Configs.USER_ID.value]}"
        )

        buildConfigField(
            STRING,
            "${Configs.EXPERIMENTAL.value}${Configs.COMMENT.value}",
            "${experimentalMap[Configs.COMMENT.value]}"
        )

        buildConfigField(
            STRING,
            "${Configs.EXPERIMENTAL.value}_${Configs.APP_NAME.value}",
            "${experimentalMap[Configs.APP_NAME.value]}"
        )

        buildConfigField(
            STRING,
            "${Configs.EXPERIMENTAL.value}_${Configs.LAUNCHER_ICON.value}",
            "${experimentalMap[Configs.LAUNCHER_ICON.value]}"
        )
        buildConfigField(
            BOOLEAN,
            "${Configs.EXPERIMENTAL.value}_${Configs.DEVELOPER_FEATURES_ENABLED.value}",
            "${experimentalMap[Configs.DEVELOPER_FEATURES_ENABLED.value]}"
        )

        buildConfigField(
            BOOLEAN,
            "${Configs.EXPERIMENTAL.value}_${Configs.SAFE_LOGGING.value}",
            "${experimentalMap[Configs.SAFE_LOGGING.value]}"
        )

        buildConfigField(
            BOOLEAN,
            "${Configs.EXPERIMENTAL.value}_${Configs.LOGGING_ENABLED.value}",
            "${experimentalMap[Configs.LOGGING_ENABLED.value]}"
        )


        val fdroidMap = buildtimeConfiguration.configuration[Configs.FDROID.value] as Map<*, *>
        buildConfigField(
            STRING,
            "${Configs.FDROID.value}_${Configs.APPLICATION_ID.value}",
            "${fdroidMap[Configs.APPLICATION_ID.value]}"
        )
        buildConfigField(
            STRING,
            "${Configs.FDROID.value}_${Configs.USER_ID.value}",
            "${fdroidMap[Configs.USER_ID.value]}"
        )

        buildConfigField(
            STRING,
            "${Configs.FDROID.value}${Configs.COMMENT.value}",
            "${fdroidMap[Configs.COMMENT.value]}"
        )

        buildConfigField(
            STRING,
            "${Configs.FDROID.value}_${Configs.APP_NAME.value}",
            "${fdroidMap[Configs.APP_NAME.value]}"
        )

        buildConfigField(
            STRING,
            "${Configs.FDROID.value}_${Configs.LAUNCHER_ICON.value}",
            "${fdroidMap[Configs.LAUNCHER_ICON.value]}"
        )
        buildConfigField(
            BOOLEAN,
            "${Configs.FDROID.value}_${Configs.DEVELOPER_FEATURES_ENABLED.value}",
            "${fdroidMap[Configs.DEVELOPER_FEATURES_ENABLED.value]}"
        )

        buildConfigField(
            BOOLEAN,
            "${Configs.FDROID.value}_${Configs.SAFE_LOGGING.value}",
            "${fdroidMap[Configs.SAFE_LOGGING.value]}"
        )

        buildConfigField(
            BOOLEAN,
            "${Configs.FDROID.value}_${Configs.LOGGING_ENABLED.value}",
            "${fdroidMap[Configs.LOGGING_ENABLED.value]}"
        )
    }


    // we can add targeted configs for each platform separately
//    targetConfigs {
//        android{
//
//        }
//    }
}
