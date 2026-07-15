/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.wire.kalium.sample.callrecorder

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.security.SecureRandom
import java.util.Base64

/** Intentionally plain-text, single-file configuration for quickly testing this sample locally. */
@Suppress("LongParameterList")
internal data class LocalTestingConfig(
    val email: String,
    val password: String,
    val backendDomain: String,
    val apiUrl: String,
    val webSocketUrl: String,
    val selfConversations: String = "auto",
    val apiVersion: Int = 12,
    val federation: Boolean = true,
    val onPremises: Boolean = true,
    val verificationCode: String? = null,
    val loginLabel: String = "kalium-call-recorder",
    val clientLabel: String = "kalium-call-recorder",
    val clientModel: String = "Kalium call recorder",
    val userAgent: String = "Kalium-Call-Recorder/experimental",
    val teamId: String? = null,
    val audioCbr: Boolean = false,
    val avsReadyTimeoutSeconds: Long = 30,
    val stateDir: String = "./call-recorder-state",
    val cryptoDir: String = "./call-recorder-crypto",
    val mlsCiphersuite: String? = null,
    val accountsUrl: String? = null,
    val blackListUrl: String? = null,
    val teamsUrl: String? = null,
    val websiteUrl: String? = null,
    val userId: String? = null,
    val userDomain: String? = null,
    val clientId: String? = null,
    val stateKeyBase64: String? = null,
    val proteusPassphraseBase64: String? = null,
    val mlsPassphraseBase64: String? = null,
) {
    fun withGeneratedSecrets(): LocalTestingConfig = copy(
        stateKeyBase64 = stateKeyBase64 ?: randomBase64Key(),
        proteusPassphraseBase64 = proteusPassphraseBase64 ?: randomBase64Key(),
        mlsPassphraseBase64 = mlsPassphraseBase64 ?: randomBase64Key(),
    )

    fun withNormalizedSelfConversations(): LocalTestingConfig =
        if (selfConversations.isBlank() || selfConversations.equals("auto", ignoreCase = true) || '|' !in selfConversations) {
            copy(selfConversations = "auto")
        } else {
            this
        }

    fun withDiscoveredBackend(apiVersion: Int, federation: Boolean): LocalTestingConfig = copy(
        apiVersion = apiVersion,
        federation = federation,
    )

    fun withIdentity(
        userId: String,
        userDomain: String,
        clientId: String,
        mlsCiphersuite: String,
    ): LocalTestingConfig = copy(
        userId = userId,
        userDomain = userDomain,
        clientId = clientId,
        mlsCiphersuite = mlsCiphersuite,
    )

    private fun randomBase64Key(): String = Base64.getEncoder().encodeToString(
        ByteArray(KEY_SIZE_BYTES).also(SecureRandom()::nextBytes),
    )

    private companion object {
        const val KEY_SIZE_BYTES = 32
    }
}

internal class LocalTestingConfigFile(private val path: Path) {
    fun load(): LocalTestingConfig {
        require(Files.exists(path)) { "Test config JSON does not exist: $path" }
        val json = Files.readString(path)
        return LocalTestingConfig(
            email = json.requiredString("email"),
            password = json.requiredString("password"),
            backendDomain = json.requiredString("backendDomain"),
            apiUrl = json.requiredString("apiUrl"),
            webSocketUrl = json.requiredString("webSocketUrl"),
            selfConversations = json.optionalString("selfConversations") ?: "auto",
            apiVersion = json.optionalInt("apiVersion") ?: 12,
            federation = json.optionalBoolean("federation") ?: true,
            onPremises = json.optionalBoolean("onPremises") ?: true,
            verificationCode = json.optionalString("verificationCode"),
            loginLabel = json.optionalString("loginLabel") ?: "kalium-call-recorder",
            clientLabel = json.optionalString("clientLabel") ?: "kalium-call-recorder",
            clientModel = json.optionalString("clientModel") ?: "Kalium call recorder",
            userAgent = json.optionalString("userAgent") ?: "Kalium-Call-Recorder/experimental",
            teamId = json.optionalString("teamId"),
            audioCbr = json.optionalBoolean("audioCbr") ?: false,
            avsReadyTimeoutSeconds = json.optionalLong("avsReadyTimeoutSeconds") ?: 30,
            stateDir = json.optionalString("stateDir") ?: "./call-recorder-state",
            cryptoDir = json.optionalString("cryptoDir") ?: "./call-recorder-crypto",
            mlsCiphersuite = json.optionalString("mlsCiphersuite"),
            accountsUrl = json.optionalString("accountsUrl"),
            blackListUrl = json.optionalString("blackListUrl"),
            teamsUrl = json.optionalString("teamsUrl"),
            websiteUrl = json.optionalString("websiteUrl"),
            userId = json.optionalString("userId"),
            userDomain = json.optionalString("userDomain"),
            clientId = json.optionalString("clientId"),
            stateKeyBase64 = json.optionalString("stateKeyBase64"),
            proteusPassphraseBase64 = json.optionalString("proteusPassphraseBase64"),
            mlsPassphraseBase64 = json.optionalString("mlsPassphraseBase64"),
        ).also {
            require(it.apiVersion >= 0) { "apiVersion must not be negative" }
            require(it.avsReadyTimeoutSeconds > 0) { "avsReadyTimeoutSeconds must be positive" }
        }
    }

    fun save(config: LocalTestingConfig) {
        path.toAbsolutePath().normalize().parent?.let(Files::createDirectories)
        Files.writeString(
            path,
            config.toJson(),
            StandardOpenOption.CREATE,
            StandardOpenOption.TRUNCATE_EXISTING,
            StandardOpenOption.WRITE,
        )
    }

    @Suppress("LongMethod")
    private fun LocalTestingConfig.toJson(): String = buildString {
        appendLine("{")
        appendJson("email", email)
        appendJson("password", password)
        appendJson("backendDomain", backendDomain)
        appendJson("apiUrl", apiUrl)
        appendJson("webSocketUrl", webSocketUrl)
        appendJson("selfConversations", selfConversations)
        appendLine("  \"apiVersion\": $apiVersion,")
        appendLine("  \"federation\": $federation,")
        appendLine("  \"onPremises\": $onPremises,")
        appendJson("verificationCode", verificationCode)
        appendJson("loginLabel", loginLabel)
        appendJson("clientLabel", clientLabel)
        appendJson("clientModel", clientModel)
        appendJson("userAgent", userAgent)
        appendJson("teamId", teamId)
        appendLine("  \"audioCbr\": $audioCbr,")
        appendLine("  \"avsReadyTimeoutSeconds\": $avsReadyTimeoutSeconds,")
        appendJson("stateDir", stateDir)
        appendJson("cryptoDir", cryptoDir)
        appendJson("mlsCiphersuite", mlsCiphersuite)
        appendJson("accountsUrl", accountsUrl)
        appendJson("blackListUrl", blackListUrl)
        appendJson("teamsUrl", teamsUrl)
        appendJson("websiteUrl", websiteUrl)
        appendJson("userId", userId)
        appendJson("userDomain", userDomain)
        appendJson("clientId", clientId)
        appendJson("stateKeyBase64", stateKeyBase64)
        appendJson("proteusPassphraseBase64", proteusPassphraseBase64)
        appendJson("mlsPassphraseBase64", mlsPassphraseBase64, trailingComma = false)
        appendLine("}")
    }

    private fun StringBuilder.appendJson(name: String, value: String?, trailingComma: Boolean = true) {
        append("  \"").append(name).append("\": ")
        if (value == null) append("null") else append('"').append(value.jsonEscape()).append('"')
        if (trailingComma) append(',')
        appendLine()
    }

    private fun String.requiredString(name: String): String = optionalString(name)
        ?.takeIf(String::isNotBlank)
        ?: error("Test config JSON is missing $name")

    private fun String.optionalString(name: String): String? =
        Regex("\\\"${Regex.escape(name)}\\\"\\s*:\\s*(null|\\\"((?:\\\\.|[^\\\"\\\\])*)\\\")")
            .find(this)
            ?.let { field -> field.groupValues[2].takeUnless { field.groupValues[1] == "null" }?.jsonUnescape() }

    private fun String.optionalBoolean(name: String): Boolean? = optionalPrimitive(name)?.let { value ->
        when (value) {
            "true" -> true
            "false" -> false
            else -> error("$name must be true or false")
        }
    }

    private fun String.optionalInt(name: String): Int? = optionalPrimitive(name)?.toIntOrNull()
        ?: if (containsField(name)) error("$name must be an integer") else null

    private fun String.optionalLong(name: String): Long? = optionalPrimitive(name)?.toLongOrNull()
        ?: if (containsField(name)) error("$name must be an integer") else null

    private fun String.optionalPrimitive(name: String): String? =
        Regex("\\\"${Regex.escape(name)}\\\"\\s*:\\s*([^,}\\r\\n]+)")
            .find(this)?.groupValues?.get(1)?.trim()?.takeUnless { it == "null" }

    private fun String.containsField(name: String): Boolean = Regex("\\\"${Regex.escape(name)}\\\"\\s*:").containsMatchIn(this)

    private fun String.jsonEscape(): String = buildString {
        this@jsonEscape.forEach { character ->
            when (character) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> append(character)
            }
        }
    }

    private fun String.jsonUnescape(): String = buildString {
        var index = 0
        while (index < this@jsonUnescape.length) {
            val character = this@jsonUnescape[index++]
            if (character != '\\' || index == this@jsonUnescape.length) {
                append(character)
            } else {
                append(
                    when (val escaped = this@jsonUnescape[index++]) {
                        '\\' -> '\\'
                        '"' -> '"'
                        'n' -> '\n'
                        'r' -> '\r'
                        't' -> '\t'
                        else -> error("Unsupported JSON escape: \\$escaped")
                    },
                )
            }
        }
    }
}
