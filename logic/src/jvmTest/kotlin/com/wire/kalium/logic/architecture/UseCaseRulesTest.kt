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
import com.lemonappdev.konsist.api.verify.assertTrue
import org.junit.Ignore
import kotlin.test.Test

class UseCaseRulesTest {

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

    @Ignore("Ignored for now, needs fine tune and violations fixed which are quite many currently for this scope")
    @Test
    fun everyUseCaseClassHasTests() {
        Konsist
            .scopeFromProduction()
            .classes()
            .withNameEndingWith(suffixes = listOf("UseCaseImpl", "UseCase"))
            .assertTrue { clazz ->
                // For UseCaseImpl classes, test should be named UseCaseTest (without Impl)
                // For standalone UseCase classes, test should be named UseCaseTest
                val expectedTestName = if (clazz.name.endsWith("Impl")) {
                    clazz.name.removeSuffix("Impl") + "Test"
                } else {
                    "${clazz.name}Test"
                }

                // Check if a test file exists with the expected test class name
                Konsist
                    .scopeFromTest()
                    .classes()
                    .any { testClass -> testClass.name == expectedTestName }
            }
    }

    @Test
    fun everyPublicUseCaseInterfaceHasKDocs() {
        Konsist
            .scopeFromProduction()
            .interfaces()
            .withNameEndingWith("UseCase")
            .filter { it.hasPublicOrDefaultModifier }
            .assertTrue { useCase ->
                useCase.hasKDoc || useCase.functions()
                    .any { func -> func.name == "invoke" && func.hasOperatorModifier && func.hasKDoc }
            }
    }

    @Test
    fun everyPublicUseCaseClassHasKDocs() {
        Konsist
            .scopeFromProduction()
            .classes()
            .withNameEndingWith("UseCase")
            .filter { it.hasPublicOrDefaultModifier }
            .assertTrue { useCase ->
                useCase.hasKDoc || useCase.functions()
                    .any { func -> func.name == "invoke" && func.hasOperatorModifier && func.hasKDoc }
            }
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

    @Test
    @Ignore("Ignored for now, this is being resolved incrementally, enable when all of the violations are fixed")
    fun kaliumLogicModuleShouldNotExposeEitherTypesInPublicAPI() {
        val scope = Konsist.scopeFromProduction()
        val violations = mutableListOf<String>()

        // Check public interfaces in kalium/logic module only
        scope.interfaces()
            .filter { it.containingFile.path.contains("/kalium/logic/src/") }
            .filter { it.hasPublicOrDefaultModifier }
            .forEach { iface ->
                iface.functions()
                    .filter { it.hasPublicOrDefaultModifier }
                    .filter { it.returnType?.sourceType?.contains("Either<") == true }
                    .forEach { func ->
                        violations.add(
                            "Public interface '${iface.name}' in ${iface.containingFile.name} " +
                                    "has function '${func.name}' returning Either: ${func.returnType?.sourceType}"
                        )
                    }
            }

        // Check public classes in kalium/logic module only
        scope.classes()
            .filter { it.containingFile.path.contains("/kalium/logic/src/") }
            .filter { it.hasPublicOrDefaultModifier }
            .forEach { clazz ->
                clazz.functions()
                    .filter { it.hasPublicOrDefaultModifier }
                    .filter { it.returnType?.sourceType?.contains("Either<") == true }
                    .forEach { func ->
                        violations.add(
                            "Public class '${clazz.name}' in ${clazz.containingFile.name} " +
                                    "has function '${func.name}' returning Either: ${func.returnType?.sourceType}"
                        )
                    }
            }

        if (violations.isNotEmpty()) {
            val message = buildString {
                appendLine("Either types should not be exposed in public API of :kalium:logic module. Found ${violations.size} violation(s):")
                violations.forEach { violation ->
                    appendLine("  - $violation")
                }
            }
            throw AssertionError(message)
        }
    }
}
