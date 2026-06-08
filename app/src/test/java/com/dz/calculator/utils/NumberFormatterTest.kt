package com.dz.calculator.utils

import org.junit.Assert.assertEquals
import org.junit.Test

class NumberFormatterTest {

    @Test
    fun testSanitizeExpression_emptyAndSingleNumbers() {
        assertEquals("", NumberFormatter.sanitizeExpression("", ",", "."))
        assertEquals("", NumberFormatter.sanitizeExpression("   ", ",", "."))
        assertEquals("3", NumberFormatter.sanitizeExpression("3", ",", "."))
        assertEquals("3.5", NumberFormatter.sanitizeExpression("3.5", ",", "."))
        assertEquals("123", NumberFormatter.sanitizeExpression("123", ",", "."))
    }

    @Test
    fun testSanitizeExpression_trailingOperators() {
        assertEquals("3", NumberFormatter.sanitizeExpression("3+", ",", "."))
        assertEquals("3", NumberFormatter.sanitizeExpression("3–", ",", ".")) // En-dash minus
        assertEquals("3", NumberFormatter.sanitizeExpression("3-", ",", "."))  // Hyphen minus
        assertEquals("3", NumberFormatter.sanitizeExpression("3×", ",", "."))
        assertEquals("3", NumberFormatter.sanitizeExpression("3÷", ",", "."))
        assertEquals("3", NumberFormatter.sanitizeExpression("3^", ",", "."))
        assertEquals("3", NumberFormatter.sanitizeExpression("3E", ",", "."))
    }

    @Test
    fun testSanitizeExpression_trailingOperatorsWithSpaces() {
        assertEquals("3", NumberFormatter.sanitizeExpression("3 + ", ",", "."))
        assertEquals("3", NumberFormatter.sanitizeExpression("3 –  ", ",", "."))
        assertEquals("3", NumberFormatter.sanitizeExpression("3 × ", ",", "."))
        assertEquals("3", NumberFormatter.sanitizeExpression("3 ÷ ", ",", "."))
    }

    @Test
    fun testSanitizeExpression_multipleTrailingOperators() {
        assertEquals("3", NumberFormatter.sanitizeExpression("3++", ",", "."))
        assertEquals("3", NumberFormatter.sanitizeExpression("3+ -", ",", "."))
        assertEquals("3", NumberFormatter.sanitizeExpression("3 × ÷", ",", "."))
    }

    @Test
    fun testSanitizeExpression_trailingDecimalSeparator() {
        assertEquals("3", NumberFormatter.sanitizeExpression("3.", ",", "."))
        assertEquals("3", NumberFormatter.sanitizeExpression("3,", ",", ",")) // using comma as decimal separator
    }

    @Test
    fun testSanitizeExpression_trailingFunctionsAndOpenBrackets() {
        assertEquals("", NumberFormatter.sanitizeExpression("sin(", ",", "."))
        assertEquals("3", NumberFormatter.sanitizeExpression("3 + cos(", ",", "."))
        assertEquals("3", NumberFormatter.sanitizeExpression("3 + (", ",", "."))
        assertEquals("3", NumberFormatter.sanitizeExpression("3 + (  ", ",", "."))
    }

    @Test
    fun testSanitizeExpression_unbalancedParentheses() {
        assertEquals("((3+2)/3)", NumberFormatter.sanitizeExpression("((3+2)/3", ",", "."))
        assertEquals("((3+2))", NumberFormatter.sanitizeExpression("((3+2", ",", "."))
        assertEquals("3+sin(5)", NumberFormatter.sanitizeExpression("3+sin(5", ",", "."))
    }

    @Test
    fun testSanitizeExpression_unmatchedClosingParentheses() {
        assertEquals("3+2", NumberFormatter.sanitizeExpression("3+2)", ",", "."))
        assertEquals("(3+2)", NumberFormatter.sanitizeExpression("(3+2))", ",", "."))
        assertEquals("3+2", NumberFormatter.sanitizeExpression(")3+2", ",", "."))
    }

    @Test
    fun testSanitizeExpression_complexIncompleteExpressions() {
        // ((3+2)/3+ -> trailing '+' removed -> ((3+2)/3 -> balanced -> ((3+2)/3)
        assertEquals("((3+2)/3)", NumberFormatter.sanitizeExpression("((3+2)/3+", ",", "."))
        // 3 + sin(5 + -> trailing '+' and '(' -> 3 -> balanced -> 3
        assertEquals("3", NumberFormatter.sanitizeExpression("3 + sin(", ",", "."))
        // (3+2) * (5+ -> trailing '+' -> (3+2) * (5 -> balanced -> (3+2) * (5)
        assertEquals("(3+2) * (5)", NumberFormatter.sanitizeExpression("(3+2) * (5+", ",", "."))
    }
}
