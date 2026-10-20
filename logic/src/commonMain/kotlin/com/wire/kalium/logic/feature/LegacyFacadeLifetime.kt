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
package com.wire.kalium.logic.feature

/**
 * Shared capture contract for a legacy facade returned fresh by a session getter.
 *
 * Each facade owns a newly created graph extension. SingleIn(LegacyFacadeLifetime) retains only
 * that instance's independently requested constructor captures; use cases remain factories.
 * Never retain one of these extensions in the user graph or reuse it for a later facade access.
 * The narrow, typed extensions differ by capture inputs, including AppScope's explicit version
 * snapshot, rather than representing package-wide feature owners. Underlying account resources
 * and provider caches keep their existing owners; these extensions own no work or cleanup.
 */
internal abstract class LegacyFacadeLifetime private constructor()
