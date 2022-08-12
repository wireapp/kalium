package com.wire.kalium.logic.feature.conversation

import kotlinx.coroutines.test.runTest

class ClearConversationContentUseCaseTest {

    @Test
    fun givenConversationHavingAssetMessagesAndTextMessages_whenInvoking_thenThoseAssetsWithMessagesAreRemoved() = runTest {

    }

    @Test
    fun givenConversationHasNoAssetsButOnlyTextMessages_whenInvoking_thenNoAssetsAreRemoved() = runTest {

    }

    @Test
    fun givenGetingASsetsMessagesFails_whenInvoking_thenFailureIsCorrectlyPropagated() = runTest {

    }

    @Test
    fun givenDeletingAssetFails_whenInvoking_thenFailureIsCorrectlyPropagated() = runTest {

    }

    @Test
    fun givendeleteingAllMessagesFails_whenInvoking_thenFailureIsCorrectlyPropagated() = runTest
    {

    }

}
