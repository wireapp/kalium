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
package com.wire.kalium.logic.feature.call

import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.user.UserId
import kotlin.test.Test
import kotlin.test.assertEquals

class ShouldRemoteMuteCheckerTest {

    @Test
    fun givenNullTargets_whenChecking_thenReturnTrue() {
        val (_, checker) = Arrangement()
            .arrange()

        val shouldRemoteMute = checker.check(
            selfUserId = SELF_USER_ID,
            selfClientId = SELF_CLIENT_ID,
            targets = null
        )

        assertEquals(true, shouldRemoteMute)
    }

    @Test
    fun givenTargetContainsCurrentUser_whenChecking_thenReturnFalse() {
        val (_, checker) = Arrangement()
            .arrange()

        val shouldRemoteMute = checker.check(
            selfUserId = SELF_USER_ID,
            selfClientId = SELF_CLIENT_ID,
            targets = targetsWithoutCurrentUser
        )

        assertEquals(false, shouldRemoteMute)
    }

    @Test
    fun givenTargetDoesNotContainCurrentUser_whenChecking_thenReturnTrue() {
        val (_, checker) = Arrangement()
            .arrange()

        val shouldRemoteMute = checker.check(
            selfUserId = SELF_USER_ID,
            selfClientId = SELF_CLIENT_ID,
            targets = targetsWithCurrentUser
        )

        assertEquals(true, shouldRemoteMute)
    }

    @Test
    fun givenTargetThatDoesNotContainCurrentClientId_whenChecking_thenReturnFalse() {
        val (_, checker) = Arrangement()
            .arrange()

        val shouldRemoteMute = checker.check(
            selfUserId = SELF_USER_ID,
            selfClientId = SELF_CLIENT_ID,
            targets = targetsWithDifferentClientId
        )

        assertEquals(false, shouldRemoteMute)
    }

    internal class Arrangement {

        fun arrange() = this to ShouldRemoteMuteCheckerImpl()
    }

    companion object {
        private val SELF_USER_ID = UserId("selfUserId", "domain")
        private val OTHER_USER_ID = UserId("otherUserId", "domain")
        private const val SELF_CLIENT_ID = "selfClientId"
        private const val OTHER_CLIENT_ID = "otherClientId"
        val targetsWithCurrentUser = MessageContent.Calling.Targets(
            domainToUserIdToClients = mapOf(
                "anta-env" to mapOf(
                    OTHER_USER_ID.value to listOf(OTHER_CLIENT_ID, OTHER_CLIENT_ID)
                ),
                "diya-env" to mapOf(
                    SELF_USER_ID.value to listOf(OTHER_CLIENT_ID, SELF_CLIENT_ID, OTHER_CLIENT_ID),
                    OTHER_USER_ID.value to listOf(OTHER_CLIENT_ID, OTHER_CLIENT_ID)
                ),
                "elna-env" to mapOf(
                    OTHER_USER_ID.value to listOf(OTHER_CLIENT_ID, OTHER_CLIENT_ID)
                )
            )
        )
        val targetsWithoutCurrentUser = MessageContent.Calling.Targets(
            domainToUserIdToClients = mapOf(
                "anta-env" to mapOf(
                    OTHER_USER_ID.value to listOf(OTHER_CLIENT_ID, OTHER_CLIENT_ID)
                )
            )
        )

        val targetsWithDifferentClientId = MessageContent.Calling.Targets(
            domainToUserIdToClients = mapOf(
                "diya-env" to mapOf(
                    SELF_USER_ID.value to listOf(OTHER_CLIENT_ID, OTHER_CLIENT_ID),
                    OTHER_USER_ID.value to listOf(OTHER_CLIENT_ID, OTHER_CLIENT_ID)
                ),
            )
        )
    }
}
