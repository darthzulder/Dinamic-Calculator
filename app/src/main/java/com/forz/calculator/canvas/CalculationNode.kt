package com.forz.calculator.canvas

import java.math.BigDecimal
import java.util.UUID

data class CalculationNode(
    val id: String = UUID.randomUUID().toString(),
    val expression: String,
    val result: BigDecimal,
    var positionX: Float,
    var positionY: Float
)
