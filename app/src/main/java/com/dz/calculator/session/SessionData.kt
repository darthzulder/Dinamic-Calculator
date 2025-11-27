package com.dz.calculator.session

import java.time.LocalDate

data class SessionData(
    val id: Int,
    val name: String,
    val canvasState: String,
    val timestamp: Long,
    val date: LocalDate
)
