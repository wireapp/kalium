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
        return Worker(js("""new URL("@cashapp/sqldelight-sqljs-worker/sqljs.worker.js", window.location.href)"""))
    }

    return js(
        """
        (function() {
            var WorkerCtor = require('node:worker_threads').Worker;
            var sqlJsModulePath = require.resolve('sql.js');
            var sqlJsWasmPath = require.resolve('sql.js/dist/sql-wasm.wasm');
            var workerCode = [
                "const { parentPort, workerData } = require('node:worker_threads');",
                "const initSqlJs = require(workerData.sqlJsModulePath);",
                "let dbPromise = initSqlJs({ locateFile: () => workerData.sqlJsWasmPath }).then(SQL => new SQL.Database());",
                "function resultFor(db, sql, params) {",
                "  const result = db.exec(sql, params)[0];",
                "  if (result) return result;",
                "  return { values: [[db.getRowsModified()]] };",
                "}",
                "parentPort.on('message', async (data) => {",
                "  try {",
                "    const db = await dbPromise;",
                "    switch (data && data.action) {",
                "      case 'exec':",
                "        if (!data.sql) throw new Error('exec: Missing query string');",
                "        parentPort.postMessage({ id: data.id, results: resultFor(db, data.sql, data.params) });",
                "        break;",
                "      case 'begin_transaction':",
                "        parentPort.postMessage({ id: data.id, results: resultFor(db, 'BEGIN TRANSACTION;', []) });",
                "        break;",
                "      case 'end_transaction':",
                "        parentPort.postMessage({ id: data.id, results: resultFor(db, 'END TRANSACTION;', []) });",
                "        break;",
                "      case 'rollback_transaction':",
                "        parentPort.postMessage({ id: data.id, results: resultFor(db, 'ROLLBACK TRANSACTION;', []) });",
                "        break;",
                "      default:",
                "        throw new Error('Unsupported action: ' + (data && data.action));",
                "    }",
                "  } catch (error) {",
                "    parentPort.postMessage({ id: data && data.id, error: { message: error && error.message, name: error && error.name } });",
                "  }",
                "});"
            ].join('\n');
            var worker = new WorkerCtor(workerCode, {
                eval: true,
                workerData: {
                    sqlJsModulePath: sqlJsModulePath,
                    sqlJsWasmPath: sqlJsWasmPath
                }
            });
            if (typeof worker.unref === 'function') {
                worker.unref();
            }
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
