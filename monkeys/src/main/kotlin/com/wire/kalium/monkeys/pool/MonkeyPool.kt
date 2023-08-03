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

import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.monkeys.conversation.Monkey
import com.wire.kalium.monkeys.importer.UserCount
import com.wire.kalium.monkeys.importer.UserData
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.roundToInt

object MonkeyPool {
    // a map of monkeys per domain
    private val pool: ConcurrentHashMap<String, MutableList<Monkey>> = ConcurrentHashMap()

    // a map of monkeys per UserId
    private val poolById: ConcurrentHashMap<UserId, Monkey> = ConcurrentHashMap()

    // a map of logged in monkeys per domain
    private val poolLoggedIn: ConcurrentHashMap<String, ConcurrentHashMap<UserId, Monkey>> = ConcurrentHashMap()

    // a map of logged out monkeys per domain
    private val poolLoggedOut: ConcurrentHashMap<String, ConcurrentHashMap<UserId, Monkey>> = ConcurrentHashMap()

    fun init(users: List<UserData>, userIdsPerBackend: Map<String, List<UserId>>) {
        users.forEach {
            val monkey = Monkey(it, userIdsPerBackend[it.backend.domain] ?: error("Missing user ids for the domain ${it.userId.value}"))
            this.pool.getOrPut(it.backend.domain) { mutableListOf() }.add(monkey)
            this.poolLoggedOut.getOrPut(it.backend.domain) { ConcurrentHashMap() }[it.userId] = monkey
            this.poolById[it.userId] = monkey
        }
    }

    fun randomMonkeysFromDomain(domain: String, userCount: UserCount): List<Monkey> {
        val count = resolveUserCount(userCount)
        val backendUsers = this.pool[domain]?.shuffled() ?: error("Domain $domain doesn't exist or there are no monkeys in the domain")
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

    fun randomLoggedInMonkeysFromDomain(domain: String, userCount: UserCount): List<Monkey> {
        val count = resolveUserCount(userCount, domain)
        val backendUsers =
            this.poolLoggedIn[domain]?.values?.shuffled() ?: error("Domain $domain doesn't exist or there are not monkeys logged in")
        return backendUsers.take(count.toInt())
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

    fun loggedIn(monkey: Monkey) {
        this.poolLoggedIn.getOrPut(monkey.user.backend.domain) { ConcurrentHashMap() }[monkey.user.userId] = monkey
        this.poolLoggedOut[monkey.user.backend.domain]?.remove(monkey.user.userId)
    }

    fun loggedOut(monkey: Monkey) {
        this.poolLoggedIn[monkey.user.backend.domain]?.remove(monkey.user.userId)
        this.poolLoggedOut.getOrPut(monkey.user.backend.domain) { ConcurrentHashMap() }[monkey.user.userId] = monkey
    }

    private fun resolveUserCount(userCount: UserCount): UInt {
        val totalUsers: UInt = this.pool.values.sumOf { it.count() }.toUInt()
        return resolveUserCount(userCount, totalUsers)
    }

    private fun resolveUserCount(userCount: UserCount, domain: String): UInt {
        val totalUsers: UInt = this.pool[domain]?.count()?.toUInt() ?: error("Domain $domain not found")
        return resolveUserCount(userCount, totalUsers)
    }

    fun get(userId: UserId): Monkey {
        return this.poolById[userId] ?: error("Monkey with id $userId not found.")
    }
}

const val HUNDRED_PERCENT: Float = 100f

fun resolveUserCount(userCount: UserCount, totalUsers: UInt): UInt {
    return when (userCount) {
        is UserCount.Percentage -> ((userCount.value.toFloat() / HUNDRED_PERCENT) * totalUsers.toFloat()).roundToInt().toUInt()
        is UserCount.FixedCount -> userCount.value
    }
}
