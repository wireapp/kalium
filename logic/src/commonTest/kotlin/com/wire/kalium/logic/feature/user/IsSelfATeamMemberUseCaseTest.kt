package com.wire.kalium.logic.feature.user

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.id.TeamId
import com.wire.kalium.logic.feature.SelfTeamIdProvider
import com.wire.kalium.logic.functional.Either
import io.mockative.Mock
import io.mockative.given
import io.mockative.mock
import io.mockative.once
import io.mockative.verify
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class IsSelfATeamMemberUseCaseTest {

    @Test
    fun givenSelfIsTeamMember_thenReturnTrue() = runTest {
        val (arrangement, isSelfTeamMember) = Arrangement()
            .withSelfTeamId(Either.Right(TeamId("gg")))
            .arrange()

        isSelfTeamMember().also { actual ->
            assertTrue(actual)
        }

        verify(arrangement.selfTeamIdProvider)
            .suspendFunction(arrangement.selfTeamIdProvider::invoke)
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenSelfIsNotTeamMember_thenReturnTrue() = runTest {
        val (arrangement, isSelfTeamMember) = Arrangement()
            .withSelfTeamId(Either.Right(null))
            .arrange()

        isSelfTeamMember().also { actual ->
            assertFalse(actual)
        }

        verify(arrangement.selfTeamIdProvider)
            .suspendFunction(arrangement.selfTeamIdProvider::invoke)
            .wasInvoked(exactly = once)
    }

    private class Arrangement {
        @Mock
        val selfTeamIdProvider: SelfTeamIdProvider = mock(SelfTeamIdProvider::class)

        private val isSelfATeamMember: IsSelfATeamMemberUseCaseImpl = IsSelfATeamMemberUseCaseImpl(selfTeamIdProvider)
        suspend fun withSelfTeamId(result: Either<CoreFailure, TeamId?>) = apply {
            given(selfTeamIdProvider).coroutine { invoke() }.then { result }
        }

        fun arrange() = this to isSelfATeamMember
    }
}
