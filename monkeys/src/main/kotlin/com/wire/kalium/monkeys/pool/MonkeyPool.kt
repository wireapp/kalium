/*
 * Wire
 * Copyright (C) 2023 Wire Swiss GmbH
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
package com.wire.kalium.monkeys.pool

import com.wire.kalium.logic.CoreLogic
import com.wire.kalium.logic.data.conversation.ConversationDetails
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.monkeys.MetricsCollector
import com.wire.kalium.monkeys.conversation.Monkey
import com.wire.kalium.monkeys.importer.Team
import com.wire.kalium.monkeys.importer.UserCount
import com.wire.kalium.monkeys.importer.UserData
import io.micrometer.core.instrument.Tag
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.roundToInt

class MonkeyPool(users: List<UserData>, testCase: String) {
    // all the teams by name
    private val teams: ConcurrentHashMap<String, Team> = ConcurrentHashMap()

    // a map of monkeys per team
    private val pool: ConcurrentHashMap<String, MutableList<Monkey>> = ConcurrentHashMap()

    // a map of monkeys per UserId
    private val poolById: ConcurrentHashMap<UserId, Monkey> = ConcurrentHashMap()

    // a map of logged in monkeys per domain
    private val poolLoggedIn: ConcurrentHashMap<String, ConcurrentHashMap<UserId, Monkey>> = ConcurrentHashMap()

    // a map of logged out monkeys per domain
    private val poolLoggedOut: ConcurrentHashMap<String, ConcurrentHashMap<UserId, Monkey>> = ConcurrentHashMap()

    init {
        users.forEach {
            val monkey = Monkey.internal(it)
            this.pool.getOrPut(it.team.name) { mutableListOf() }.add(monkey)
            this.poolLoggedOut.getOrPut(it.team.name) { ConcurrentHashMap() }[it.userId] = monkey
            this.poolById[it.userId] = monkey
            this.teams.putIfAbsent(it.team.name, it.team)
        }
        this.poolLoggedOut.forEach { (domain, usersById) ->
            MetricsCollector.gaugeMap(
                "g_loggedOutUsers", listOf(Tag.of("domain", domain), Tag.of("testCase", testCase)), usersById
            )
            // init so we can have metrics for it
            this.poolLoggedIn[domain] = ConcurrentHashMap()
        }
        this.poolLoggedIn.forEach { (domain, usersById) ->
            MetricsCollector.gaugeMap(
                "g_loggedInUsers", listOf(Tag.of("domain", domain), Tag.of("testCase", testCase)), usersById
            )
        }
    }

    suspend fun warmUp(core: CoreLogic) = coroutineScope {
        // this is needed to create key packages for clients at least once
        poolById.values.map {
            async {
                it.login(core) {}
                it.logout {}
            }
        }.awaitAll()
    }

    fun randomMonkeysFromTeam(team: String, userCount: UserCount): List<Monkey> {
        val count = resolveUserCount(userCount)
        val backendUsers = this.pool[team]?.shuffled() ?: error("Team $team doesn't exist or there are no monkeys in the team")
        return backendUsers.take(count.toInt())
    }

    /**
     * This is costly depending on the size. Use with caution
     */
    fun randomMonkeys(userCount: UserCount): List<Monkey> {
        val count = resolveUserCount(userCount)
        val allUsers = this.pool.values.flatten().shuffled()
        return allUsers.take(count.toInt())
    }

    fun randomLoggedInMonkeysFromTeam(team: String, userCount: UserCount): List<Monkey> {
        val count = resolveUserCount(userCount, team)
        val backendUsers =
            this.poolLoggedIn[team]?.values?.shuffled() ?: error("Domain $team doesn't exist or there are not monkeys logged in")
        return backendUsers.take(count.toInt())
    }

    suspend fun randomMonkeysWithConnectionRequests(userCount: UserCount): Map<Monkey, List<ConversationDetails.Connection>> {
        val monkeysWithPendingRequests = this.poolLoggedIn.values.flatMap { idToMonkey ->
            idToMonkey.values.map { it to it.pendingConnectionRequests() }.filter { it.second.isNotEmpty() }
        }.shuffled()
        val count = resolveUserCount(userCount, monkeysWithPendingRequests.count().toUInt())
        return monkeysWithPendingRequests.take(count.toInt()).toMap()
    }

    /**
     * This is costly depending on the size. Use with caution
     */
    fun randomLoggedInMonkeys(userCount: UserCount): List<Monkey> {
        val count = resolveUserCount(userCount)
        val allUsers = this.poolLoggedIn.values.flatMap { it.values }.shuffled()
        return allUsers.take(count.toInt())
    }

    /**
     * This is costly depending on the size. Use with caution
     */
    fun randomLoggedOutMonkeys(userCount: UserCount): List<Monkey> {
        val count = resolveUserCount(userCount)
        val allUsers = this.poolLoggedOut.values.flatMap { it.values }.shuffled()
        return allUsers.take(count.toInt())
    }

    suspend fun externalUsersFromTeam(teamName: String): List<Monkey> {
        val team = this.teams[teamName] ?: error("Backend $teamName not found")
        val monkeysFromTeam = this.pool[teamName] ?: error("Monkeys from $teamName not found")
        return team.usersFromTeam().filter { u -> monkeysFromTeam.none { u == it.monkeyType.userId() } }.map(Monkey::external)
    }

    fun loggedIn(monkey: Monkey) {
        this.poolLoggedIn.getOrPut(monkey.monkeyType.userData().team.name) { ConcurrentHashMap() }[monkey.monkeyType.userData().userId] =
            monkey
        this.poolLoggedOut[monkey.monkeyType.userData().team.name]?.remove(monkey.monkeyType.userData().userId)
    }

    fun loggedOut(monkey: Monkey) {
        this.poolLoggedIn[monkey.monkeyType.userData().team.name]?.remove(monkey.monkeyType.userData().userId)
        this.poolLoggedOut.getOrPut(monkey.monkeyType.userData().team.name) { ConcurrentHashMap() }[monkey.monkeyType.userData().userId] =
            monkey
    }

    private fun resolveUserCount(userCount: UserCount): UInt {
        val totalUsers: UInt = this.pool.values.sumOf { it.count() }.toUInt()
        return resolveUserCount(userCount, totalUsers)
    }

    private fun resolveUserCount(userCount: UserCount, team: String): UInt {
        val totalUsers: UInt = this.pool[team]?.count()?.toUInt() ?: error("Domain $team not found")
        return resolveUserCount(userCount, totalUsers)
    }

    fun get(userId: UserId): Monkey {
        return this.poolById[userId] ?: Monkey.external(userId)
    }
}

const val HUNDRED_PERCENT: Float = 100f

fun resolveUserCount(userCount: UserCount, totalUsers: UInt): UInt {
    return when (userCount) {
        is UserCount.Percentage -> ((userCount.value.toFloat() / HUNDRED_PERCENT) * totalUsers.toFloat()).roundToInt().toUInt()
        is UserCount.FixedCount -> userCount.value
    }
}
