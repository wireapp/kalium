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
import com.wire.kalium.monkeys.importer.UserData
import java.util.concurrent.ConcurrentHashMap

object MonkeyPool {
    // a map of monkeys per domain
    private val pool: ConcurrentHashMap<String, MutableList<Monkey>> = ConcurrentHashMap()

    // a map of logged in monkeys per domain
    private val poolLoggedIn: ConcurrentHashMap<String, ConcurrentHashMap<UserId, Monkey>> = ConcurrentHashMap()

    fun init(users: List<UserData>) {
        users.forEach {
            this.pool.getOrPut(it.backend.domain) { mutableListOf() }.add(Monkey(it))
        }
    }

    // TODO: ensure there's no duplicated results
    fun randomMonkeysFromDomain(domain: String, count: UInt): List<Monkey> {
        val backendUsers = this.pool[domain] ?: error("Domain $domain doesn't exist")
        return (1u..count).map { backendUsers.randomOrNull() ?: error("There are no monkeys for the $domain backend") }
    }

    /**
     * This is costly depending on the size. Use with caution
     */
    // TODO: ensure there's no duplicated results
    fun randomMonkeys(count: UInt): List<Monkey> {
        val allUsers = this.pool.values.flatten()
        return (1u..count).map { allUsers.randomOrNull() ?: error("The Monkey pool is empty") }
    }

    // TODO: ensure there's no duplicated results
    fun randomLoggedInMonkeysFromDomain(domain: String, count: UInt): List<Monkey> {
        val backendUsers = this.poolLoggedIn[domain]?.values ?: error("Domain $domain doesn't exist")
        return (1u..count).map { backendUsers.randomOrNull() ?: error("There are no logged in monkeys for the $domain backend") }
    }

    /**
     * This is costly depending on the size. Use with caution
     */
    // TODO: ensure there's no duplicated results
    fun randomLoggedInMonkeys(count: UInt): List<Monkey> {
        val allUsers = this.poolLoggedIn.values.flatMap { it.values }
        return (1u..count).map { allUsers.randomOrNull() ?: error("The Monkey pool of logged users is empty") }
    }

    fun loggedIn(monkey: Monkey) {
        this.poolLoggedIn.getOrPut(monkey.user.backend.domain) { ConcurrentHashMap() }[monkey.user.userId] = monkey
    }

    fun loggedOut(monkey: Monkey) {
        this.poolLoggedIn[monkey.user.backend.domain]?.remove(monkey.user.userId)
    }
}
