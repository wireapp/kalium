package com.wire.kalium.monkeys.actions

import com.wire.kalium.logic.CoreLogic
import com.wire.kalium.monkeys.conversation.Monkey
import com.wire.kalium.monkeys.importer.ActionType
import com.wire.kalium.monkeys.importer.UserCount
import com.wire.kalium.monkeys.pool.MonkeyPool
import io.mockk.coVerify
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test

class SendRequestActionTest {

    @Test
    fun givenAcceptRequestConfig_shouldAcceptRequest() = runTest {
        val config = ActionType.SendRequest(UserCount.single(), UserCount.single(), "wire.com", "wearezeta.com", 0u)
        val monkeyPool = mockk<MonkeyPool>()
        val monkeyOrigin = mockk<Monkey>(relaxed = true)
        val monkeyTarget = mockk<Monkey>(relaxed = true)
        val coreLogic = mockk<CoreLogic>()
        every { monkeyPool.randomLoggedInMonkeysFromTeam(config.originTeam, UserCount.single()) } returns listOf(monkeyOrigin)
        every { monkeyPool.randomLoggedInMonkeysFromTeam(config.targetTeam, UserCount.single()) } returns listOf(monkeyTarget)
        SendRequestAction(config).execute(coreLogic, monkeyPool)
        coVerify(exactly = 1) { monkeyOrigin.sendRequest(monkeyTarget) }
        coVerify(exactly = 1) { monkeyTarget.acceptRequest(monkeyOrigin) }
        confirmVerified(monkeyOrigin)
        confirmVerified(monkeyTarget)
    }

    @Test
    fun givenRejectRequestConfig_shouldRejectRequest() = runTest {
        val config = ActionType.SendRequest(UserCount.single(), UserCount.single(), "wire.com", "wearezeta.com", 0u, false)
        val monkeyPool = mockk<MonkeyPool>()
        val monkeyOrigin = mockk<Monkey>(relaxed = true)
        val monkeyTarget = mockk<Monkey>(relaxed = true)
        val coreLogic = mockk<CoreLogic>()
        every { monkeyPool.randomLoggedInMonkeysFromTeam(config.originTeam, UserCount.single()) } returns listOf(monkeyOrigin)
        every { monkeyPool.randomLoggedInMonkeysFromTeam(config.targetTeam, UserCount.single()) } returns listOf(monkeyTarget)
        SendRequestAction(config).execute(coreLogic, monkeyPool)
        coVerify(exactly = 1) { monkeyOrigin.sendRequest(monkeyTarget) }
        coVerify(exactly = 1) { monkeyTarget.rejectRequest(monkeyOrigin) }
        confirmVerified(monkeyOrigin)
        confirmVerified(monkeyTarget)
    }
}
