/*
 * Wire
 * Copyright (C) 2025 Wire Swiss GmbH
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
package com.wire.backup.verification

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.reflect.KClass
import kotlin.reflect.full.declaredMembers
import kotlin.reflect.full.instanceParameter

@Composable
fun compareFieldsRecursively(clazz: KClass<*>, itemsBySource: Map<BackupId, Any>, level: Int) {
    val fields = clazz.declaredMembers
        .filter { it.name != "id" }
        .filter { field ->
            field.parameters.size == 1 &&
                    !field.name.startsWith("hashCode") &&
                    !field.name.startsWith("toString") &&
                    !field.name.startsWith("get") &&
                    !field.name.startsWith("component") &&
                    !field.name.startsWith("copy") &&
                    field.returnType.toString() != "kotlin.Unit"
        }

    fields.forEach { field ->
        val fieldValues = itemsBySource.mapNotNull { (source, msg) ->
            try {
                // Check if the object is an instance of the declaring class before calling
                if (field.instanceParameter?.type?.classifier == msg::class) {
                    val value = field.call(msg)
                    source to value
                } else {
                    // Try to find a similar field in the actual class of the object
                    val actualClass = msg::class
                    val actualField = actualClass.declaredMembers.find { it.name == field.name }
                    if (actualField != null && actualField.parameters.size == 1) {
                        val value = actualField.call(msg)

                        source to value
                    } else {
                        null
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                // Skip fields that can't be accessed
                null
            }
        }

        if (fieldValues.isEmpty()) {
            return@forEach
        }

        Text(
            "${field.name}:",
            fontSize = when (level) {
                0 -> 18.sp
                1 -> 16.sp
                else -> 14.sp
            },
            modifier = Modifier.padding(start = (level * 16).dp, top = 8.dp)
        )

        val firstValue = fieldValues.firstOrNull()?.second
        if (firstValue != null && firstValue::class.isData) {
            val nonNullValues = fieldValues.filter { it.second != null }
            if (nonNullValues.isNotEmpty()) {
                compareFieldsRecursively(
                    firstValue::class,
                    nonNullValues.associate { it.first to it.second!! },
                    level + 1
                )
            }
        } else {
            val stringValues = fieldValues.map { (first, second) ->
                val stringValue = when (second) {
                    is ByteArray -> second.joinToString("") { "%02x".format(it) }
                    else -> second?.toString()
                } ?: "null"
                first to stringValue
            }
            val allSameValue = stringValues.all { it.second == stringValues.first().second }

            if (allSameValue) {
                Text(
                    stringValues.first().second,
                    fontSize = when (level) {
                        0 -> 18.sp
                        1 -> 16.sp
                        else -> 14.sp
                    },
                    modifier = Modifier.padding(start = ((level + 1) * 16).dp)
                )
            } else {
                stringValues.forEach { (source, value) ->
                    Text(
                        "${source.id}: $value",
                        fontSize = when (level) {
                            0 -> 18.sp
                            1 -> 16.sp
                            else -> 14.sp
                        },
                        modifier = Modifier
                            .padding(start = ((level + 1) * 16).dp)
                            .background(
                                color = Color(
                                    red = 1f,
                                    green = 0.9f,
                                    blue = 0.9f,
                                    alpha = 0.8f
                                )
                            )
                    )
                }
            }
        }
    }
}
