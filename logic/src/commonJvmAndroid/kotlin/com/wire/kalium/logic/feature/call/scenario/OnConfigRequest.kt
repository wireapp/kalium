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

package com.wire.kalium.logic.feature.call.scenario

import com.wire.kalium.calling.AvsCallConfigRequestHandler
import com.wire.kalium.calling.Calling
import com.wire.kalium.calling.callbacks.CallConfigRequestHandler
import com.wire.kalium.common.logger.callingLogger
import com.wire.kalium.logic.data.call.CallRepository
import com.wire.kalium.logic.feature.call.AvsCallBackError
import com.wire.kalium.common.functional.nullableFold
import kotlinx.coroutines.CoroutineScope

@Suppress("FunctionNaming")
internal fun OnConfigRequest(
    calling: Calling,
    callRepository: CallRepository,
    callingScope: CoroutineScope,
    configTransformer: ((String) -> String)? = null,
): CallConfigRequestHandler = AvsCallConfigRequestHandler(
        scope = callingScope,
        loadConfig = {
            callingLogger.i("[OnConfigRequest] - STARTED")
            callRepository.getCallConfigResponse(limit = null).nullableFold(
                {
                    callingLogger.w("[OnConfigRequest] - Error: $it")
                    null
                },
                { config -> configTransformer?.invoke(config) ?: config },
            )
        },
        respond = { inst, config ->
            calling.wcall_config_update(inst, if (config == null) 1 else 0, config.orEmpty())
            if (config != null) callingLogger.i("[OnConfigRequest] - wcall_config_update()")
        },
        callbackResult = AvsCallBackError.NONE.value,
    )
