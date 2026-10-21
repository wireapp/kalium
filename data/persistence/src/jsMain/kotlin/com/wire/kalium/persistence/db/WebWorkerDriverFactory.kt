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

// Node worker threads whose database was closed and reset, ready to host the next database.
private val idleNodeWorkers: dynamic = js("[]")

@Suppress("LongMethod", "UnsafeCastFromDynamic")
private fun createKaliumWorker(): Worker {
    val hasBrowserWorker = js("typeof window !== 'undefined' && typeof Worker !== 'undefined'").unsafeCast<Boolean>()
    if (hasBrowserWorker) {
        return Worker(js("""new URL("@cashapp/sqldelight-sqljs-worker/sqljs.worker.js", window.location.href)"""))
    }

    val createNodeWorker: dynamic = js(
        """
        (function(idleWorkers) {
            var worker = idleWorkers.pop() || createWorker();
            var listeners = new Map();
            var closed = false;

            function createWorker() {
                var WorkerCtor = require('node:worker_threads').Worker;
                var sqlJsModulePath = require.resolve('sql.js');
                var sqlJsWasmPath = require.resolve('sql.js/dist/sql-wasm.wasm');
                var workerCode = [
                    "const { parentPort, workerData } = require('node:worker_threads');",
                    "const initSqlJs = require(workerData.sqlJsModulePath);",
                    "const sqlPromise = initSqlJs({ locateFile: () => workerData.sqlJsWasmPath });",
                    "let dbPromise = sqlPromise.then(SQL => new SQL.Database());",
                    "const booleanColumnPattern = /^(deleted|incomplete_metadata|defederated|archived|deletedLocally|isFavorite|userDeleted|userDefederated|isUserDeleted|senderIsDeleted|senderDefederated|hasIncompleteMetadata|lastMessageIsSelfMessage|expects_read_confirmation|expectsReadConfirmation|isSelfMessage|isSelfDelete|isEphemeral|isMentioningSelfUser|isQuotingSelfUser|isQuotingSelf|isQuoteVerified|isUnread|isConversationAppsEnabled|newConversationReceiptMode|conversationReceiptModeChanged|isDecryptionResolved|is_selected|is_valid|is_verified|is_mls_capable|is_async_notifications_capable|legal_hold_status_change_notified|legalHoldStatusChangeNotified|degraded_conversation_notified|degradedConversationNotified|mls_degraded_notified|is_guest_password_protected|should_notify|read_receipt|enabled|notified|hasRegisteredMLSClient|isOnPremises|federation|apiProxyNeedsAuthentication|isNativePushSupportedByServer|isPersistentWebSocketEnabled|has_password|meetingHasPassword)$/i;",
                    "function normalizeResult(sql, result) {",
                    "  if (!result || !Array.isArray(result.columns) || !Array.isArray(result.values)) return result;",
                    "  const booleanIndexes = result.columns",
                    "    .map((column, index) => booleanColumnPattern.test(String(column)) ? index : -1)",
                    "    .filter(index => index >= 0);",
                    "  if (result.columns.length === 1 && /^\\s*select\\s+exists\\s*\\(/i.test(sql)) booleanIndexes.push(0);",
                    "  if (booleanIndexes.length === 0) return result;",
                    "  return {",
                    "    values: result.values.map(row => row.map((value, index) =>",
                    "      booleanIndexes.includes(index) && (value === 0 || value === 1) ? value === 1 : value",
                    "    ))",
                    "  };",
                    "}",
                    "function isQuery(sql) {",
                    "  return /^\\s*(select|with|pragma|explain)\\b/i.test(sql);",
                    "}",
                    "function resultFor(db, sql, params) {",
                    "  const result = db.exec(sql, params)[0];",
                    "  if (result) return normalizeResult(sql, result);",
                    "  if (isQuery(sql)) return { values: [] };",
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
                    "      case 'reset':",
                    "        db.close();",
                    "        dbPromise = sqlPromise.then(SQL => new SQL.Database());",
                    "        parentPort.postMessage({ id: data.id, results: { values: [] } });",
                    "        break;",
                    "      default:",
                    "        throw new Error('Unsupported action: ' + (data && data.action));",
                    "    }",
                    "  } catch (error) {",
                    "    parentPort.postMessage({ id: data && data.id, error: { message: error && error.message, name: error && error.name } });",
                    "  }",
                    "});"
                ].join('\n');
                var created = new WorkerCtor(workerCode, {
                    eval: true,
                    workerData: {
                        sqlJsModulePath: sqlJsModulePath,
                        sqlJsWasmPath: sqlJsWasmPath
                    }
                });
                if (typeof created.setMaxListeners === 'function') {
                    created.setMaxListeners(0);
                }
                if (typeof created.unref === 'function') {
                    created.unref();
                }
                created.once('exit', function() {
                    var index = idleWorkers.indexOf(created);
                    if (index >= 0) idleWorkers.splice(index, 1);
                });
                return created;
            }

            return {
                postMessage: function(message) {
                    // A closed driver must not reach the next database hosted by this thread.
                    if (!closed) worker.postMessage(message);
                },
                // Reuse the thread instead of stopping it. Each new worker recompiles sql.js, and the Node test
                // process died intermittently on Linux CI while tests stopped a worker per database.
                terminate: function() {
                    if (closed) return;
                    closed = true;
                    listeners.forEach(function(entry) {
                        worker.off(entry.eventName, entry.handler);
                    });
                    listeners.clear();
                    // Replies are ordered, so the reset reply follows every reply to this driver.
                    var onReset = function(message) {
                        if (!message || message.id !== 'kalium-reset') return;
                        worker.off('message', onReset);
                        if (!message.error) idleWorkers.push(worker);
                    };
                    worker.on('message', onReset);
                    worker.postMessage({ id: 'kalium-reset', action: 'reset' });
                },
                addEventListener: function(type, listener) {
                    // A message listener keeps the process alive, and a closed driver never gets a reply.
                    if (closed) return;
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
        })
        """
    )
    return createNodeWorker(idleNodeWorkers).unsafeCast<Worker>()
}
