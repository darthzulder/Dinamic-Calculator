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

    fun addNode(expression: String, result: BigDecimal) {
        val newNode = CalculationNode(
            expression = expression,
            result = result,
            positionX = 150f, 
            positionY = 150f
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
}