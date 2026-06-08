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

@Suppress("LongMethod", "UnsafeCastFromDynamic")
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
                "const booleanColumnPattern = /^(deleted|incomplete_metadata|defederated|archived|deletedLocally|isFavorite|isChannel|is_channel|userDeleted|userDefederated|isUserDeleted|senderIsDeleted|senderDefederated|hasIncompleteMetadata|lastMessageIsSelfMessage|expects_read_confirmation|expectsReadConfirmation|isSelfMessage|isSelfDelete|isEphemeral|isMentioningSelfUser|isQuotingSelfUser|isQuotingSelf|isQuoteVerified|isUnread|isConversationAppsEnabled|newConversationReceiptMode|conversationReceiptModeChanged|isDecryptionResolved|is_selected|cell_asset|is_valid|is_verified|is_mls_capable|is_async_notifications_capable|legal_hold_status_change_notified|legalHoldStatusChangeNotified|degraded_conversation_notified|degradedConversationNotified|mls_degraded_notified|is_guest_password_protected|should_notify|read_receipt|enabled|notified|hasRegisteredMLSClient|isOnPremises|federation|apiProxyNeedsAuthentication|isNativePushSupportedByServer|isPersistentWebSocketEnabled)$/i;",
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
            if (typeof worker.setMaxListeners === 'function') {
                worker.setMaxListeners(0);
            }
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
