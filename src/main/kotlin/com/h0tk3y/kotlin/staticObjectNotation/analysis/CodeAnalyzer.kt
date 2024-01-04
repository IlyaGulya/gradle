package com.h0tk3y.kotlin.staticObjectNotation.analysis

import com.h0tk3y.kotlin.staticObjectNotation.language.*

interface CodeAnalyzer {
    fun analyzeStatementsInProgramOrder(context: AnalysisContext, elements: List<DataStatement>)
}

class CodeAnalyzerImpl(
    private val analysisStatementFilter: AnalysisStatementFilter, private val statementResolver: StatementResolver
) : CodeAnalyzer {
    override fun analyzeStatementsInProgramOrder(
        context: AnalysisContext, elements: List<DataStatement>
    ) {
        for (element in elements) {
            if (analysisStatementFilter.shouldAnalyzeStatement(element, context.currentScopes)) {
                doResolveStatement(context, element)
            }
        }
    }

    private fun doResolveStatement(context: AnalysisContext, statement: DataStatement) {
        when (statement) {
            is Assignment -> statementResolver.doResolveAssignment(context, statement)
            is LocalValue -> statementResolver.doResolveLocalValue(context, statement)
            is Expr -> statementResolver.doResolveExpressionStatement(context, statement)
        }
    }

}
