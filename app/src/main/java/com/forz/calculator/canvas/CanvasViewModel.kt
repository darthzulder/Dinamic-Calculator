package com.forz.calculator.canvas

import androidx.lifecycle.ViewModel
import com.forz.calculator.calculator.Evaluator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.math.BigDecimal

class CanvasViewModel : ViewModel() {

    private val _nodes = MutableStateFlow<List<CalculationNode>>(emptyList())
    val nodes: StateFlow<List<CalculationNode>> = _nodes

    private var pendingCombination: Pair<String, String>? = null

    // Constantes para posicionamiento de nodos
    companion object {
        private const val INITIAL_X = 150f
        private const val INITIAL_Y = 150f
        private const val NODE_HEIGHT_WITH_MARGIN = 150f // Altura aproximada del nodo + margen de separación
        private const val X_TOLERANCE = 20f // Tolerancia en píxeles para considerar que dos nodos están en la misma columna X
    }

    fun addNode(expression: String, result: BigDecimal) {
        val initialX = INITIAL_X
        val initialY = INITIAL_Y
        
        // Buscar todos los nodos que estén en la misma posición X (misma columna)
        // con una tolerancia para considerar que están alineados verticalmente
        val nodesInSameColumn = _nodes.value.filter { node ->
            kotlin.math.abs(node.positionX - initialX) <= X_TOLERANCE
        }
        
        // Calcular la nueva posición Y
        val newY = if (nodesInSameColumn.isEmpty()) {
            // Si no hay nodos en esta columna, usar la posición inicial
            initialY
        } else {
            // Encontrar el nodo con la posición Y más baja (más abajo)
            val lowestNode = nodesInSameColumn.maxByOrNull { it.positionY }
            // Colocar el nuevo nodo debajo del nodo más bajo
            (lowestNode?.positionY ?: initialY) + NODE_HEIGHT_WITH_MARGIN
        }
        
        val newNode = CalculationNode(
            expression = expression,
            result = result,
            positionX = initialX, 
            positionY = newY
        )
        _nodes.value = _nodes.value + newNode
    }

    fun updateNodePosition(nodeId: String, x: Float, y: Float) {
        _nodes.value = _nodes.value.map {
            if (it.id == nodeId) {
                it.copy(positionX = x, positionY = y)
            } else {
                it
            }
        }
    }

    fun setPendingCombination(sourceNodeId: String, targetNodeId: String) {
        pendingCombination = Pair(sourceNodeId, targetNodeId)
    }

    fun combineNodes(operator: String) {
        pendingCombination?.let { (sourceId, targetId) ->
            val sourceNode = _nodes.value.find { it.id == sourceId }
            val targetNode = _nodes.value.find { it.id == targetId }

            if (sourceNode != null && targetNode != null) {
                val newExpression = "${sourceNode.result}${operator}${targetNode.result}"
                
                try {
                    // CORRECTED: Call the new public method and handle potential errors
                    val newResult = Evaluator.evaluate(newExpression)
                    addNode(newExpression, newResult)
                } catch (e: Exception) {
                    // Calculation failed (e.g., division by zero). Do nothing to prevent a crash.
                    e.printStackTrace()
                }
            }
        }
        pendingCombination = null
    }

    fun deleteNode(nodeId: String) {
        _nodes.value = _nodes.value.filter { it.id != nodeId }
    }
}