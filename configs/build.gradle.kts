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
            Configs.MAX_ACCOUNTS.name, "${buildtimeConfiguration?.configuration?.get(Configs.MAX_ACCOUNTS.value)}"
        )

        buildConfigField(
            STRING,
            Configs.BACKEND_URL.name, "${buildtimeConfiguration?.configuration?.get(Configs.BACKEND_URL.value)}"
        )
        buildConfigField(
            STRING,
            Configs.WEBSITE_URL.name, "${buildtimeConfiguration?.configuration?.get(Configs.WEBSITE_URL.value)}"
        )

        buildConfigField(
            BOOLEAN,
            Configs.ALLOW_ACCOUNT_CREATION.name,
            "${buildtimeConfiguration?.configuration?.get(Configs.ALLOW_ACCOUNT_CREATION.value)}"
        )
        buildConfigField(
            BOOLEAN,
            Configs.ALLOW_SSO.name, "${buildtimeConfiguration?.configuration?.get(Configs.ALLOW_SSO.value)}"
        )

        buildConfigField(
            STRING,
            Configs.SUPPORT_EMAIL.name, "${buildtimeConfiguration?.configuration?.get(Configs.SUPPORT_EMAIL.value)}"
        )

        buildConfigField(
            STRING,
            Configs.FIREBASE_PUSH_SENDER_ID.name,
            "${buildtimeConfiguration?.configuration?.get(Configs.FIREBASE_PUSH_SENDER_ID.value)}"
        )
        buildConfigField(
            STRING,
            Configs.FIREBASE_APP_ID.name,
            "${buildtimeConfiguration?.configuration?.get(Configs.FIREBASE_APP_ID.value)}"
        )

        buildConfigField(
            BOOLEAN,
            Configs.SUBMIT_CRASH_REPORTS.name,
            "${buildtimeConfiguration?.configuration?.get(Configs.SUBMIT_CRASH_REPORTS.value)}"
        )

        buildConfigField(
            BOOLEAN,
            Configs.ALLOW_MARKETING_COMMUNICATION.name,
            "${buildtimeConfiguration?.configuration?.get(Configs.ALLOW_MARKETING_COMMUNICATION.value)}"
        )

        buildConfigField(
            BOOLEAN,
            Configs.ENABLE_BLACK_LIST.name,
            "${buildtimeConfiguration?.configuration?.get(Configs.ENABLE_BLACK_LIST.value)}"
        )

        buildConfigField(
            STRING,
            Configs.BLACKLIST_HOST.name,
            "${buildtimeConfiguration?.configuration?.get(Configs.BLACKLIST_HOST.value)}"
        )

        buildConfigField(
            BOOLEAN,
            Configs.ALLOW_CHANGE_OF_EMAIL.name,
            "${buildtimeConfiguration?.configuration?.get(Configs.ALLOW_CHANGE_OF_EMAIL.value)}"
        )

        buildConfigField(
            STRING,
            Configs.TEAMS_URL.name, "${buildtimeConfiguration?.configuration?.get(Configs.TEAMS_URL.value)}"
        )
        buildConfigField(
            STRING,
            Configs.ACCOUNT_URL.name, "${buildtimeConfiguration?.configuration?.get(Configs.ACCOUNT_URL.value)}"
        )
        buildConfigField(
            STRING,
            Configs.WEBSITE_URL.name, "${buildtimeConfiguration?.configuration?.get(Configs.WEBSITE_URL.value)}"
        )
        buildConfigField(
            STRING,
            Configs.CUSTOM_URL_SCHEME.name,
            "${buildtimeConfiguration?.configuration?.get(Configs.CUSTOM_URL_SCHEME.value)}"
        )

        buildConfigField(
            INT,
            Configs.NEW_PASSWORD_MINIMUM_LENGTH.name,
            "${buildtimeConfiguration?.configuration?.get(Configs.NEW_PASSWORD_MINIMUM_LENGTH.value)}"
        )
        buildConfigField(
            INT,
            Configs.NEW_PASSWORD_MAXIMUM_LENGTH.name,
            "${buildtimeConfiguration?.configuration?.get(Configs.NEW_PASSWORD_MAXIMUM_LENGTH.value)}"
        )

        buildConfigField(
            STRING,
            Configs.HTTP_PROXY_URL.name,
            "${buildtimeConfiguration?.configuration?.get(Configs.HTTP_PROXY_URL.value)}"
        )

        buildConfigField(
            STRING,
            Configs.HTTP_PROXY_PORT.name,
            "${buildtimeConfiguration?.configuration?.get(Configs.HTTP_PROXY_PORT.value)}"
        )

        buildConfigField(
            BOOLEAN,
            Configs.BLOCK_ON_JAILBREAK_OR_ROOT.name,
            "${buildtimeConfiguration?.configuration?.get(Configs.BLOCK_ON_JAILBREAK_OR_ROOT.value)}"
        )

        buildConfigField(
            BOOLEAN,
            Configs.FORCE_HIDE_SCREEN_CONTENT.name,
            "${buildtimeConfiguration?.configuration?.get(Configs.FORCE_HIDE_SCREEN_CONTENT.value)}"
        )

        buildConfigField(
            BOOLEAN,
            Configs.FORCE_APP_LOCK.name,
            "${buildtimeConfiguration?.configuration?.get(Configs.FORCE_APP_LOCK.value)}"
        )

        buildConfigField(
            INT,
            Configs.APP_LOCK_TIMEOUT.name,
            "${buildtimeConfiguration?.configuration?.get(Configs.APP_LOCK_TIMEOUT.value)}"
        )

        buildConfigField(
            BOOLEAN,
            Configs.BLOCK_ON_PASSWORD_POLICY.name,
            "${buildtimeConfiguration?.configuration?.get(Configs.BLOCK_ON_PASSWORD_POLICY.value)}"
        )

        buildConfigField(
            BOOLEAN,
            Configs.WIPE_ON_COOKIE_INVALID.name,
            "${buildtimeConfiguration?.configuration?.get(Configs.WIPE_ON_COOKIE_INVALID.value)}"
        )

        buildConfigField(
            BOOLEAN,
            Configs.FORCE_PRIVATE_KEYBOARD.name,
            "${buildtimeConfiguration?.configuration?.get(Configs.FORCE_PRIVATE_KEYBOARD.value)}"
        )

        buildConfigField(
            INT,
            Configs.PASSWORD_MAX_ATTEMPTS.name,
            "${buildtimeConfiguration?.configuration?.get(Configs.PASSWORD_MAX_ATTEMPTS.value)}"
        )

        buildConfigField(
            BOOLEAN,
            Configs.FILE_RESTRICTION_ENABLED.name,
            "${buildtimeConfiguration?.configuration?.get(Configs.FILE_RESTRICTION_ENABLED.value)}"
        )

        buildConfigField(
            STRING,
            Configs.FILE_RESTRICTION_LIST.name,
            "${buildtimeConfiguration?.configuration?.get(Configs.FILE_RESTRICTION_LIST.value)}"
        )

        buildConfigField(
            STRING,
            Configs.COUNTLY_SERVER_URL.name,
            "${buildtimeConfiguration?.configuration?.get(Configs.COUNTLY_SERVER_URL.value)}"
        )

        buildConfigField(
            STRING,
            Configs.COUNTLY_APP_KEY.name,
            "${buildtimeConfiguration?.configuration?.get(Configs.COUNTLY_APP_KEY.value)}"
        )


        buildConfigField(
            BOOLEAN,
            Configs.FORCE_CONSTANT_BITRATE_CALLS.name,
            "${buildtimeConfiguration?.configuration?.get(Configs.FORCE_CONSTANT_BITRATE_CALLS.value)}"
        )

        buildConfigField(
            BOOLEAN,
            Configs.WEB_LINK_PREVIEW.name,
            "${buildtimeConfiguration?.configuration?.get(Configs.WEB_LINK_PREVIEW.value)}"
        )

        buildConfigField(
            BOOLEAN,
            Configs.KEEP_WEB_SOCKET_ON.name,
            "${buildtimeConfiguration?.configuration?.get(Configs.KEEP_WEB_SOCKET_ON.value)}"
        )

        val certificatePinMap = buildtimeConfiguration?.configuration?.get(Configs.CERTIFICATE_PIN.value) as Map<*, *>
        buildConfigField(
            STRING,
            "${Configs.CERTIFICATE_PIN.name}_${Configs.DOMAIN.name}",
            "${certificatePinMap[Configs.DOMAIN.value]}"
        )

        buildConfigField(
            STRING,
            "${Configs.CERTIFICATE_PIN.name}_${Configs.CERTIFICATE.name}",
            "${certificatePinMap[Configs.CERTIFICATE.value]}"
        )
        val prodMap = buildtimeConfiguration.configuration.get(Configs.PROD.value) as Map<*, *>
        buildConfigField(
            STRING,
            "${Configs.PROD.name}_${Configs.APPLICATION_ID.name}",
            "${prodMap[Configs.APPLICATION_ID.value]}"
        )
        buildConfigField(
            STRING,
            "${Configs.PROD.name}_${Configs.USER_ID.name}",
            "${prodMap[Configs.USER_ID.value]}"
        )

        buildConfigField(
            STRING,
            "${Configs.PROD.name}${Configs.COMMENT.name}",
            "${prodMap[Configs.COMMENT.value]}"
        )

        buildConfigField(
            STRING,
            "${Configs.PROD.name}_${Configs.APP_NAME.name}",
            "${prodMap[Configs.APP_NAME.value]}"
        )

        buildConfigField(
            STRING,
            "${Configs.PROD.name}_${Configs.LAUNCHER_ICON.name}",
            "${prodMap[Configs.LAUNCHER_ICON.value]}"
        )
        buildConfigField(
            BOOLEAN,
            "${Configs.PROD.name}_${Configs.DEVELOPER_FEATURES_ENABLED.name}",
            "${prodMap[Configs.DEVELOPER_FEATURES_ENABLED.value]}"
        )

        buildConfigField(
            BOOLEAN,
            "${Configs.PROD.name}_${Configs.SAFE_LOGGING.name}",
            "${prodMap[Configs.SAFE_LOGGING.value]}"
        )

        buildConfigField(
            BOOLEAN,
            "${Configs.PROD.name}_${Configs.LOGGING_ENABLED.name}",
            "${prodMap[Configs.LOGGING_ENABLED.value]}"
        )


        val devMap = buildtimeConfiguration.configuration[Configs.DEV.value] as Map<*, *>
        buildConfigField(
            STRING,
            "${Configs.DEV.name}_${Configs.APPLICATION_ID.name}",
            "${devMap[Configs.APPLICATION_ID.value]}"
        )
        buildConfigField(
            STRING,
            "${Configs.DEV.name}_${Configs.USER_ID.name}",
            "${devMap[Configs.USER_ID.value]}"
        )

        buildConfigField(
            STRING,
            "${Configs.DEV.name}${Configs.COMMENT.name}",
            "${devMap[Configs.COMMENT.value]}"
        )

        buildConfigField(
            STRING,
            "${Configs.DEV.name}_${Configs.APP_NAME.name}",
            "${devMap[Configs.APP_NAME.value]}"
        )

        buildConfigField(
            STRING,
            "${Configs.DEV.name}_${Configs.LAUNCHER_ICON.name}",
            "${devMap[Configs.LAUNCHER_ICON.value]}"
        )
        buildConfigField(
            BOOLEAN,
            "${Configs.DEV.name}_${Configs.DEVELOPER_FEATURES_ENABLED.name}",
            "${devMap[Configs.DEVELOPER_FEATURES_ENABLED.value]}"
        )

        buildConfigField(
            BOOLEAN,
            "${Configs.DEV.name}_${Configs.SAFE_LOGGING.name}",
            "${devMap[Configs.SAFE_LOGGING.value]}"
        )

        buildConfigField(
            BOOLEAN,
            "${Configs.DEV.name}_${Configs.LOGGING_ENABLED.name}",
            "${devMap[Configs.LOGGING_ENABLED.value]}"
        )


        val candidateMap = buildtimeConfiguration.configuration[Configs.CANDIDATE.value] as Map<*, *>
        buildConfigField(
            STRING,
            "${Configs.CANDIDATE.name}_${Configs.APPLICATION_ID.name}",
            "${candidateMap[Configs.APPLICATION_ID.value]}"
        )
        buildConfigField(
            STRING,
            "${Configs.CANDIDATE.name}_${Configs.USER_ID.name}",
            "${candidateMap[Configs.USER_ID.value]}"
        )

        buildConfigField(
            STRING,
            "${Configs.CANDIDATE.name}${Configs.COMMENT.name}",
            "${candidateMap[Configs.COMMENT.value]}"
        )

        buildConfigField(
            STRING,
            "${Configs.CANDIDATE.name}_${Configs.APP_NAME.name}",
            "${candidateMap[Configs.APP_NAME.value]}"
        )

        buildConfigField(
            STRING,
            "${Configs.CANDIDATE.name}_${Configs.LAUNCHER_ICON.name}",
            "${candidateMap[Configs.LAUNCHER_ICON.value]}"
        )
        buildConfigField(
            BOOLEAN,
            "${Configs.CANDIDATE.name}_${Configs.DEVELOPER_FEATURES_ENABLED.name}",
            "${candidateMap[Configs.DEVELOPER_FEATURES_ENABLED.value]}"
        )

        buildConfigField(
            BOOLEAN,
            "${Configs.CANDIDATE.name}_${Configs.SAFE_LOGGING.name}",
            "${candidateMap[Configs.SAFE_LOGGING.value]}"
        )

        buildConfigField(
            BOOLEAN,
            "${Configs.CANDIDATE.name}_${Configs.LOGGING_ENABLED.name}",
            "${candidateMap[Configs.LOGGING_ENABLED.value]}"
        )


        val internalMap = buildtimeConfiguration.configuration[Configs.INTERNAL.value] as Map<*, *>
        buildConfigField(
            STRING,
            "${Configs.INTERNAL.name}_${Configs.APPLICATION_ID.name}",
            "${internalMap[Configs.APPLICATION_ID.value]}"
        )
        buildConfigField(
            STRING,
            "${Configs.INTERNAL.name}_${Configs.USER_ID.name}",
            "${internalMap[Configs.USER_ID.value]}"
        )

        buildConfigField(
            STRING,
            "${Configs.INTERNAL.name}${Configs.COMMENT.name}",
            "${internalMap[Configs.COMMENT.value]}"
        )

        buildConfigField(
            STRING,
            "${Configs.INTERNAL.name}_${Configs.APP_NAME.name}",
            "${internalMap[Configs.APP_NAME.value]}"
        )

        buildConfigField(
            STRING,
            "${Configs.INTERNAL.name}_${Configs.LAUNCHER_ICON.name}",
            "${internalMap[Configs.LAUNCHER_ICON.value]}"
        )
        buildConfigField(
            BOOLEAN,
            "${Configs.INTERNAL.name}_${Configs.DEVELOPER_FEATURES_ENABLED.name}",
            "${internalMap[Configs.DEVELOPER_FEATURES_ENABLED.value]}"
        )

        buildConfigField(
            BOOLEAN,
            "${Configs.INTERNAL.name}_${Configs.SAFE_LOGGING.name}",
            "${internalMap[Configs.SAFE_LOGGING.value]}"
        )

        buildConfigField(
            BOOLEAN,
            "${Configs.INTERNAL.name}_${Configs.LOGGING_ENABLED.name}",
            "${internalMap[Configs.LOGGING_ENABLED.value]}"
        )


        val experimentalMap = buildtimeConfiguration.configuration[Configs.EXPERIMENTAL.value] as Map<*, *>
        buildConfigField(
            STRING,
            "${Configs.EXPERIMENTAL.name}_${Configs.APPLICATION_ID.name}",
            "${experimentalMap[Configs.APPLICATION_ID.value]}"
        )
        buildConfigField(
            STRING,
            "${Configs.EXPERIMENTAL.name}_${Configs.USER_ID.name}",
            "${experimentalMap[Configs.USER_ID.value]}"
        )

        buildConfigField(
            STRING,
            "${Configs.EXPERIMENTAL.name}${Configs.COMMENT.name}",
            "${experimentalMap[Configs.COMMENT.value]}"
        )

        buildConfigField(
            STRING,
            "${Configs.EXPERIMENTAL.name}_${Configs.APP_NAME.name}",
            "${experimentalMap[Configs.APP_NAME.value]}"
        )

        buildConfigField(
            STRING,
            "${Configs.EXPERIMENTAL.name}_${Configs.LAUNCHER_ICON.name}",
            "${experimentalMap[Configs.LAUNCHER_ICON.value]}"
        )
        buildConfigField(
            BOOLEAN,
            "${Configs.EXPERIMENTAL.name}_${Configs.DEVELOPER_FEATURES_ENABLED.name}",
            "${experimentalMap[Configs.DEVELOPER_FEATURES_ENABLED.value]}"
        )

        buildConfigField(
            BOOLEAN,
            "${Configs.EXPERIMENTAL.name}_${Configs.SAFE_LOGGING.name}",
            "${experimentalMap[Configs.SAFE_LOGGING.value]}"
        )

        buildConfigField(
            BOOLEAN,
            "${Configs.EXPERIMENTAL.name}_${Configs.LOGGING_ENABLED.name}",
            "${experimentalMap[Configs.LOGGING_ENABLED.value]}"
        )


        val fdroidMap = buildtimeConfiguration.configuration[Configs.FDROID.value] as Map<*, *>
        buildConfigField(
            STRING,
            "${Configs.FDROID.name}_${Configs.APPLICATION_ID.name}",
            "${fdroidMap[Configs.APPLICATION_ID.value]}"
        )
        buildConfigField(
            STRING,
            "${Configs.FDROID.name}_${Configs.USER_ID.name}",
            "${fdroidMap[Configs.USER_ID.value]}"
        )

        buildConfigField(
            STRING,
            "${Configs.FDROID.name}${Configs.COMMENT.name}",
            "${fdroidMap[Configs.COMMENT.value]}"
        )

        buildConfigField(
            STRING,
            "${Configs.FDROID.name}_${Configs.APP_NAME.name}",
            "${fdroidMap[Configs.APP_NAME.value]}"
        )

        buildConfigField(
            STRING,
            "${Configs.FDROID.name}_${Configs.LAUNCHER_ICON.name}",
            "${fdroidMap[Configs.LAUNCHER_ICON.value]}"
        )
        buildConfigField(
            BOOLEAN,
            "${Configs.FDROID.name}_${Configs.DEVELOPER_FEATURES_ENABLED.name}",
            "${fdroidMap[Configs.DEVELOPER_FEATURES_ENABLED.value]}"
        )

        buildConfigField(
            BOOLEAN,
            "${Configs.FDROID.name}_${Configs.SAFE_LOGGING.name}",
            "${fdroidMap[Configs.SAFE_LOGGING.value]}"
        )

        buildConfigField(
            BOOLEAN,
            "${Configs.FDROID.name}_${Configs.LOGGING_ENABLED.name}",
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
