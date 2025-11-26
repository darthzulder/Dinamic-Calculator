package com.dz.calculator.calculator

import android.content.Context
import android.widget.TextView
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.dz.calculator.utils.NumberFormatter
import com.dz.calculator.R
import com.dz.calculator.expression.ExpressionEditText
import com.dz.calculator.settings.Config.decimalSeparatorSymbol
import com.dz.calculator.settings.Config.groupingSeparatorSymbol
import com.dz.calculator.settings.Config.maxScientificNotationDigits
import com.dz.calculator.settings.Config.numberPrecision
import org.javia.arity.Symbols
import java.math.BigDecimal

object Evaluator: ViewModel() {
    var isCalculated = false

    private val _converterResult = MutableLiveData<Double?>()
    val converterResult: LiveData<Double?> get() = _converterResult

    private val symbols: Symbols = Symbols()

    // NEW Public method for calculation without Context
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

    private fun evaluate(expr: String, context: Context): String {
        var exp = expr

        if (exp == "Anastasia" || exp == "Анастасия"){
            _converterResult.value = null
            isCalculated = false
            return "I love you❤\uFE0F"
        }

        exp = NumberFormatter.clearExpression(exp, groupingSeparatorSymbol, decimalSeparatorSymbol)
        if (exp.isEmpty()) {
            _converterResult.value = null
            isCalculated = false
            return ""
        }else if (exp.toDoubleOrNull() != null) {
            _converterResult.value = exp.toDouble()
            isCalculated = false
            return ""
        }

        try {
            val result: Double = symbols.eval(exp)
            if (java.lang.Double.isNaN(result)) {
                _converterResult.value = null
                isCalculated = false
                return context.getString(R.string.expression_error)
            } else if (result.isInfinite()){
                _converterResult.value = null
                isCalculated = false
                return context.getString(R.string.expression_infinity)
            }else{
                _converterResult.value = result
                isCalculated = true
                return NumberFormatter.formatResult(BigDecimal.valueOf(result), numberPrecision, maxScientificNotationDigits, groupingSeparatorSymbol, decimalSeparatorSymbol)
            }
        } catch (e: Exception) {
            _converterResult.value = null
            isCalculated = false
            return context.getString(R.string.expression_error)
        }
    }

    fun getResult(expressionEditText: ExpressionEditText, isSelected: Boolean, context: Context): String{
        return if (isSelected){
            evaluate(
                expressionEditText.text
                    .toString()
                    .substring(
                        expressionEditText.selectionStart, expressionEditText.selectionEnd),
                context)
        }else{
            evaluate(expressionEditText.text.toString(), context)
        }
    }

    fun setResultTextView(expressionEditText: ExpressionEditText, resultTextView: TextView, isSelected: Boolean, context: Context){
        resultTextView.text = getResult(expressionEditText, isSelected, context)
    }
}