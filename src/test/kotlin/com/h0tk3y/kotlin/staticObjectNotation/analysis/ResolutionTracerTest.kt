/*
 * Copyright 2024 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.com.h0tk3y.kotlin.staticObjectNotation.analysis

import com.h0tk3y.kotlin.staticObjectNotation.Adding
import com.h0tk3y.kotlin.staticObjectNotation.Restricted
import com.h0tk3y.kotlin.staticObjectNotation.analysis.ErrorReason
import com.h0tk3y.kotlin.staticObjectNotation.analysis.ObjectOrigin
import com.h0tk3y.kotlin.staticObjectNotation.analysis.ResolutionError
import com.h0tk3y.kotlin.staticObjectNotation.analysis.ResolutionTrace
import com.h0tk3y.kotlin.staticObjectNotation.analysis.tracingCodeResolver
import com.h0tk3y.kotlin.staticObjectNotation.demo.resolve
import com.h0tk3y.kotlin.staticObjectNotation.language.Assignment
import com.h0tk3y.kotlin.staticObjectNotation.language.FunctionArgument
import com.h0tk3y.kotlin.staticObjectNotation.language.FunctionCall
import com.h0tk3y.kotlin.staticObjectNotation.schemaBuilder.schemaFromTypes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull

object ResolutionTracerTest {
    val schema = schemaFromTypes(TopLevelReceiver::class, listOf(TopLevelReceiver::class))

    @Test
    fun `collects successful resolution of function`() {
        val resolver = tracingCodeResolver()
        val result = schema.resolve(
            """
            f(0) // must be an unresolved property x
            """.trimIndent(),
            resolver
        )

        val call = result.additions.single().dataObject.originElement as FunctionCall
        assertIs<ObjectOrigin.FunctionOrigin>(resolver.trace.expressionResolution(call).result)

        val arg = (call.args.single() as FunctionArgument.Positional).expr
        assertIs<ObjectOrigin.ConstantOrigin>(resolver.trace.expressionResolution(arg).result)
    }

    @Test
    fun `collects successful resolution of assignment`() {
        val resolver = tracingCodeResolver()
        val result = schema.resolve(
            """
            s = "test"
            """.trimIndent(),
            resolver
        )

        val assignment = result.assignments.single().originElement as Assignment
        assertNotNull(resolver.trace.assignmentResolution(assignment).result)
    }

    @Test
    fun `collects trace of resolution errors in a function call`() {
        val resolver = tracingCodeResolver()
        val result = schema.resolve(
            """
            f(x) // must be an unresolved property x
            """.trimIndent(),
            resolver
        )

        val call = result.errors.first { it.errorReason is ErrorReason.UnresolvedFunctionCallSignature }.element as FunctionCall
        val arg = (call.args[0] as FunctionArgument.ValueArgument).expr

        val outerErrors = assertNotNull(resolver.trace.expressionResolution(call).errors)
        assertEquals(2, outerErrors.size)
        assertEquals(
            setOf(ErrorReason.UnresolvedFunctionCallSignature::class, ErrorReason.UnresolvedFunctionCallArguments::class),
            outerErrors.map { it.errorReason::class }.toSet()
        )

        val innerErrors = assertNotNull(resolver.trace.expressionResolution(arg).errors)
        assertIs<ErrorReason.UnresolvedReference>(innerErrors.single().errorReason)
    }

    @Test
    fun `collects trace of resolution errors in assignment`() {
        val resolver = tracingCodeResolver()
        val result = schema.resolve(
            """
            s = nope
            x = "yep"
            """.trimIndent(),
            resolver
        )

        val failedLhs = result.errors.first { it.errorReason is ErrorReason.UnresolvedAssignmentLhs }.element as Assignment
        assertNull(resolver.trace.assignmentResolution(failedLhs).result)
        assertEquals(listOf(ErrorReason.UnresolvedAssignmentLhs::class), resolver.trace.assignmentResolution(failedLhs).errors?.map { it.errorReason::class })

        assertNull(resolver.trace.expressionResolution(failedLhs.lhs).result)
        assertEquals(listOf(ErrorReason.UnresolvedReference::class), resolver.trace.expressionResolution(failedLhs.lhs).errors?.map { it.errorReason::class })

        val failedRhs = result.errors.first { it.errorReason is ErrorReason.UnresolvedAssignmentRhs }.element as Assignment
        assertNull(resolver.trace.assignmentResolution(failedRhs).result)
        assertEquals(listOf(ErrorReason.UnresolvedAssignmentRhs::class), resolver.trace.assignmentResolution(failedRhs).errors?.map { it.errorReason::class })
    }

    @Test
    fun `returns no-resolution result when an element has an unresolved base`() {
        val resolver = tracingCodeResolver()
        val result = schema.resolve(
            """
            noSuchBlock {
                x = 123
            }
            """.trimIndent(),
            resolver
        )

        val unresolvedFunctionCall = result.errors.single { it.errorReason is ErrorReason.UnresolvedFunctionCallSignature }.element as FunctionCall
        val assignmentInBlock = (unresolvedFunctionCall.args.single() as FunctionArgument.Lambda).block.statements.single() as Assignment

        assertIs<ResolutionTrace.ResolutionOrErrors.NoResolution>(resolver.trace.assignmentResolution(assignmentInBlock))
    }

    class TopLevelReceiver {
        @Adding
        fun f(@Suppress("UNUSED_PARAMETER") x: Int) = 0

        @Restricted
        var s: String = ""
    }

    private val <R> ResolutionTrace.ResolutionOrErrors<R>.result: R? get() = (this as? ResolutionTrace.ResolutionOrErrors.Resolution)?.result
    private val <R> ResolutionTrace.ResolutionOrErrors<R>.errors: List<ResolutionError>? get() = (this as? ResolutionTrace.ResolutionOrErrors.Errors)?.errors
}
