/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
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

#pragma once

#import <Network/Network.h>

// Network.framework may supply a block-backed default/final content context.
// Never bridge that unused context to Kotlin: Kotlin/Native tries to convert
// it to kotlin.Any and aborts for its void-returning Objective-C block signature.
static inline void wire_preview_receive(
    nw_connection_t connection,
    uint32_t minimumIncompleteLength,
    uint32_t maximumLength,
    void (^completion)(dispatch_data_t, bool, nw_error_t)
) {
    nw_connection_receive(connection, minimumIncompleteLength, maximumLength,
        ^(dispatch_data_t data, nw_content_context_t context, bool complete, nw_error_t error) {
            completion(data, complete, error);
        });
}
