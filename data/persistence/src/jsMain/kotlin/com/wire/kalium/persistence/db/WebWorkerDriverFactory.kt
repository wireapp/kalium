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

package com.wire.kalium.persistence.db

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.worker.WebWorkerDriver
import org.w3c.dom.Worker

internal fun createKaliumWebWorkerDriver(): SqlDriver = WebWorkerDriver(createKaliumWorker())

@Suppress("UnsafeCastFromDynamic")
private fun createKaliumWorker(): Worker {
    val hasBrowserWorker = js("typeof window !== 'undefined' && typeof Worker !== 'undefined'").unsafeCast<Boolean>()
    if (hasBrowserWorker) {
        return Worker(js("""new URL("@cashapp/sqldelight-sqljs-worker/sqljs.worker.js", import.meta.url)"""))
    }

    return js(
        """
        (function() {
            var WorkerCtor = require('node:worker_threads').Worker;
            var workerPath = require.resolve('@cashapp/sqldelight-sqljs-worker/sqljs.worker.js');
            var worker = new WorkerCtor(workerPath);
            var listeners = new Map();

            return {
                postMessage: function(message) {
                    worker.postMessage(message);
                },
                terminate: function() {
                    return worker.terminate();
                },
                addEventListener: function(type, listener) {
                    var eventName = type === 'message' ? 'message' : 'error';
                    var handler = function(payload) {
                        if (typeof listener === 'function') {
                            listener({ data: payload });
                        } else if (listener && typeof listener.handleEvent === 'function') {
                            listener.handleEvent(eventName === 'message' ? { data: payload } : payload);
                        }
                    };

                    listeners.set(listener, { eventName: eventName, handler: handler });
                    worker.on(eventName, handler);
                },
                removeEventListener: function(_type, listener) {
                    var entry = listeners.get(listener);
                    if (!entry) return;

                    worker.off(entry.eventName, entry.handler);
                    listeners.delete(listener);
                }
            };
        })()
        """
    ).unsafeCast<Worker>()
}
