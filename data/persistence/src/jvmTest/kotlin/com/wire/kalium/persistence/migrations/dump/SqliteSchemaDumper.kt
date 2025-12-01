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
package com.wire.kalium.persistence.migrations.dump

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.sql.DriverManager

class SqliteSchemaDumper(
    private val dbPath: String,
    private val outputPath: String,
) {

    fun dumpToJson(): Path {
        val schema = SchemaDump(
            table = extractComponent(SqlObjectType.TABLE),
            view = extractComponent(SqlObjectType.VIEW),
            index = extractComponent(SqlObjectType.INDEX),
            trigger = extractComponent(SqlObjectType.TRIGGER)
        )

        val jsonText = json.encodeToString(schema)

        val path = Paths.get(outputPath)
        Files.createDirectories(path.parent)
        Files.writeString(path, jsonText)

        println("Dumped schema: $dbPath → $outputPath")
        return path
    }

    private fun extractComponent(component: SqlObjectType): Map<String, String> {
        val result = mutableMapOf<String, String>()

        DriverManager.getConnection("jdbc:sqlite:$dbPath").use { conn ->
            conn.createStatement().use { stmt ->
                val rs = stmt.executeQuery(
                    "SELECT name, sql FROM sqlite_master WHERE type = '${component.type}' AND sql IS NOT NULL"
                )
                while (rs.next()) {
                    val name = rs.getString("name") ?: continue
                    val sql = rs.getString("sql") ?: continue
                    result[SqlNormalizer.normalize(name)] = SqlNormalizer.normalize(sql)
                }
            }
        }
        return result.toSortedMap()
    }

    companion object {
        val json = Json {
            encodeDefaults = true
            prettyPrint = true
        }
    }
}

@Serializable
data class SchemaDump(
    val table: Map<String, String> = emptyMap(),
    val view: Map<String, String> = emptyMap(),
    val index: Map<String, String> = emptyMap(),
    val trigger: Map<String, String> = emptyMap()
)
