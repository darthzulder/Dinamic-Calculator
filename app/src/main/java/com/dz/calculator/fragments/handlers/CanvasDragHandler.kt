package com.dz.calculator.fragments.handlers

import android.content.ClipDescription
import android.view.DragEvent
import android.view.View
import com.dz.calculator.canvas.CanvasViewModel
import com.dz.calculator.canvas.ZoomableCanvasContainer
import com.dz.calculator.databinding.FragmentMainBinding
import com.google.android.material.card.MaterialCardView

/**
 * Maneja los eventos de drag & drop en el canvas.
 * Responsable de:
 * - Iniciar/finalizar operaciones de drag
 * - Calcular nuevas posiciones de nodos
 * - Actualizar el estado visual durante el drag
 */
class CanvasDragHandler(
    private val getBinding: () -> FragmentMainBinding?,
    private val canvasViewModel: CanvasViewModel,
    private val onDragStateChanged: (isDragging: Boolean) -> Unit,
    private val onRenderRequested: () -> Unit,
    private val getSelectedNode: () -> Any?,
    private val hideNodePropertiesPanel: () -> Unit
) {
    
    fun setupCanvasDragListener() {
        val binding = getBinding() ?: return
        
        binding.zoomableCanvasContainer?.setOnDragListener { view, event ->
            val container = view as? ZoomableCanvasContainer ?: return@setOnDragListener false
            
            when (event.action) {
                DragEvent.ACTION_DRAG_STARTED -> handleDragStarted(binding, event)
                DragEvent.ACTION_DROP -> handleDrop(binding, container, event)
                DragEvent.ACTION_DRAG_ENDED -> handleDragEnded(binding, container)
                else -> true
            }
        }
    }
    
    private fun handleDragStarted(binding: FragmentMainBinding, event: DragEvent): Boolean {
        // Deseleccionar el nodo actualmente seleccionado si hay uno
        if (getSelectedNode() != null) {
            // Remover el borde de selección de todas las vistas de nodos
            val innerContainer = binding.canvasContainer
            innerContainer?.let { container ->
                for (i in 0 until container.childCount) {
                    val child = container.getChildAt(i)
                    val cardView = child as? MaterialCardView
                    cardView?.strokeWidth = 0
                }
            }
            hideNodePropertiesPanel()
        }
        
        onDragStateChanged(true)
        return event.clipDescription?.hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN) ?: false
    }
    
    private fun handleDrop(
        binding: FragmentMainBinding,
        container: ZoomableCanvasContainer,
        event: DragEvent
    ): Boolean {
        val clipData = event.clipData ?: return false
        if (clipData.itemCount > 0) {
            val item = clipData.getItemAt(0)
            val nodeId = item.text?.toString() ?: return false
            
            // Buscar la vista original en el contenedor interno
            val innerContainer = binding.canvasContainer
            val draggedView = innerContainer?.findViewWithTag<View>(nodeId)
            
            val widthOffset = (draggedView?.width ?: 0) / 2f
            val heightOffset = (draggedView?.height ?: 0) / 2f

            val touchX = event.x
            val touchY = event.y
            
            // Convertir coordenadas de pantalla a coordenadas del canvas
            val canvasPoint = container.screenToCanvas(touchX, touchY)
            
            val newX = canvasPoint.x - widthOffset
            val newY = canvasPoint.y - heightOffset

            canvasViewModel.updateNodePosition(nodeId, newX, newY)
        }
        
        // Limpiar el tag del contenedor después de soltar en el canvas
        binding.canvasContainer?.tag = null
        return true
    }
    
    private fun handleDragEnded(
        binding: FragmentMainBinding,
        container: ZoomableCanvasContainer
    ): Boolean {
        onDragStateChanged(false)
        binding.trashIconOverlay?.visibility = View.GONE
        binding.canvasContainer?.tag = null
        
        // Re-renderizar después del drag
        container.post {
            if (getBinding() != null) {
                onRenderRequested()
            }
        }
        return true
    }
}
