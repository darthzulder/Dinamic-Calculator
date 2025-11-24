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
        // Primero actualizar el nodo padre
        var updatedNode: CalculationNode? = null
        _nodes.value = _nodes.value.map {
            if (it.id == nodeId) {
                try {
                    val newResult = Evaluator.evaluate(expression)
                    val updated = it.copy(expression = expression, result = newResult)
                    updatedNode = updated
                    updated
                } catch (e: Exception) {
                    // Si la expresión no es válida, mantener el nodo sin cambios
                    e.printStackTrace()
                    it
                }
            } else {
                it
            }
        }
        
        // Si el nodo se actualizó correctamente, actualizar recursivamente todos los nodos hijos
        updatedNode?.let { parentNode ->
            updateChildNodesRecursively(parentNode.id, parentNode.result)
        }
    }
    
    /**
     * Actualiza recursivamente todos los nodos hijos cuando cambia el resultado de un nodo padre.
     * Los nodos hijos tienen expresiones que referencian los resultados de sus padres.
     */
    private fun updateChildNodesRecursively(parentNodeId: String, newParentResult: BigDecimal) {
        // Obtener los nodos actuales (pueden haber sido actualizados en llamadas recursivas anteriores)
        val currentNodes = _nodes.value
        val nodesToUpdate = mutableListOf<Pair<String, CalculationNode>>()
        
        // Encontrar todos los nodos hijos directos que tienen este nodo como padre
        currentNodes.forEach { node ->
            if (node.parentNodeIds.contains(parentNodeId)) {
                // Este nodo es hijo del nodo padre que cambió
                // Reconstruir su expresión usando los resultados actuales de sus padres
                val updatedNode = rebuildChildNodeExpression(node, currentNodes)
                if (updatedNode != null) {
                    nodesToUpdate.add(Pair(node.id, updatedNode))
                }
            }
        }
        
        // Actualizar los nodos hijos encontrados
        if (nodesToUpdate.isNotEmpty()) {
            _nodes.value = _nodes.value.map { node ->
                val update = nodesToUpdate.find { it.first == node.id }
                if (update != null) {
                    update.second
                } else {
                    node
                }
            }
            
            // Recursivamente actualizar los hijos de los nodos actualizados
            // Usar los nodos actualizados de _nodes.value para la siguiente iteración
            nodesToUpdate.forEach { (_, updatedChildNode) ->
                updateChildNodesRecursively(updatedChildNode.id, updatedChildNode.result)
            }
        }
    }
    
    /**
     * Reconstruye la expresión de un nodo hijo usando los resultados actuales de sus nodos padre.
     * La expresión se reconstruye en el formato: "${parent1.result}${operator}${parent2.result}"
     */
    private fun rebuildChildNodeExpression(childNode: CalculationNode, allNodes: List<CalculationNode>): CalculationNode? {
        if (childNode.parentNodeIds.isEmpty()) {
            return null // No es un nodo hijo, no necesita reconstrucción
        }
        
        // Obtener los nodos padre actuales
        val parentNodes = childNode.parentNodeIds.mapNotNull { parentId ->
            allNodes.find { it.id == parentId }
        }
        
        if (parentNodes.size != childNode.parentNodeIds.size) {
            // Algunos padres no se encontraron, mantener el nodo sin cambios
            return null
        }
        
        // Reconstruir la expresión basándose en la expresión original del hijo
        // La expresión original es del formato: "${parent1.result}${operator}${parent2.result}"
        // Necesitamos extraer el operador y reconstruir con los nuevos resultados
        val originalExpression = childNode.expression
        
        // Intentar extraer el operador de la expresión original
        // Buscar operadores comunes: +, -, *, /
        val operators = listOf("+", "-", "*", "/")
        var foundOperator: String? = null
        var operatorIndex = -1
        
        for (operator in operators) {
            val index = originalExpression.indexOf(operator)
            if (index > 0 && index < originalExpression.length - 1) {
                // Verificar que no sea parte de un número negativo al inicio
                if (operator == "-" && index == 0) continue
                foundOperator = operator
                operatorIndex = index
                break
            }
        }
        
        if (foundOperator == null) {
            // No se pudo determinar el operador, intentar detectarlo probando cada uno
            return tryDetectOperatorAndRebuild(childNode, parentNodes)
        }
        
        if (parentNodes.size == 2) {
            // Reconstruir la expresión con los nuevos resultados de los padres
            val newExpression = "${parentNodes[0].result}${foundOperator}${parentNodes[1].result}"
            
            try {
                val newResult = Evaluator.evaluate(newExpression)
                return childNode.copy(expression = newExpression, result = newResult)
            } catch (e: Exception) {
                e.printStackTrace()
                return null
            }
        } else if (parentNodes.size == 1) {
            // Un solo padre: la expresión del hijo debería ser igual al resultado del padre
            return childNode.copy(
                expression = parentNodes[0].result.toString(),
                result = parentNodes[0].result
            )
        }
        
        return null
    }
    
    /**
     * Intenta detectar el operador probando cada uno y reconstruye la expresión.
     * Se usa cuando no se puede extraer el operador directamente de la expresión.
     */
    private fun tryDetectOperatorAndRebuild(
        childNode: CalculationNode,
        parentNodes: List<CalculationNode>
    ): CalculationNode? {
        if (parentNodes.size != 2) {
            return null
        }
        
        val operators = listOf("+", "-", "*", "/")
        
        // Probar cada operador y ver cuál produce un resultado que coincide con el resultado original
        for (operator in operators) {
            try {
                val testExpression = "${parentNodes[0].result}${operator}${parentNodes[1].result}"
                val testResult = Evaluator.evaluate(testExpression)
                // Si el resultado coincide aproximadamente, usar este operador
                // (Tolerancia para errores de punto flotante)
                if ((testResult - childNode.result).abs() < BigDecimal("0.0001")) {
                    return childNode.copy(expression = testExpression, result = testResult)
                }
            } catch (e: Exception) {
                continue
            }
        }
        
        // Si no encontramos coincidencia, usar el operador más común (+) y recalcular
        try {
            val newExpression = "${parentNodes[0].result}+${parentNodes[1].result}"
            val newResult = Evaluator.evaluate(newExpression)
            return childNode.copy(expression = newExpression, result = newResult)
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }
}