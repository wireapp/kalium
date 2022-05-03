package com.wire.kalium.logic.sync

import com.wire.kalium.logic.feature.UserSessionScope
import kotlin.test.Ignore

/**
 * Currently unable to test, as subclasses of [UserSessionWorker] require
 * a mandatory [UserSessionScope] parameter in the constructor.
 */
@Ignore
class PendingMessagesSenderWorkerTest
