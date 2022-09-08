package com.wire.kalium.network.utils

import com.wire.kalium.logger.obfuscateId
import com.wire.kalium.logger.obfuscateUrlPath
import com.wire.kalium.network.kaliumLogger
import io.ktor.http.Url
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject


@Suppress("TooGenericExceptionCaught")
fun obfuscateAndLogMessage(text: String) {
    try {
        val obj = (Json.decodeFromString(text) as JsonElement)
        if (obj.jsonArray.size > 0) {
            obj.jsonArray.map {
                logObfuscatedJsonElement(it)
            }
        } else {
            logObfuscatedJsonElement(obj)
        }
        obj.toString()
    } catch (e: Exception) {
        "error the body content while logging "
    }
}

fun logObfuscatedJsonElement(obj: JsonElement) {
    obj.jsonObject.entries.toMutableSet().map {
        when {
            sensitiveJsonKeys.contains(it.key.lowercase()) -> {
                kaliumLogger.v("${it.key} : ******")
            }

            sensitiveJsonIdKeys.contains(it.key.lowercase()) -> {
                kaliumLogger.v("${it.key} : ${it.value.toString().obfuscateId()}")
            }

            sensitiveJsonObjects.contains(it.key.lowercase()) -> {
                logObfuscatedJsonElement(it.value)
            }

            else -> {
                kaliumLogger.v("${it.key} : ${it.value}")
            }
        }
    }
}

fun obfuscatePath(url: Url): String {
    var requestToLog = url.host
    if (url.pathSegments.isNotEmpty()) {
        url.pathSegments.map {
            requestToLog += "/${it.obfuscateUrlPath()}"
        }
    }
    url.parameters.entries().map {
        if (it.value.isNotEmpty()) {
            requestToLog += "/${it.key} = ${it.value[0].obfuscateUrlPath()}"
        }
    }
    return requestToLog
}

fun deleteSensitiveItemsFromJson(text: String) {
    try {
        val obj = (Json.decodeFromString(text) as JsonElement)
        if (obj.jsonArray.size > 0) {
            obj.jsonArray.map {
                itemsToKeep(it)
            }
        } else {
            itemsToKeep(obj)
        }
        obj.toString()
    } catch (e: Exception) {
        "error the body content while logging "
    }

}

fun itemsToKeep(obj: JsonElement) {
    obj.jsonObject.entries.toMutableSet().map {
        when {
            notSensitiveJsonKeys.contains(it.key.lowercase()) -> {
                kaliumLogger.v("${it.key} : ${it.value}")
            }
        }
    }
}


 val sensitiveJsonKeys by lazy { listOf("password", "authorization", "set-cookie", "Location", "x-amz-meta-user") }
private val sensitiveJsonIdKeys by lazy { listOf("conversation", "id", "user", "team") }
private val sensitiveJsonObjects by lazy { listOf("qualified_id") }
private val notSensitiveJsonKeys by lazy { listOf("type", "time") }
