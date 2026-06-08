package com.dz.calculator.calculator

import android.content.Context
import com.dz.calculator.utils.NumberFormatter
import com.dz.calculator.R
import com.dz.calculator.settings.Config.decimalSeparatorSymbol
import com.dz.calculator.settings.Config.groupingSeparatorSymbol
import com.dz.calculator.settings.Config.maxScientificNotationDigits
import com.dz.calculator.settings.Config.numberPrecision
import org.javia.arity.Symbols
import java.math.BigDecimal

data class EvaluationResult(
    val resultString: String,
    val numericResult: Double?,
    val isCalculated: Boolean
)

object Evaluator {
    private val symbols: Symbols = Symbols()

    // Public method for calculation without Context (e.g. for CanvasViewModel)
    fun evaluate(expression: String): BigDecimal {
        val cleanExpression = NumberFormatter.clearExpression(expression, groupingSeparatorSymbol, decimalSeparatorSymbol)
        if (cleanExpression.isEmpty()) {
            throw IllegalArgumentException("Expression is empty")
        }

        try {
            val result: Double = symbols.eval(cleanExpression)
            if (java.lang.Double.isNaN(result) || result.isInfinite()) {
                throw ArithmeticException("Invalid result: NaN or Infinity")
            }
            return BigDecimal.valueOf(result)
        } catch (e: org.javia.arity.SyntaxException) {
            throw IllegalArgumentException("Invalid expression syntax", e)
        }
    }

    fun evaluate(expr: String, context: Context): EvaluationResult {
        var exp = expr

        if (exp == "Anastasia" || exp == "Анастасия"){
            return EvaluationResult("I love you❤\uFE0F", null, false)
        }

        exp = NumberFormatter.clearExpression(exp, groupingSeparatorSymbol, decimalSeparatorSymbol)
        if (exp.isEmpty()) {
            return EvaluationResult("", null, false)
        } else if (exp.toDoubleOrNull() != null) {
            return EvaluationResult("", exp.toDouble(), false)
        }

        try {
            val result: Double = symbols.eval(exp)
            if (java.lang.Double.isNaN(result)) {
                return EvaluationResult(context.getString(R.string.expression_error), null, false)
            } else if (result.isInfinite()){
                return EvaluationResult(context.getString(R.string.expression_infinity), null, false)
            } else {
                val formatted = NumberFormatter.formatResult(
                    BigDecimal.valueOf(result),
                    numberPrecision,
                    maxScientificNotationDigits,
                    groupingSeparatorSymbol,
                    decimalSeparatorSymbol
                )
                return EvaluationResult(formatted, result, true)
            }
        } catch (e: Exception) {
            return EvaluationResult(context.getString(R.string.expression_error), null, false)
        }
    }
}