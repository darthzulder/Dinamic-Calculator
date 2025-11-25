package com.forz.calculator.fragments.handlers

import android.content.ClipData
import android.content.Context
import android.util.TypedValue
import android.view.View
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.forz.calculator.R
import com.forz.calculator.canvas.CalculationNode
import com.forz.calculator.canvas.CanvasViewModel
import com.forz.calculator.databinding.FragmentMainBinding
import com.forz.calculator.settings.Config
import com.forz.calculator.utils.NumberFormatter
import com.google.android.material.card.MaterialCardView

/**
 * Gestiona el renderizado y actualización de las vistas de nodos en el canvas.
 * Responsable de:
 * - Crear vistas de nodos
 * - Actualizar vistas existentes
 * - Gestionar el ciclo de vida de las vistas
 * - Aplicar ajustes de pan para coordenadas negativas
 */
class NodeRenderingManager(
    private val context: Context,
    private val getBinding: () -> FragmentMainBinding?,
    private val canvasViewModel: CanvasViewModel,
    private val createNodeViewCallback: (CalculationNode) -> View,
    private val getSelectedNode: () -> CalculationNode?
) {
    
    var pendingPanAdjustment: Pair<Float, Float>? = null
    
    fun renderNodes(
        nodes: List<CalculationNode>,
        isDragInProgress: Boolean,
        onNeedsRenderAfterDrag: () -> Unit
    ) {
        val binding = getBinding() ?: return
        val container = binding.canvasContainer ?: return
        
        // No modificar vistas durante un drag activo
        if (isDragInProgress) {
            onNeedsRenderAfterDrag()
            return
        }

        // Aplicar ajuste de pan pendiente
        pendingPanAdjustment?.let { (dx, dy) ->
            binding.zoomableCanvasContainer?.adjustPan(dx, dy)
            pendingPanAdjustment = null
        }
        
        // Solo ocultar el nodo si realmente está siendo arrastrado
        val draggedNodeId = (container.tag as? ClipData)?.getItemAt(0)?.text?.toString()
        
        container.post {
            if (isDragInProgress || getBinding() == null) {
                onNeedsRenderAfterDrag()
                return@post
            }
            
            val currentBinding = getBinding() ?: return@post
            val currentContainer = currentBinding.canvasContainer ?: return@post
            
            // Verificar si necesitamos desplazar los nodos para evitar coordenadas negativas
            handleNodeShifting(nodes)?.let { return@post }
            
            // Renderizar nodos
            val finalNodeViews = updateNodeViews(
                nodes,
                currentContainer,
                draggedNodeId
            )
            
            // Actualizar líneas de conexión
            updateConnectionLines(currentBinding, nodes, finalNodeViews)
            
            // Expandir canvas si es necesario
            expandCanvas(currentBinding, nodes)
        }
    }
    
    private fun handleNodeShifting(nodes: List<CalculationNode>): Unit? {
        val padding = 100f
        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        
        if (nodes.isNotEmpty()) {
            nodes.forEach { 
                minX = minOf(minX, it.positionX)
                minY = minOf(minY, it.positionY)
            }
            
            var shiftX = 0f
            var shiftY = 0f
            
            if (minX < padding) shiftX = padding - minX
            if (minY < padding) shiftY = padding - minY
            
            if (shiftX > 0 || shiftY > 0) {
                pendingPanAdjustment = Pair(shiftX, shiftY)
                canvasViewModel.shiftAllNodes(shiftX, shiftY)
                return Unit // Señal para salir del post
            }
        }
        return null
    }
    
    private fun updateNodeViews(
        nodes: List<CalculationNode>,
        container: android.view.ViewGroup,
        draggedNodeId: String?
    ): MutableMap<String, View> {
        // Obtener vistas existentes
        val existingViews = mutableMapOf<String, View>()
        for (i in 0 until container.childCount) {
            val child = container.getChildAt(i)
            val nodeId = child.tag?.toString()
            if (nodeId != null) {
                existingViews[nodeId] = child
            }
        }
        
        // Remover vistas que ya no existen
        val nodeIds = nodes.map { it.id }.toSet()
        existingViews.forEach { (nodeId, view) ->
            if (!nodeIds.contains(nodeId)) {
                container.removeView(view)
            }
        }
        
        // Asegurar que la vista de líneas esté por debajo
        val binding = getBinding()
        val connectionLinesView = binding?.connectionLinesView
        if (connectionLinesView != null && connectionLinesView.parent == null) {
            container.addView(connectionLinesView, 0)
        }
        
        // Actualizar o crear vistas de nodos
        val finalNodeViews = mutableMapOf<String, View>()
        nodes.forEach { node ->
            val existingView = existingViews[node.id]
            if (existingView != null) {
                updateExistingNodeView(existingView, node, draggedNodeId)
                finalNodeViews[node.id] = existingView
            } else {
                val newView = createNewNodeView(node, draggedNodeId, container)
                finalNodeViews[node.id] = newView
            }
        }
        
        return finalNodeViews
    }
    
    private fun updateExistingNodeView(
        view: View,
        node: CalculationNode,
        draggedNodeId: String?
    ) {
        view.x = node.positionX
        view.y = node.positionY
        
        // Actualizar texto
        val textView = view.findViewById<TextView>(R.id.node_text)
        val formattedResult = NumberFormatter.formatResult(
            node.result,
            Config.numberPrecision,
            Config.maxScientificNotationDigits,
            Config.groupingSeparatorSymbol,
            Config.decimalSeparatorSymbol
        )
        val displayText = if (node.name.isNotEmpty()) {
            "${node.name}: ${node.expression} = $formattedResult"
        } else {
            "${node.expression} = $formattedResult"
        }
        
        if (textView?.text.toString() != displayText) {
            textView?.text = displayText
        }
        
        // Actualizar visibilidad
        view.visibility = if (draggedNodeId != null && node.id == draggedNodeId) {
            View.INVISIBLE
        } else {
            View.VISIBLE
        }
        
        // Actualizar borde de selección
        applySelectionBorder(view as? MaterialCardView, node)
    }
    
    private fun createNewNodeView(
        node: CalculationNode,
        draggedNodeId: String?,
        container: android.view.ViewGroup
    ): View {
        val nodeView = createNodeViewCallback(node)
        nodeView.tag = node.id
        
        nodeView.visibility = if (draggedNodeId != null && node.id == draggedNodeId) {
            View.INVISIBLE
        } else {
            View.VISIBLE
        }
        
        applySelectionBorder(nodeView as? MaterialCardView, node)
        container.addView(nodeView)
        
        return nodeView
    }
    
    private fun applySelectionBorder(cardView: MaterialCardView?, node: CalculationNode) {
        if (cardView != null) {
            val selectedNode = getSelectedNode()
            if (selectedNode != null && node.id == selectedNode.id) {
                val strokeWidth = (4 * context.resources.displayMetrics.density).toInt()
                cardView.strokeWidth = strokeWidth
                val typedValue = TypedValue()
                context.theme.resolveAttribute(
                    com.google.android.material.R.attr.colorPrimary,
                    typedValue,
                    true
                )
                cardView.strokeColor = ContextCompat.getColor(context, typedValue.resourceId)
            } else {
                cardView.strokeWidth = 0
            }
        }
    }
    
    private fun updateConnectionLines(
        binding: FragmentMainBinding,
        nodes: List<CalculationNode>,
        finalNodeViews: Map<String, View>
    ) {
        val connectionLinesView = binding.connectionLinesView
        if (connectionLinesView != null) {
            val connections = canvasViewModel.getConnections()
            connectionLinesView.updateData(nodes, connections, finalNodeViews)
        }
    }
    
    private fun expandCanvas(binding: FragmentMainBinding, nodes: List<CalculationNode>) {
        val zoomableContainer = binding.zoomableCanvasContainer
        zoomableContainer?.post {
            zoomableContainer.expandCanvasForNodes(nodes)
            zoomableContainer.requestLayout()
        }
    }
}
