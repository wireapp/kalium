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
package com.wire.kalium.logic.architecture

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.ext.list.withNameEndingWith
import com.lemonappdev.konsist.api.ext.list.withParentNamed
import com.lemonappdev.konsist.api.verify.assertTrue
import kotlin.test.Test

class UseCaseRulesTest {

    @Test
    fun classesWithUseCaseSuffixShouldResideInFeaturePackage() {
        Konsist.scopeFromProduction()
            .classes()
            .withNameEndingWith("UseCase")
            .assertTrue { it.resideInPackage("..feature..") }
    }

    @Test
    fun classesWithUseCaseSuffixShouldHaveSinglePublicOperatorFunctionCalledInvoke() {
        Konsist
            .scopeFromProduction()
            .classes()
            .withNameEndingWith("UseCase")
            .assertTrue {
                val hasSingleInvokeOperatorMethod = it.hasFunction { function ->
                    function.name == "invoke" && function.hasPublicOrDefaultModifier && function.hasOperatorModifier
                }

                hasSingleInvokeOperatorMethod && it.countFunctions { item -> item.hasPublicOrDefaultModifier } == 1
            }
    }

    @Test
    fun everyUseCaseClassHasTests() {
        Konsist
            .scopeFromProduction()
            .classes()
            .withParentNamed("UseCase")
            .assertTrue { it.hasTestClass() }
    }

    @Test
    fun everyUseCaseClassHasKDocs() {
        Konsist
            .scopeFromProduction()
            .classes()
            .withParentNamed("UseCase")
            .assertTrue { it.hasKDoc }
    }

    @Test
    fun useCaseImplementationsShouldBeInternalOrHaveInternalConstructor() {
        Konsist.scopeFromProduction()
            .classes()
            .withNameEndingWith("UseCaseImpl")
            .assertTrue { useCase ->
                useCase.hasAllConstructors { it.hasInternalModifier } || useCase.hasInternalModifier
            }
    }
}
