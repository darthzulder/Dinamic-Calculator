package com.forz.calculator.canvas

import android.graphics.Color
import androidx.lifecycle.ViewModel
import com.forz.calculator.calculator.Evaluator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.math.BigDecimal

class CanvasViewModel : ViewModel() {

    private val _nodes = MutableStateFlow<List<CalculationNode>>(emptyList())
    val nodes: StateFlow<List<CalculationNode>> = _nodes

    private var pendingCombination: Pair<String, String>? = null
    
    // Colores contrastantes para las conexiones
    private val connectionColors = listOf(
        Color.parseColor("#00D9FF"), // Azul eléctrico
        Color.parseColor("#FF00FF"), // Magenta
        Color.parseColor("#00FF00"), // Verde brillante
        Color.parseColor("#FF6B00"), // Naranja
        Color.parseColor("#FF00AA"), // Rosa
        Color.parseColor("#00FFFF"), // Cyan
        Color.parseColor("#FFD700"), // Dorado
        Color.parseColor("#FF1493"), // Rosa profundo
        Color.parseColor("#00CED1"), // Turquesa oscuro
        Color.parseColor("#FF4500"), // Naranja rojizo
    )
    private var currentColorIndex = 0

    // Constantes para posicionamiento de nodos
    companion object {
        private const val INITIAL_X = 150f
        private const val INITIAL_Y = 150f
        private const val NODE_HEIGHT_WITH_MARGIN = 150f // Altura aproximada del nodo + margen de separación
        private const val X_TOLERANCE = 20f // Tolerancia en píxeles para considerar que dos nodos están en la misma columna X
    }

    fun addNode(
        expression: String, 
        result: BigDecimal, 
        parentNodeIds: List<String> = emptyList(), 
        connectionColor: Int = 0,
        customPosition: Pair<Float, Float>? = null
    ) {
        val (newX, newY) = if (customPosition != null) {
            // Usar posición personalizada (para nodos resultantes de combinaciones)
            customPosition
        } else {
            // Calcular posición automática (para nodos creados normalmente)
            val initialX = INITIAL_X
            val initialY = INITIAL_Y
            
            // Buscar todos los nodos que estén en la misma posición X (misma columna)
            // con una tolerancia para considerar que están alineados verticalmente
            val nodesInSameColumn = _nodes.value.filter { node ->
                kotlin.math.abs(node.positionX - initialX) <= X_TOLERANCE
            }
            
            // Calcular la nueva posición Y
            val calculatedY = if (nodesInSameColumn.isEmpty()) {
                // Si no hay nodos en esta columna, usar la posición inicial
                initialY
            } else {
                // Encontrar el nodo con la posición Y más baja (más abajo)
                val lowestNode = nodesInSameColumn.maxByOrNull { it.positionY }
                // Colocar el nuevo nodo debajo del nodo más bajo
                (lowestNode?.positionY ?: initialY) + NODE_HEIGHT_WITH_MARGIN
            }
            
            Pair(initialX, calculatedY)
        }
        
        val newNode = CalculationNode(
            expression = expression,
            result = result,
            positionX = newX, 
            positionY = newY,
            parentNodeIds = parentNodeIds,
            connectionColor = connectionColor
        )
        _nodes.value = _nodes.value + newNode
    }
    
    private fun getNextConnectionColor(): Int {
        val color = connectionColors[currentColorIndex]
        currentColorIndex = (currentColorIndex + 1) % connectionColors.size
        return color
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
                    // Asignar un color único para esta conexión
                    val connectionColor = getNextConnectionColor()
                    
                    // Calcular la posición del nodo resultante:
                    // 1. Encontrar el nodo más a la derecha de los nodos origen
                    val rightmostNode = if (sourceNode.positionX > targetNode.positionX) {
                        sourceNode
                    } else {
                        targetNode
                    }
                    
                    // 2. Posición Y media entre los dos nodos origen
                    val midY = (sourceNode.positionY + targetNode.positionY) / 2f
                    
                    // 3. Colocar el nodo resultante a la derecha del nodo más a la derecha
                    // Asumimos que el ancho promedio de un nodo es aproximadamente NODE_HEIGHT_WITH_MARGIN
                    val offsetX = NODE_HEIGHT_WITH_MARGIN
                    val resultX = rightmostNode.positionX + offsetX
                    val resultY = midY
                    
                    // Crear el nuevo nodo con los nodos padre, el color de conexión y la posición calculada
                    addNode(
                        newExpression, 
                        newResult, 
                        listOf(sourceId, targetId), 
                        connectionColor,
                        Pair(resultX, resultY)
                    )
                } catch (e: Exception) {
                    // Calculation failed (e.g., division by zero). Do nothing to prevent a crash.
                    e.printStackTrace()
                }
            }
        }
        pendingCombination = null
    }
    
    fun getConnections(): List<NodeConnection> {
        val connections = mutableListOf<NodeConnection>()
        _nodes.value.forEach { node ->
            if (node.parentNodeIds.isNotEmpty() && node.connectionColor != 0) {
                node.parentNodeIds.forEach { parentId ->
                    connections.add(NodeConnection(parentId, node.id, node.connectionColor))
                }
            }
        }
        return connections
    }

    fun deleteNode(nodeId: String) {
        // Eliminar el nodo y también todos los nodos que dependen de él (nodos hijos)
        val nodesToDelete = mutableSetOf(nodeId)
        var changed = true
        
        // Encontrar recursivamente todos los nodos que dependen del nodo eliminado
        while (changed) {
            changed = false
            val newNodesToDelete = _nodes.value.filter { node ->
                node.parentNodeIds.any { parentId -> nodesToDelete.contains(parentId) }
            }.map { it.id }
            
            newNodesToDelete.forEach { id ->
                if (nodesToDelete.add(id)) {
                    changed = true
                }
            }
        }
        
        // Eliminar todos los nodos encontrados
        _nodes.value = _nodes.value.filter { !nodesToDelete.contains(it.id) }
    }

    fun updateNodeName(nodeId: String, name: String) {
        _nodes.value = _nodes.value.map {
            if (it.id == nodeId) {
                it.copy(name = name)
            } else {
                it
            }
        }
    }

    fun updateNodeDescription(nodeId: String, description: String) {
        _nodes.value = _nodes.value.map {
            if (it.id == nodeId) {
                it.copy(description = description)
            } else {
                it
            }
        }
    }

    fun updateNodeExpression(nodeId: String, expression: String) {
        _nodes.value = _nodes.value.map {
            if (it.id == nodeId) {
                try {
                    val newResult = Evaluator.evaluate(expression)
                    it.copy(expression = expression, result = newResult)
                } catch (e: Exception) {
                    // Si la expresión no es válida, mantener el nodo sin cambios
                    e.printStackTrace()
                    it
                }
            } else {
                it
            }
        }
    }
}