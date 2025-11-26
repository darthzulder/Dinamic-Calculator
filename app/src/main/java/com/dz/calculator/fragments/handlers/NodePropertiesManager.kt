package com.dz.calculator.fragments.handlers

import android.content.Context
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import androidx.fragment.app.Fragment
import androidx.viewpager2.widget.ViewPager2
import com.dz.calculator.R
import com.dz.calculator.canvas.CalculationNode
import com.dz.calculator.canvas.CanvasViewModel
import com.dz.calculator.databinding.FragmentMainBinding

/**
 * Gestiona el panel de propiedades de nodos.
 * Responsable de:
 * - Mostrar/ocultar el panel de propiedades
 * - Sincronizar ediciones con el ViewModel
 * - Gestionar el teclado del sistema vs el teclado de calculadora
 */
class NodePropertiesManager(
    private val fragment: Fragment,
    private val getBinding: () -> FragmentMainBinding?,
    private val canvasViewModel: CanvasViewModel,
    private val onRenderRequested: () -> Unit,
    private val getSelectedNode: () -> CalculationNode?,
    private val setSelectedNode: (CalculationNode?) -> Unit
) {
    
    private var calculatorKeyboardSyncListener: TextWatcher? = null
    
    fun setupNodePropertiesPanel() {
        val binding = getBinding() ?: return
        
        // Configurar callback para clicks en el canvas
        binding.zoomableCanvasContainer?.onCanvasClick = { _, _ ->
            val panelContainer = binding.nodePropertiesPanelContainer
            if (getSelectedNode() != null && panelContainer != null && panelContainer.visibility == View.VISIBLE) {
                hideNodePropertiesPanel()
            }
        }
        
        // Prevenir que los clicks dentro del panel cierren el panel
        binding.nodePropertiesPanelContainer?.setOnTouchListener { _, _ -> true }
    }
    
    fun showNodePropertiesPanel(node: CalculationNode) {
        val binding = getBinding() ?: return
        val panelContainer = binding.nodePropertiesPanelContainer ?: return
        
        // Obtener el nodo actualizado desde el ViewModel
        val currentNode = canvasViewModel.nodes.value.find { it.id == node.id } ?: node
        setSelectedNode(currentNode)
        
        // Determinar si el nodo es hijo
        val isChildNode = currentNode.parentNodeIds.isNotEmpty()
        
        // Mostrar el panel
        panelContainer.visibility = View.VISIBLE
        
        // Cargar la expresión del nodo
        binding.expressionEditText.setText(currentNode.expression)
        binding.expressionEditText.setSelection(currentNode.expression.length)
        
        // Configurar edición según tipo de nodo
        configureExpressionEditing(binding, isChildNode)
        
        // Cargar valores en el panel
        setupPanelFields(panelContainer, currentNode, isChildNode, binding)
        
        // Enfocar el ExpressionEditText si no es nodo hijo
        if (!isChildNode) {
            binding.expressionEditText.requestFocus()
        }
        
        // Re-renderizar para mostrar borde de selección
        onRenderRequested()
    }
    
    fun hideNodePropertiesPanel() {
        val binding = getBinding() ?: return
        val panelContainer = binding.nodePropertiesPanelContainer ?: return
        
        // Ocultar el panel
        panelContainer.visibility = View.GONE
        
        // Restaurar el estado del ExpressionEditText
        binding.expressionEditText.isEnabled = true
        binding.expressionEditText.isFocusable = true
        binding.expressionEditText.isClickable = true
        binding.expressionEditText.alpha = 1.0f
        
        // Mostrar el teclado de calculadora
        binding.root.findViewById<ViewPager2>(R.id.keyboard_container)?.visibility = View.VISIBLE
        
        // Ocultar teclado del sistema
        val view = fragment.requireActivity().currentFocus ?: binding.root
        hideSystemKeyboard(view)
        
        // Limpiar foco de los campos de edición
        panelContainer.findViewById<EditText>(R.id.node_name_edit)?.clearFocus()
        panelContainer.findViewById<EditText>(R.id.node_description_edit)?.clearFocus()
        
        // Desconectar el teclado de calculadora
        disconnectCalculatorKeyboard()
        
        setSelectedNode(null)
        
        // Re-renderizar para actualizar borde de selección
        onRenderRequested()
    }
    
    private fun configureExpressionEditing(binding: FragmentMainBinding, isChildNode: Boolean) {
        if (isChildNode) {
            // Deshabilitar edición para nodos hijos
            binding.expressionEditText.isEnabled = false
            binding.expressionEditText.isFocusable = false
            binding.expressionEditText.isClickable = false
            binding.expressionEditText.alpha = 0.6f
            binding.root.findViewById<ViewPager2>(R.id.keyboard_container)?.visibility = View.GONE
        } else {
            // Habilitar edición para nodos normales
            binding.expressionEditText.isEnabled = true
            binding.expressionEditText.isFocusable = true
            binding.expressionEditText.isClickable = true
            binding.expressionEditText.alpha = 1.0f
            binding.root.findViewById<ViewPager2>(R.id.keyboard_container)?.visibility = View.VISIBLE
            connectCalculatorKeyboard()
        }
    }
    
    private fun setupPanelFields(
        panelContainer: View,
        node: CalculationNode,
        isChildNode: Boolean,
        binding: FragmentMainBinding
    ) {
        val nameEdit = panelContainer.findViewById<EditText>(R.id.node_name_edit)
        val descriptionEdit = panelContainer.findViewById<EditText>(R.id.node_description_edit)
        
        nameEdit?.setText(node.name)
        descriptionEdit?.setText(node.description)
        
        // Configurar listeners para actualizar el ViewModel
        setupFieldListener(nameEdit) { text ->
            getSelectedNode()?.let { canvasViewModel.updateNodeName(it.id, text) }
        }
        
        setupFieldListener(descriptionEdit) { text ->
            getSelectedNode()?.let { canvasViewModel.updateNodeDescription(it.id, text) }
        }
        
        // Configurar teclado del sistema
        nameEdit?.showSoftInputOnFocus = true
        descriptionEdit?.showSoftInputOnFocus = true
        
        // Configurar focus listeners
        setupFocusListeners(nameEdit, descriptionEdit, isChildNode, binding, panelContainer)
    }
    
    private fun setupFieldListener(editText: EditText?, onTextChanged: (String) -> Unit) {
        editText?.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                onTextChanged(s?.toString() ?: "")
            }
        })
    }
    
    private fun setupFocusListeners(
        nameEdit: EditText?,
        descriptionEdit: EditText?,
        isChildNode: Boolean,
        binding: FragmentMainBinding,
        panelContainer: View
    ) {
        nameEdit?.setOnFocusChangeListener { view, hasFocus ->
            if (hasFocus) {
                showSystemKeyboard(view)
                if (!isChildNode) {
                    binding.root.findViewById<ViewPager2>(R.id.keyboard_container)?.visibility = View.GONE
                }
            } else {
                val descriptionEditCheck = panelContainer.findViewById<EditText>(R.id.node_description_edit)
                if (descriptionEditCheck?.hasFocus() != true && !isChildNode) {
                    binding.root.findViewById<ViewPager2>(R.id.keyboard_container)?.visibility = View.VISIBLE
                }
            }
        }
        
        descriptionEdit?.setOnFocusChangeListener { view, hasFocus ->
            if (hasFocus) {
                showSystemKeyboard(view)
                if (!isChildNode) {
                    binding.root.findViewById<ViewPager2>(R.id.keyboard_container)?.visibility = View.GONE
                }
            } else {
                val nameEditCheck = panelContainer.findViewById<EditText>(R.id.node_name_edit)
                if (nameEditCheck?.hasFocus() != true && !isChildNode) {
                    binding.root.findViewById<ViewPager2>(R.id.keyboard_container)?.visibility = View.VISIBLE
                }
            }
        }
    }
    
    private fun connectCalculatorKeyboard() {
        val binding = getBinding() ?: return
        val mainExpressionEdit = binding.expressionEditText
        
        // Remover listener anterior si existe
        calculatorKeyboardSyncListener?.let {
            mainExpressionEdit.removeTextChangedListener(it)
        }
        
        // Agregar listener para actualizar el nodo
        calculatorKeyboardSyncListener = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                getSelectedNode()?.let { node ->
                    val text = s?.toString() ?: ""
                    if (text.isNotEmpty()) {
                        canvasViewModel.updateNodeExpression(node.id, text)
                    }
                }
            }
        }
        mainExpressionEdit.addTextChangedListener(calculatorKeyboardSyncListener)
    }
    
    private fun disconnectCalculatorKeyboard() {
        val binding = getBinding() ?: return
        val mainExpressionEdit = binding.expressionEditText
        
        calculatorKeyboardSyncListener?.let {
            mainExpressionEdit.removeTextChangedListener(it)
            calculatorKeyboardSyncListener = null
        }
    }
    
    private fun showSystemKeyboard(view: View) {
        val imm = fragment.requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        view.requestFocus()
        imm.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT)
    }
    
    internal fun hideSystemKeyboard(view: View) {
        val imm = fragment.requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(view.windowToken, 0)
    }
}
