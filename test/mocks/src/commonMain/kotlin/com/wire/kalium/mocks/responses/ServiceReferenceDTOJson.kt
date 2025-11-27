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

package com.wire.kalium.mocks.responses

import com.wire.kalium.network.api.authenticated.conversation.ServiceReferenceDTO

object ServiceReferenceDTOJson {
    val valid = ValidJsonProvider(
        ServiceReferenceDTO("ID", "provider")
    ) {
        """
        |{
        |   "id":"${it.id}",
        |   "provider":"${it.provider}"
        |}
        """.trimMargin()
    }
    val missingId = FaultyJsonProvider(
        """
        |{
        |   "provider":"123"
        |}
        """.trimMargin()
    )
    val wrongIdFormat = FaultyJsonProvider(
        """
        |{
        |   "id":123,
        |   "provider":"123"
        |}
        """.trimMargin()
    )
    val missingProvider = FaultyJsonProvider(
        """
        |{
        |   "id":"123"
        |}
        """.trimMargin()
    )
    val wrongProviderFormat = FaultyJsonProvider(
        """
        |{
        |   "id":"123",
        |   "provider":123
        |}
        """.trimMargin()
    )
}
