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

class LoginActionTest {

    @Test
    fun givenLoginConfigProvided_thenShouldLogin() = runTest {
        val monkeyPool = mockk<MonkeyPool>()
        val monkey = mockk<Monkey>(relaxed = true)
        val coreLogic = mockk<CoreLogic>()
        every { monkeyPool.randomLoggedOutMonkeys(UserCount.single()) } returns listOf(monkey)
        LoginAction(ActionType.Login(UserCount.single())).execute(coreLogic, monkeyPool)
        coVerify(exactly = 1) { monkey.login(coreLogic, monkeyPool::loggedIn) }
        coVerify(exactly = 0) { monkey.logout(monkeyPool::loggedOut) }
        confirmVerified(monkey)
    }

    @Test
    fun givenLoginConfigWithDurationProvided_thenShouldLoginAndLogout() = runTest {
        val monkeyPool = mockk<MonkeyPool>()
        val monkey = mockk<Monkey>(relaxed = true)
        val coreLogic = mockk<CoreLogic>()
        every { monkeyPool.randomLoggedOutMonkeys(UserCount.single()) } returns listOf(monkey)
        LoginAction(ActionType.Login(UserCount.single(), 10u)).execute(coreLogic, monkeyPool)
        coVerify(exactly = 1) { monkey.login(coreLogic, monkeyPool::loggedIn) }
        coVerify(exactly = 1) { monkey.logout(monkeyPool::loggedOut) }
        confirmVerified(monkey)
    }
}
