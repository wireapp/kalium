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

    fun init(users: List<UserData>) {
        users.forEach {
            val monkey = Monkey(it)
            this.pool.getOrPut(it.backend.domain) { mutableListOf() }.add(monkey)
            this.poolLoggedOut.getOrPut(it.backend.domain) { ConcurrentHashMap() }[it.userId] = monkey
            this.poolById[it.userId] = monkey
        }
    }

    // TODO: ensure there's no duplicated results
    fun randomMonkeysFromDomain(domain: String, userCount: UserCount): List<Monkey> {
        val count = resolveUserCount(userCount)
        val backendUsers = this.pool[domain] ?: error("Domain $domain doesn't exist")
        return (1u..count).map { backendUsers.randomOrNull() ?: error("There are no monkeys for the $domain backend") }
    }

    /**
     * This is costly depending on the size. Use with caution
     */
    // TODO: ensure there's no duplicated results
    fun randomMonkeys(userCount: UserCount): List<Monkey> {
        val count = resolveUserCount(userCount)
        val allUsers = this.pool.values.flatten()
        return (1u..count).map { allUsers.randomOrNull() ?: error("The Monkey pool is empty") }
    }

    // TODO: ensure there's no duplicated results
    fun randomLoggedInMonkeysFromDomain(domain: String, userCount: UserCount): List<Monkey> {
        val count = resolveUserCount(userCount)
        val backendUsers = this.poolLoggedIn[domain]?.values ?: error("Domain $domain doesn't exist")
        return (1u..count).map { backendUsers.randomOrNull() ?: error("There are no logged in monkeys for the $domain backend") }
    }

    /**
     * This is costly depending on the size. Use with caution
     */
    // TODO: ensure there's no duplicated results
    fun randomLoggedInMonkeys(userCount: UserCount): List<Monkey> {
        val count = resolveUserCount(userCount)
        val allUsers = this.poolLoggedIn.values.flatMap { it.values }
        return (1u..count).map { allUsers.randomOrNull() ?: error("The Monkey pool of logged in users is empty") }
    }

    // TODO: ensure there's no duplicated results
    fun randomLoggedOutMonkeysFromDomain(domain: String, userCount: UserCount): List<Monkey> {
        val count = resolveUserCount(userCount)
        val backendUsers = this.poolLoggedOut[domain]?.values ?: error("Domain $domain doesn't exist")
        return (1u..count).map { backendUsers.randomOrNull() ?: error("There are no logged out monkeys for the $domain backend") }
    }

    /**
     * This is costly depending on the size. Use with caution
     */
    // TODO: ensure there's no duplicated results
    fun randomLoggedOutMonkeys(userCount: UserCount): List<Monkey> {
        val count = resolveUserCount(userCount)
        val allUsers = this.poolLoggedOut.values.flatMap { it.values }
        return (1u..count).map { allUsers.randomOrNull() ?: error("The Monkey pool of logged out users is empty") }
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

    fun get(userId: UserId): Monkey {
        return this.poolById[userId] ?: error("Monkey with id $userId not found.")
    }
}

fun resolveUserCount(userCount: UserCount, totalUsers: UInt): UInt {
    return when (userCount) {
        is UserCount.Percentage -> ((userCount.value.toFloat() / 100f) * totalUsers.toFloat()).roundToInt().toUInt()
        is UserCount.FixedCount -> userCount.value
    }
}
