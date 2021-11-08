package com.wire.kalium.exceptions

import java.io.IOException
import java.util.UUID

class MissingStateException(botId: UUID?) : IOException("Unknown botId: $botId")
