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

import com.wire.kalium.logic.featureFlags.KaliumConfigs
import com.wire.kalium.network.api.base.unbound.acme.AcmeDirectoriesResponse
import com.wire.kalium.network.utils.isSuccessful
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TempTest {
    @Test
    fun testFun() = runTest {
        val coreLogic = coreLogic(
            rootPath = "$HOME_DIRECTORY/.kalium/accounts",
            kaliumConfigs = KaliumConfigs(
                developmentApiEnabled = true,
                encryptProteusStorage = true,
                isMLSSupportEnabled = true,
                wipeOnDeviceRemoval = true,
            )
        )

        launch {
            val expectedResult = AcmeDirectoriesResponse(
                newNonce = "nonce",
                newAccount = "newAccount",
                newOrder = "newOrder",
                revokeCert = "revokeCert",
                keyChange = "keyChange"
            )

            val result = coreLogic
                .getGlobalScope()
                .unboundNetworkContainer
                .value.acmeApi
                .getACMEDirectories()

            assertTrue(result.isSuccessful())
            assertEquals(expectedResult.newNonce, result.value.newNonce)
            assertEquals(expectedResult.newAccount, result.value.newAccount)
            assertEquals(expectedResult.newOrder, result.value.newOrder)
            assertEquals(expectedResult.revokeCert, result.value.revokeCert)
            assertEquals(expectedResult.keyChange, result.value.keyChange)
        }
    }

    companion object {
        val HOME_DIRECTORY: String = homeDirectory()
    }
}
