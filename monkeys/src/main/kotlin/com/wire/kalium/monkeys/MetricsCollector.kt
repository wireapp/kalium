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
package com.wire.kalium.monkeys

import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.metrics.micrometer.MicrometerMetrics
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.micrometer.core.instrument.DistributionSummary
import io.micrometer.core.instrument.Tag
import io.micrometer.core.instrument.Timer
import io.micrometer.prometheus.PrometheusConfig
import io.micrometer.prometheus.PrometheusMeterRegistry
import kotlin.time.measureTimedValue
import kotlin.time.toJavaDuration

object MetricsCollector {
    private val registry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)

    fun Application.configureMicrometer(route: String) {
        install(MicrometerMetrics) {
            registry = registry
        }
        routing {
            get(route) {
                call.respond(registry.scrape())
            }
        }
    }

    fun count(key: String, tags: List<Tag>) {
        this.registry.counter(key, tags).increment()
    }

    @Suppress("MagicNumber")
    suspend fun <T> time(key: String, tags: List<Tag>, func: suspend () -> T): T {
        val timer: Timer = Timer
            .builder(key)
            .tags(tags)
            .publishPercentiles(0.3, 0.5, 0.95)
            .publishPercentileHistogram()
            .register(this.registry)
        val (result, duration) = measureTimedValue { func() }
        timer.record(duration.toJavaDuration())
        return result
    }

    fun <T> gaugeCollection(key: String, tags: List<Tag>, collection: Collection<T>) {
        this.registry.gaugeCollectionSize(key, tags, collection)
    }

    fun <K, V> gaugeMap(key: String, tags: List<Tag>, collection: Map<K, V>) {
        this.registry.gaugeMapSize(key, tags, collection)
    }

    @Suppress("MagicNumber")
    fun distribution(key: String, tags: List<Tag>, amount: Double) {
        val distribution = DistributionSummary
            .builder(key)
            .tags(tags)
            .publishPercentiles(0.3, 0.5, 0.95)
            .publishPercentileHistogram()
            .register(this.registry)
        distribution.record(amount)
    }
}
