package com.wire.kalium.network

import io.ktor.client.engine.HttpClientEngine

expect fun defaultHttpEngine(): HttpClientEngine
