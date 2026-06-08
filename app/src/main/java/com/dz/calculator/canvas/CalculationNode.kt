package com.dz.calculator.canvas

import java.math.BigDecimal
import java.util.UUID
import kotlinx.serialization.Serializable
import com.dz.calculator.utils.BigDecimalSerializer

@Serializable
data class CalculationNode(
    val id: String = UUID.randomUUID().toString(),
    val expression: String,
    @Serializable(with = BigDecimalSerializer::class)
    val result: BigDecimal,
    var positionX: Float,
    var positionY: Float,
    val parentNodeIds: List<String> = emptyList(), // IDs de los nodos que dieron origen a este nodo
    val connectionColor: Int = 0, // Color único para las líneas de conexión
    val name: String = "", // Nombre del nodo
    val description: String = "" // Descripción del nodo
)
