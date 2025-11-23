package com.forz.calculator.canvas

/**
 * Representa una conexión entre nodos en el canvas
 */
data class NodeConnection(
    val fromNodeId: String,
    val toNodeId: String,
    val color: Int
)

