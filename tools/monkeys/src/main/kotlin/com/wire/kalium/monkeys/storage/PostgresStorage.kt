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
package com.wire.kalium.monkeys.storage

import app.cash.sqldelight.ColumnAdapter
import app.cash.sqldelight.async.coroutines.awaitAsList
import app.cash.sqldelight.async.coroutines.awaitAsOne
import app.cash.sqldelight.async.coroutines.awaitCreate
import app.cash.sqldelight.driver.r2dbc.R2dbcDriver
import com.wire.kalium.monkeys.db.Execution
import com.wire.kalium.monkeys.db.InfiniteMonkeysDB
import com.wire.kalium.monkeys.db.Team
import com.wire.kalium.monkeys.model.BackendConfig
import com.wire.kalium.monkeys.model.Event
import com.wire.kalium.monkeys.model.EventType
import com.wire.kalium.monkeys.model.MonkeyId
import io.r2dbc.spi.ConnectionFactories
import io.r2dbc.spi.ConnectionFactoryOptions
import io.r2dbc.spi.ConnectionFactoryOptions.DATABASE
import io.r2dbc.spi.ConnectionFactoryOptions.DRIVER
import io.r2dbc.spi.ConnectionFactoryOptions.HOST
import io.r2dbc.spi.ConnectionFactoryOptions.PASSWORD
import io.r2dbc.spi.ConnectionFactoryOptions.PORT
import io.r2dbc.spi.ConnectionFactoryOptions.USER
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.produce
import kotlinx.coroutines.reactive.awaitSingle
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import reactor.core.publisher.Mono
import com.wire.kalium.monkeys.db.Event as EventDB
import com.wire.kalium.monkeys.model.EventStorage.PostgresStorage as EventConfig

private const val PG_PORT = 5432

class PostgresStorage(pgConfig: EventConfig, private val executionId: Int? = null) : EventStorage() {
    private var execution: Execution? = null
    private val connectionFactory = ConnectionFactories.get(
        ConnectionFactoryOptions.builder().option(DRIVER, "postgresql").option(HOST, pgConfig.host).option(PORT, PG_PORT)
            .option(USER, pgConfig.username).option(PASSWORD, pgConfig.password).option(DATABASE, pgConfig.dbName).build()
    )
    private val teamAdapter = object : ColumnAdapter<BackendConfig, String> {
        override fun decode(databaseValue: String): BackendConfig {
            return Json.decodeFromString(databaseValue)
        }

        override fun encode(value: BackendConfig): String {
            return Json.encodeToString(value)
        }
    }
    private val eventTypeAdapter = object : ColumnAdapter<EventType, String> {
        override fun decode(databaseValue: String): EventType {
            return Json.decodeFromString(databaseValue)
        }

        override fun encode(value: EventType): String {
            return Json.encodeToString(value)
        }
    }

    override suspend fun store(event: Event) {
        withDatabase { database, execution ->
            database.executionEventQueries.insertEvent(
                execution_id = execution.id,
                monkey_index = event.monkeyOrigin.index,
                team = event.monkeyOrigin.team,
                client_id = event.monkeyOrigin.clientId,
                event_data = event.eventType
            )
        }
    }

    override suspend fun storeBackends(backends: List<BackendConfig>) {
        withDatabase { database, execution ->
            backends.forEach {
                val count =
                    if (it.presetTeam != null && it.presetTeam.users.isNotEmpty()) it.presetTeam.users.count() else it.userCount.toInt()
                database.teamQueries.insertTeam(it.teamName, execution.id, count, it)
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun CoroutineScope.readEvents() = produce {
        withDatabase { database, execution ->
            for (event in database.executionEventQueries.selectByExecutionId(execution.id).awaitAsList()) {
                send(Event(MonkeyId(event.monkey_index, event.team, event.client_id), Json.decodeFromString(event.event_data)))
            }
        }
    }

    override suspend fun readTeamConfig(): List<BackendConfig> {
        return withDatabase { database, execution ->
            database.teamQueries.selectTeams(execution.id).awaitAsList().map { Json.decodeFromString(it.backend_config) }
        }
    }

    override suspend fun releaseResources() {
        withDatabase { database, execution -> database.executionQueries.finishExecution(execution.id) }
    }

    private suspend fun <T> withDatabase(func: suspend (InfiniteMonkeysDB, Execution) -> T): T {
        val mono = Mono.from(this.connectionFactory.create())
        val driver = mono.map { R2dbcDriver(it) }.awaitSingle()
        InfiniteMonkeysDB.Schema.awaitCreate(driver)
        val database = InfiniteMonkeysDB(
            driver = driver,
            EventAdapter = EventDB.Adapter(event_dataAdapter = eventTypeAdapter),
            TeamAdapter = Team.Adapter(backend_configAdapter = teamAdapter)
        )
        if (this.execution == null) {
            this.execution = if (this.executionId == null) {
                database.executionQueries.insertExecution().awaitAsOne()
            } else {
                database.executionQueries.selectExecution(this.executionId).awaitAsOne()
            }
        }
        return func(database, this.execution!!)
    }
}
