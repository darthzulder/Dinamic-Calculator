package com.dz.calculator.fragments.handlers

import android.content.ClipDescription
import android.view.DragEvent
import android.view.View
import androidx.viewpager2.widget.ViewPager2
import com.dz.calculator.R
import com.dz.calculator.canvas.CanvasViewModel
import com.dz.calculator.databinding.FragmentMainBinding

/**
 * Maneja el drag & drop sobre el área del teclado (zona de eliminación).
 * Responsable de:
 * - Mostrar/ocultar el icono de basura
 * - Eliminar nodos cuando se sueltan sobre el teclado
 */
class KeyboardDragHandler(
    private val getBinding: () -> FragmentMainBinding?,
    private val canvasViewModel: CanvasViewModel
) {
    
    fun setupKeyboardDragListener() {
        val binding = getBinding() ?: return
        val keyboardContainer = binding.root.findViewById<ViewPager2>(R.id.keyboard_container)
        
        keyboardContainer?.setOnDragListener { _, event ->
            val b = getBinding() ?: return@setOnDragListener false
            
            when (event.action) {
                DragEvent.ACTION_DRAG_STARTED -> handleDragStarted(b, event)
                DragEvent.ACTION_DRAG_ENTERED -> true
                DragEvent.ACTION_DRAG_EXITED -> true
                DragEvent.ACTION_DROP -> handleDrop(b, event)
                DragEvent.ACTION_DRAG_ENDED -> handleDragEnded(b)
                else -> true
            }
        }
    }
    
    private fun handleDragStarted(binding: FragmentMainBinding, event: DragEvent): Boolean {
        // Verificar si es un nodo el que se está arrastrando
        val isNodeDrag = event.clipDescription?.hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN) ?: false
        if (isNodeDrag) {
            // Mostrar el icono de basura
            binding.trashIconOverlay?.visibility = View.VISIBLE
        }
        return isNodeDrag
    }
    
    private fun handleDrop(binding: FragmentMainBinding, event: DragEvent): Boolean {
        // Eliminar el nodo si se suelta sobre el teclado
        val clipData = event.clipData
        if (clipData != null && clipData.itemCount > 0) {
            val nodeId = clipData.getItemAt(0).text?.toString()
            if (nodeId != null) {
                canvasViewModel.deleteNode(nodeId)
            }
        }
        // Ocultar el icono de basura
        binding.trashIconOverlay?.visibility = View.GONE
        return true
    }
    
    private fun handleDragEnded(binding: FragmentMainBinding): Boolean {
        // Ocultar el icono de basura cuando termine el drag
        binding.trashIconOverlay?.visibility = View.GONE
        return true
    }
}
