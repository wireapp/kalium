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

import com.wire.kalium.network.api.base.model.AccessTokenDTO
import com.wire.kalium.network.api.base.model.QualifiedID
import com.wire.kalium.network.tools.KtxSerializer
import kotlinx.serialization.encodeToString

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
object CommonResponses {

    /**
     * URL Paths
     */
    const val BASE_PATH = "https://test.api.com/"
    const val BASE_PATH_V1 = "${BASE_PATH}v1/"

    /**
     * DTO
     */
    val userID = QualifiedID("user_id", "user.domain.io")
    private val accessTokenDTO = AccessTokenDTO(
        userId = userID.value,
        value = "Nlrhltkj-NgJUjEVevHz8Ilgy_pyWCT2b0kQb-GlnamyswanghN9DcC3an5RUuA7sh1_nC3hv2ZzMRlIhPM7Ag==.v=1.k=1.d=1637254939." +
                "t=a.l=.u=75ebeb16-a860-4be4-84a7-157654b492cf.c=18401233206926541098",
        expiresIn = 900,
        tokenType = "Bearer"
    )

    /**
     * JSON Response
     */
    const val REFRESH_TOKEN = "415a5306-a476-41bc-af36-94ab075fd881"
    val VALID_ACCESS_TOKEN_RESPONSE = KtxSerializer.json.encodeToString(accessTokenDTO)
}
