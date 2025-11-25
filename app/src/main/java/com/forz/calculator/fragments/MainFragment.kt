package com.forz.calculator.fragments

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipDescription
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.DragEvent
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.util.TypedValue
import android.widget.EditText
import android.widget.PopupMenu
import android.widget.TextView
import androidx.cardview.widget.CardView
import com.google.android.material.card.MaterialCardView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.forz.calculator.AboutActivity
import com.forz.calculator.App
import com.forz.calculator.MainActivity
import com.forz.calculator.OnMainActivityListener
import com.forz.calculator.R
import com.forz.calculator.calculator.CalculatorViewModel
import com.forz.calculator.calculator.DefaultOperator
import com.forz.calculator.calculator.Evaluator
import com.forz.calculator.canvas.CalculationNode
import com.forz.calculator.canvas.CanvasViewModel
import com.forz.calculator.databinding.FragmentMainBinding
import com.forz.calculator.expression.ExpressionViewModel
import com.forz.calculator.expression.ExpressionViewModel.cursorPositionStart
import com.forz.calculator.expression.ExpressionViewModel.expression
import com.forz.calculator.expression.ExpressionViewModel.oldExpression
import com.forz.calculator.history.HistoryService
import com.forz.calculator.settings.Config
import com.forz.calculator.settings.SettingsActivity
import com.forz.calculator.utils.HapticAndSound
import com.forz.calculator.utils.InsertInExpression
import com.forz.calculator.utils.NumberFormatter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import java.math.BigDecimal

@Suppress("DEPRECATION")
class MainFragment : Fragment(),
    OnMainActivityListener,
    CalculatorFragment.OnButtonClickListener {

    private var _binding: FragmentMainBinding? = null
    private val binding get() = _binding!!

    private val hapticAndSound: HapticAndSound by lazy { HapticAndSound(requireContext(), emptyArray()) }
    private val canvasViewModel: CanvasViewModel by viewModels()

    private val historyService: HistoryService
        get() = (requireContext().applicationContext as App).historyService

    // Flag para rastrear si hay un drag en progreso
    private var isDragInProgress = false
    // Flag para indicar que se necesita un re-renderizado después del drag
    private var needsRenderAfterDrag = false
    // Nodo actualmente seleccionado para edición
    private var selectedNode: CalculationNode? = null
    // Listener para sincronizar el teclado de calculadora con el panel de expresión
    private var calculatorKeyboardSyncListener: TextWatcher? = null
    // Variable para almacenar el ajuste de pan pendiente después de un shift de nodos
private var pendingPanAdjustment: Pair<Float, Float>? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMainBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.expressionEditText.showSoftInputOnFocus = false
        binding.expressionEditText.requestFocus()
        
        // Ocultar teclado del teléfono cuando ExpressionEditText tiene foco
        binding.expressionEditText.setOnFocusChangeListener { view, hasFocus ->
            if (hasFocus) {
                hideSystemKeyboard(view)
            }
        }

        // Restaurar nodos guardados antes de configurar los observadores
        canvasViewModel.restoreNodes(requireContext())

        setupToolbar()
        setupExpressionInput()
        observeViewModels()
        setupCanvasDragListener()
        setupKeyboardDragListener()
        setupNodePropertiesPanel()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun setupToolbar() {
        binding.toolbar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.settings -> {
                    val intent = Intent(requireActivity(), SettingsActivity::class.java)
                    startActivityForResult(intent, MainActivity.REQUEST_CODE_CHILD)
                    true
                }
                R.id.about -> {
                    val intent = Intent(requireContext(), AboutActivity::class.java)
                    startActivity(intent)
                    true
                }
                else -> false
            }
        }
    }

    private fun setupExpressionInput() {
        binding.expressionEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val b = _binding ?: return
                Evaluator.setResultTextView(b.expressionEditText, b.resultText, ExpressionViewModel.isSelected.value ?: false, requireContext())
            }
        })
    }

    private fun observeViewModels() {
        ExpressionViewModel.isSelected.observe(viewLifecycleOwner) { isSelected ->
            val b = _binding ?: return@observe
            Evaluator.setResultTextView(b.expressionEditText, b.resultText, isSelected, requireContext())
        }

        CalculatorViewModel.isDegreeModActivated.observe(viewLifecycleOwner) { isDegreeModActivated ->
            val b = _binding ?: return@observe
            Evaluator.setResultTextView(b.expressionEditText, b.resultText, ExpressionViewModel.isSelected.value ?: false, requireContext())
        }

        canvasViewModel.nodes.onEach { nodes ->
            // No renderizar durante un drag activo para evitar ConcurrentModificationException
            if (!isDragInProgress) {
                renderNodes(nodes)
            } else {
                // Marcar que se necesita renderizar después del drag
                needsRenderAfterDrag = true
            }
        }.launchIn(viewLifecycleOwner.lifecycleScope)
    }

    private fun setupCanvasDragListener() {
        val b = _binding ?: return
    
        // ERROR 1 CORREGIDO: Usamos ?. (safe call) para asignar el listener
        b.zoomableCanvasContainer?.setOnDragListener { view, event ->
            
            // TRUCO PRO: Castemos la 'view' que llega al listener. 
            // Como el listener está puesto en el ZoomableCanvasContainer, 'view' ES el contenedor.
            // Esto evita tener que llamar a b.zoomableCanvasContainer? a cada rato dentro del código.
            val container = view as? com.forz.calculator.canvas.ZoomableCanvasContainer 
                ?: return@setOnDragListener false
    
            when (event.action) {
                DragEvent.ACTION_DRAG_STARTED -> {
                    // Deseleccionar el nodo actualmente seleccionado si hay uno
                    if (selectedNode != null) {
                        // Remover el borde de selección inmediatamente de todas las vistas de nodos
                        val innerContainer = b.canvasContainer
                        innerContainer?.let { container ->
                            for (i in 0 until container.childCount) {
                                val child = container.getChildAt(i)
                                val cardView = child as? MaterialCardView
                                if (cardView != null) {
                                    cardView.strokeWidth = 0
                                }
                            }
                        }
                        hideNodePropertiesPanel()
                    }
                    isDragInProgress = true
                    event.clipDescription?.hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN) ?: false
                }
                DragEvent.ACTION_DROP -> {
                    val clipData = event.clipData ?: return@setOnDragListener false
                    if (clipData.itemCount > 0) {
                        val item = clipData.getItemAt(0)
                        val nodeId = item.text?.toString() ?: return@setOnDragListener false
                        
                        // Buscamos la vista original en el contenedor INTERNO (canvas_container)
                        // Nota: b.canvasContainer también puede ser nullable en el binding, usamos ?.
                        val innerContainer = b.canvasContainer
                        val draggedView = innerContainer?.findViewWithTag<View>(nodeId)
                        
                        val widthOffset = (draggedView?.width ?: 0) / 2f
                        val heightOffset = (draggedView?.height ?: 0) / 2f
    
                        val touchX = event.x
                        val touchY = event.y
                        
                        // ERROR 2 CORREGIDO: Usamos la variable local 'container' que ya sabemos que no es nula
                        // para llamar a screenToCanvas.
                        val canvasPoint = container.screenToCanvas(touchX, touchY)
                        
                        val newX = canvasPoint.x - widthOffset
                        val newY = canvasPoint.y - heightOffset
    
                        canvasViewModel.updateNodePosition(nodeId, newX, newY)
                    }
                    // Limpiar el tag del contenedor después de soltar en el canvas
                    b.canvasContainer?.tag = null
                    true
                }
                DragEvent.ACTION_DRAG_ENDED -> {
                    isDragInProgress = false
                    b.trashIconOverlay?.visibility = View.GONE
                    // Limpiar el tag del contenedor cuando termine el drag
                    b.canvasContainer?.tag = null
                    
                    // ERROR 3 CORREGIDO: Usamos la variable local 'container' o b.zoomableCanvasContainer?.post
                    container.post {
                        // Verificamos binding nuevamente dentro del post por seguridad
                        if (_binding != null) {
                             canvasViewModel.nodes.value.let { renderNodes(it) }
                        }
                    }
                    true
                }
                else -> true
            }
        }
    }

    private fun setupKeyboardDragListener() {
        binding.keyboardContainer?.setOnDragListener { _, event ->
            val b = _binding ?: return@setOnDragListener false
            when (event.action) {
                DragEvent.ACTION_DRAG_STARTED -> {
                    // Verificar si es un nodo el que se está arrastrando
                    val isNodeDrag = event.clipDescription?.hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN) ?: false
                    if (isNodeDrag) {
                        // Mostrar el icono de basura
                        b.trashIconOverlay?.visibility = View.VISIBLE
                    }
                    isNodeDrag
                }
                DragEvent.ACTION_DRAG_ENTERED -> {
                    // El nodo entró al área del teclado
                    true
                }
                DragEvent.ACTION_DRAG_EXITED -> {
                    // El nodo salió del área del teclado (pero el drag sigue activo)
                    true
                }
                DragEvent.ACTION_DROP -> {
                    // Eliminar el nodo si se suelta sobre el teclado
                    val clipData = event.clipData
                    if (clipData != null && clipData.itemCount > 0) {
                        val nodeId = clipData.getItemAt(0).text?.toString()
                        if (nodeId != null) {
                            canvasViewModel.deleteNode(nodeId)
                        }
                    }
                    // Ocultar el icono de basura
                    b.trashIconOverlay?.visibility = View.GONE
                    true
                }
                DragEvent.ACTION_DRAG_ENDED -> {
                    // Ocultar el icono de basura cuando termine el drag
                    b.trashIconOverlay?.visibility = View.GONE
                    true
                }
                else -> true
            }
        }
    }

    private fun renderNodes(nodes: List<CalculationNode>) {
        val b = _binding ?: return
        val container = b.canvasContainer ?: return
        
        // No modificar vistas durante un drag activo
        if (isDragInProgress) {
            needsRenderAfterDrag = true
            return
        }

        // 1. APLICAR AJUSTE DE PAN PENDIENTE (Nuevo)
        // Esto compensa visualmente el desplazamiento de coordenadas
        pendingPanAdjustment?.let { (dx, dy) ->
            b.zoomableCanvasContainer?.adjustPan(dx, dy)
            pendingPanAdjustment = null
        }
        
        // Solo ocultar el nodo si realmente está siendo arrastrado (tag existe)
        val draggedNodeId = (container.tag as? ClipData)?.getItemAt(0)?.text?.toString()
        
        // Usar post() para asegurar que las modificaciones ocurran después de cualquier evento de drag
        container.post {
            if (isDragInProgress || _binding == null) {
                needsRenderAfterDrag = true
                return@post
            }
            
            val currentBinding = _binding ?: return@post
            val currentContainer = currentBinding.canvasContainer ?: return@post
            
            // 2. LÓGICA DE DESPLAZAMIENTO (SHIFT) (Nuevo)
            // Verificar si necesitamos desplazar los nodos para evitar coordenadas negativas
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
                
                // Si algún nodo está muy cerca del borde izquierdo o superior (o en negativo)
                if (minX < padding) shiftX = padding - minX
                if (minY < padding) shiftY = padding - minY
                
                if (shiftX > 0 || shiftY > 0) {
                    // Guardar el ajuste visual para el próximo ciclo
                    pendingPanAdjustment = Pair(shiftX, shiftY)
                    // Desplazar las coordenadas de todos los nodos
                    canvasViewModel.shiftAllNodes(shiftX, shiftY)
                    // Salimos y esperamos al próximo render con las nuevas coordenadas
                    return@post
                }
            }

            // --- A PARTIR DE AQUÍ ES EL CÓDIGO ORIGINAL DE RENDERIZADO ---
            
            // Obtener vistas existentes para reutilizar cuando sea posible
            val existingViews = mutableMapOf<String, View>()
            for (i in 0 until currentContainer.childCount) {
                val child = currentContainer.getChildAt(i)
                val nodeId = child.tag?.toString()
                if (nodeId != null) {
                    existingViews[nodeId] = child
                }
            }
            
            // Remover vistas que ya no existen en la lista de nodos
            val nodeIds = nodes.map { it.id }.toSet()
            existingViews.forEach { (nodeId, view) ->
                if (!nodeIds.contains(nodeId)) {
                    currentContainer.removeView(view)
                }
            }
            
            // Asegurar que la vista de líneas esté por debajo de los nodos
            val connectionLinesView = currentBinding.connectionLinesView
            if (connectionLinesView != null && connectionLinesView.parent == null) {
                currentContainer.addView(connectionLinesView, 0)
            }
            
            // Agregar o actualizar vistas para cada nodo
            val finalNodeViews = mutableMapOf<String, View>()
            nodes.forEach { node ->
                val existingView = existingViews[node.id]
                if (existingView != null) {
                    // Actualizar vista existente
                    existingView.x = node.positionX
                    existingView.y = node.positionY
                    val textView = existingView.findViewById<TextView>(R.id.node_text)
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
                    textView?.text = displayText
                    
                    if (draggedNodeId != null && node.id == draggedNodeId) {
                        existingView.visibility = View.INVISIBLE
                    } else {
                        existingView.visibility = View.VISIBLE
                    }
                    
                    val cardView = existingView as? MaterialCardView
                    if (cardView != null) {
                        if (selectedNode != null && node.id == selectedNode!!.id) {
                            val strokeWidth = (4 * requireContext().resources.displayMetrics.density).toInt()
                            cardView.strokeWidth = strokeWidth
                            val typedValue = TypedValue()
                            requireContext().theme.resolveAttribute(com.google.android.material.R.attr.colorPrimary, typedValue, true)
                            cardView.strokeColor = ContextCompat.getColor(requireContext(), typedValue.resourceId)
                        } else {
                            cardView.strokeWidth = 0
                        }
                    }
                    finalNodeViews[node.id] = existingView
                } else {
                    // Crear nueva vista
                    val nodeView = createNodeView(node)
                    nodeView.tag = node.id
                    if (draggedNodeId != null && node.id == draggedNodeId) {
                        nodeView.visibility = View.INVISIBLE
                    } else {
                        nodeView.visibility = View.VISIBLE
                    }
                    
                    val cardView = nodeView as? MaterialCardView
                    if (cardView != null && selectedNode != null && node.id == selectedNode!!.id) {
                        val strokeWidth = (4 * requireContext().resources.displayMetrics.density).toInt()
                        cardView.strokeWidth = strokeWidth
                        val typedValue = TypedValue()
                        requireContext().theme.resolveAttribute(com.google.android.material.R.attr.colorPrimary, typedValue, true)
                        cardView.strokeColor = ContextCompat.getColor(requireContext(), typedValue.resourceId)
                    }
                    currentContainer.addView(nodeView)
                    finalNodeViews[node.id] = nodeView
                }
            }
            
            // Actualizar la vista de líneas de conexión
            if (connectionLinesView != null) {
                val connections = canvasViewModel.getConnections()
                connectionLinesView.updateData(nodes, connections, finalNodeViews)
            }
            
            // Expandir el canvas para que pueda contener todos los nodos
            val zoomableContainer = currentBinding.zoomableCanvasContainer
            if (zoomableContainer != null) {
                zoomableContainer.post {
                    zoomableContainer.expandCanvasForNodes(nodes)
                    zoomableContainer.requestLayout()
                }
            }
        }
    }

    @SuppressLint("SetTextI18n", "ClickableViewAccessibility")
    private fun createNodeView(node: CalculationNode): View {
        val inflater = LayoutInflater.from(requireContext())

        val b = _binding ?: return View(requireContext())
        val nodeView = inflater.inflate(R.layout.node_view, binding.canvasContainer, false)

        nodeView.x = node.positionX
        nodeView.y = node.positionY

        val textView = nodeView.findViewById<TextView>(R.id.node_text)
        if (textView != null) {
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
            textView.text = displayText
        } else {
            // Log para debugging
            android.util.Log.e("MainFragment", "TextView with id 'node_text' not found in node_view.xml")
        }

        nodeView.setOnClickListener {
            showNodePropertiesPanel(node)
        }

        nodeView.setOnLongClickListener { view ->
            // Deseleccionar el nodo actualmente seleccionado si hay uno
            if (selectedNode != null) {
                // Remover el borde de selección inmediatamente de todas las vistas de nodos
                val container = b.canvasContainer ?: return@setOnLongClickListener false
                for (i in 0 until container.childCount) {
                    val child = container.getChildAt(i)
                    val cardView = child as? MaterialCardView
                    if (cardView != null) {
                        cardView.strokeWidth = 0
                    }
                }
                hideNodePropertiesPanel()
            }
            
            val item = ClipData.Item(node.id)
            val mimeTypes = arrayOf(ClipDescription.MIMETYPE_TEXT_PLAIN)
            val clipData = ClipData(node.id, mimeTypes, item)
            b.canvasContainer?.tag = clipData

            // Marcar que un drag está a punto de comenzar
            isDragInProgress = true

            val dragShadow = View.DragShadowBuilder(view)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                view.startDragAndDrop(clipData, dragShadow, view, 0)
            } else {
                view.startDrag(clipData, dragShadow, view, 0)
            }
            view.visibility = View.INVISIBLE
            true
        }

        nodeView.setOnDragListener(nodeDragListener)

        return nodeView
    }

    private val nodeDragListener = View.OnDragListener { view, event ->
        val b = _binding ?: return@OnDragListener false
        val targetCard = view as? MaterialCardView ?: return@OnDragListener false
        when (event.action) {
            DragEvent.ACTION_DRAG_STARTED -> true
            DragEvent.ACTION_DRAG_ENTERED -> {
                targetCard.setCardBackgroundColor(ContextCompat.getColor(requireContext(), R.color.md_theme_light_tertiaryContainer))
                true
            }
            DragEvent.ACTION_DRAG_EXITED -> {
                targetCard.setCardBackgroundColor(ContextCompat.getColor(requireContext(), R.color.md_theme_light_secondaryContainer))
                true
            }
            DragEvent.ACTION_DROP -> {
                val clipData = event.clipData
                if (clipData != null && clipData.itemCount > 0) {
                    val sourceNodeId = clipData.getItemAt(0).text?.toString()
                    val targetNodeId = targetCard.tag?.toString()
                    
                    if (sourceNodeId != null && targetNodeId != null && sourceNodeId != targetNodeId) {
                        canvasViewModel.setPendingCombination(sourceNodeId, targetNodeId)
                        showOperationsMenu(targetCard)
                    }
                }
                // Limpiar el tag del contenedor después de soltar
                b.canvasContainer?.tag = null
                true
            }
            DragEvent.ACTION_DRAG_ENDED -> {
                targetCard.setCardBackgroundColor(ContextCompat.getColor(requireContext(), R.color.md_theme_light_secondaryContainer))
                // Limpiar el tag del contenedor cuando termine el drag
                b.canvasContainer?.tag = null
                isDragInProgress = false
                // Diferir el re-renderizado hasta después de que termine el evento de drag
                targetCard.post {
                    if (needsRenderAfterDrag || _binding != null) {
                        needsRenderAfterDrag = false
                        canvasViewModel.nodes.value.let { renderNodes(it) }
                    }
                }
                true
            }
            else -> true
        }
    }

    private fun showOperationsMenu(anchorView: View) {
        val popup = PopupMenu(requireContext(), anchorView)
        popup.menu.add("+")
        popup.menu.add("-")
        popup.menu.add("*")
        popup.menu.add("/")
        popup.setOnMenuItemClickListener { item ->
            canvasViewModel.combineNodes(item.title.toString())
            true
        }
        popup.show()
    }

    override fun onStart() {
        super.onStart()
        val b = _binding ?: return
        b.expressionEditText.setText(expression)
        b.expressionEditText.setSelection(cursorPositionStart)
    }

    override fun onResume() {
        super.onResume()
        // Re-renderizar nodos cuando se vuelve de settings para aplicar el nuevo formato
        val b = _binding ?: return
        canvasViewModel.nodes.value.let { nodes ->
            if (nodes.isNotEmpty()) {
                renderNodes(nodes)
            }
        }
    }

    override fun onPause() {
        super.onPause()
        // Guardar el estado de los nodos antes de que se destruya el fragment
        // onPause se llama antes de onStop, así que es más seguro para guardar antes de recrear
        canvasViewModel.saveNodes(requireContext())
    }

    override fun onStop() {
        super.onStop()
        val b = _binding ?: return
        expression = b.expressionEditText.text.toString()
        cursorPositionStart = b.expressionEditText.selectionStart
    }

    override fun onBackPressed(): Boolean {
        return false
    }

    override fun onDigitButtonClick(digit: String) {
        val b = _binding ?: return
        InsertInExpression.enterDigit(digit, b.expressionEditText)
    }

    override fun onDotButtonClick() {
        val b = _binding ?: return
        InsertInExpression.enterDot(b.expressionEditText)
    }

    override fun onBackspaceButtonClick() {
        val b = _binding ?: return
        InsertInExpression.enterBackspace(b.expressionEditText)
    }

    override fun onClearExpressionButtonClick() {
        val b = _binding ?: return
        InsertInExpression.clearExpression(b.expressionEditText)
        canvasViewModel.clearNodes()
    }

    override fun onOperatorButtonClick(operator: String) {
        val b = _binding ?: return
        InsertInExpression.enterOperator(operator, b.expressionEditText)
    }

    override fun onScienceFunctionButtonClick(scienceFunction: String) {
        val b = _binding ?: return
        InsertInExpression.enterScienceFunction(scienceFunction, b.expressionEditText)
    }

    override fun onAdditionalOperatorButtonClick(operator: String) {
        val b = _binding ?: return
        InsertInExpression.enterAdditionalOperator(operator, b.expressionEditText)
    }

    override fun onConstantButtonClick(constant: String) {
        val b = _binding ?: return
        InsertInExpression.enterConstant(constant, b.expressionEditText)
    }

    override fun onBracketButtonClick() {
        val b = _binding ?: return
        InsertInExpression.enterBracket(b.expressionEditText)
    }

    override fun onDoubleBracketsButtonClick() {
        val b = _binding ?: return
        InsertInExpression.enterDoubleBrackets(b.expressionEditText)
    }

    override fun onEqualsButtonClick() {
        val b = _binding ?: return
        
        // Si hay un nodo seleccionado, salir de la selección (como cuando se hace tab fuera del frame)
        if (selectedNode != null) {
            hideNodePropertiesPanel()
            return
        }
        
        if (Evaluator.isCalculated) {
            val rawResultText = b.resultText.text.toString()
            val expressionText = b.expressionEditText.text.toString()
            val parsableResult = rawResultText
                .replace(Config.groupingSeparatorSymbol, "")
                .replace(Config.decimalSeparatorSymbol, ".")
                .replace(DefaultOperator.Minus.text, DefaultOperator.Minus.value)

            try {
                val resultValue = BigDecimal(parsableResult)
                canvasViewModel.addNode(expressionText, resultValue)
            } catch (e: NumberFormatException) {
                // Node not created if result is not a valid number
            }

            oldExpression = expressionText
            InsertInExpression.setExpression(rawResultText, b.expressionEditText)
            if (Config.autoSavingResults && !rawResultText.contains("Error")) {
                historyService.addHistoryData(expressionText, rawResultText)
            }
        }
    }

    override fun onEqualsButtonLongClick() {
        val b = _binding ?: return
        if (oldExpression.isNotEmpty()) {
            InsertInExpression.setExpression(oldExpression, b.expressionEditText)
        }
    }

    private fun setupNodePropertiesPanel() {
        val b = _binding ?: return
        
        // Configurar callback para clicks en el canvas
        b.zoomableCanvasContainer?.onCanvasClick = { x, y ->
            val panelContainer = b.nodePropertiesPanelContainer
            if (selectedNode != null && panelContainer != null && panelContainer.visibility == View.VISIBLE) {
                // Convertir coordenadas del canvas a coordenadas de pantalla
                val location = IntArray(2)
                panelContainer.getLocationOnScreen(location)
                val canvasLocation = IntArray(2)
                b.zoomableCanvasContainer?.getLocationOnScreen(canvasLocation)
                
                // Verificar si el click fue fuera del panel
                // El click en el canvas siempre es fuera del panel (el panel está debajo)
                hideNodePropertiesPanel()
            }
        }
        
        // Prevenir que los clicks dentro del panel cierren el panel
        b.nodePropertiesPanelContainer?.setOnTouchListener { view, event ->
            // Consumir el evento para que no se propague
            true
        }
    }

    private fun showNodePropertiesPanel(node: CalculationNode) {
        val b = _binding ?: return
        val panelContainer = b.nodePropertiesPanelContainer ?: return
        
        // Obtener el nodo actualizado desde el ViewModel para asegurar que tenemos los datos más recientes
        val currentNode = canvasViewModel.nodes.value.find { it.id == node.id } ?: node
        selectedNode = currentNode
        
        // Determinar si el nodo es hijo (tiene padres)
        val isChildNode = currentNode.parentNodeIds.isNotEmpty()
        
        // Mostrar el panel
        panelContainer.visibility = View.VISIBLE
        
        // Cargar la expresión del nodo actualizado en el ExpressionEditText principal
        b.expressionEditText.setText(currentNode.expression)
        b.expressionEditText.setSelection(currentNode.expression.length)
        
        // Si es un nodo hijo, deshabilitar la edición de expresión
        if (isChildNode) {
            // Deshabilitar el ExpressionEditText para que no se pueda editar la expresión
            b.expressionEditText.isEnabled = false
            b.expressionEditText.isFocusable = false
            b.expressionEditText.isClickable = false
            // Cambiar la opacidad para indicar que es de solo lectura
            b.expressionEditText.alpha = 0.6f
            // Ocultar el teclado de calculadora ya que no se puede editar la expresión
            b.keyboardContainer?.visibility = View.GONE
            // No conectar el teclado ya que no se puede editar
        } else {
            // Habilitar el ExpressionEditText para nodos que no son hijos
            b.expressionEditText.isEnabled = true
            b.expressionEditText.isFocusable = true
            b.expressionEditText.isClickable = true
            // Restaurar la opacidad normal
            b.expressionEditText.alpha = 1.0f
            // Mostrar el teclado de la calculadora debajo del panel
            b.keyboardContainer?.visibility = View.VISIBLE
            // Conectar el teclado al ExpressionEditText principal para actualizar el nodo
            connectCalculatorKeyboardToMainExpression()
        }
        
        // Cargar valores actuales del nodo actualizado en el panel
        val nameEdit = panelContainer.findViewById<EditText>(R.id.node_name_edit)
        val descriptionEdit = panelContainer.findViewById<EditText>(R.id.node_description_edit)
        
        nameEdit?.setText(currentNode.name)
        descriptionEdit?.setText(currentNode.description)
        
        // Configurar listeners para actualizar el ViewModel cuando cambien los valores
        nameEdit?.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                selectedNode?.let { node ->
                    canvasViewModel.updateNodeName(node.id, s?.toString() ?: "")
                }
            }
        })
        
        descriptionEdit?.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                selectedNode?.let { node ->
                    canvasViewModel.updateNodeDescription(node.id, s?.toString() ?: "")
                }
            }
        })
        
        // Configurar showSoftInputOnFocus programáticamente para nombre y descripción
        nameEdit?.showSoftInputOnFocus = true
        descriptionEdit?.showSoftInputOnFocus = true
        
        // Configurar focus listeners para manejar los teclados
        nameEdit?.setOnFocusChangeListener { view: View, hasFocus: Boolean ->
            if (hasFocus) {
                // Mostrar teclado del sistema para nombre
                showSystemKeyboard(view)
                // Ocultar teclado de calculadora temporalmente solo si no es un nodo hijo
                if (!isChildNode) {
                    b.keyboardContainer?.visibility = View.GONE
                }
            } else {
                // Cuando pierde el foco, mostrar el teclado de calculadora nuevamente solo si no es un nodo hijo
                val descriptionEditCheck = panelContainer.findViewById<EditText>(R.id.node_description_edit)
                if (descriptionEditCheck?.hasFocus() != true && !isChildNode) {
                    b.keyboardContainer?.visibility = View.VISIBLE
                }
            }
        }
        
        descriptionEdit?.setOnFocusChangeListener { view: View, hasFocus: Boolean ->
            if (hasFocus) {
                // Mostrar teclado del sistema para descripción
                showSystemKeyboard(view)
                // Ocultar teclado de calculadora temporalmente solo si no es un nodo hijo
                if (!isChildNode) {
                    b.keyboardContainer?.visibility = View.GONE
                }
            } else {
                // Cuando pierde el foco, mostrar el teclado de calculadora nuevamente solo si no es un nodo hijo
                val nameEditCheck = panelContainer.findViewById<EditText>(R.id.node_name_edit)
                if (nameEditCheck?.hasFocus() != true && !isChildNode) {
                    b.keyboardContainer?.visibility = View.VISIBLE
                }
            }
        }
        
        // Enfocar el ExpressionEditText principal solo si no es un nodo hijo
        if (!isChildNode) {
            b.expressionEditText.requestFocus()
        }
        
        // Re-renderizar nodos para mostrar el borde de selección
        canvasViewModel.nodes.value.let { renderNodes(it) }
    }

    private fun hideNodePropertiesPanel() {
        val b = _binding ?: return
        val panelContainer = b.nodePropertiesPanelContainer ?: return
        
        // Ocultar el panel
        panelContainer.visibility = View.GONE
        
        // Restaurar el estado del ExpressionEditText
        b.expressionEditText.isEnabled = true
        b.expressionEditText.isFocusable = true
        b.expressionEditText.isClickable = true
        // Restaurar la opacidad normal
        b.expressionEditText.alpha = 1.0f
        
        // Mostrar el teclado de calculadora nuevamente
        b.keyboardContainer?.visibility = View.VISIBLE
        
        // Ocultar teclado del sistema si está visible
        val currentFocus = requireActivity().currentFocus
        if (currentFocus != null) {
            hideSystemKeyboard(currentFocus)
        }
        
        // Desconectar el teclado de calculadora del panel
        disconnectCalculatorKeyboardFromPanelExpression()
        
        selectedNode = null
        
        // Re-renderizar nodos para actualizar el borde de selección
        canvasViewModel.nodes.value.let { renderNodes(it) }
    }

    private fun showSystemKeyboard(view: View) {
        val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        view.requestFocus()
        imm.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT)
    }

    private fun hideSystemKeyboard(view: View) {
        val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(view.windowToken, 0)
    }

    private fun connectCalculatorKeyboardToMainExpression() {
        val b = _binding ?: return
        val mainExpressionEdit = b.expressionEditText
        
        // Remover listener anterior si existe
        calculatorKeyboardSyncListener?.let {
            mainExpressionEdit.removeTextChangedListener(it)
        }
        
        // Agregar listener para actualizar el nodo cuando cambia la expresión principal
        calculatorKeyboardSyncListener = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (selectedNode != null) {
                    val text = s?.toString() ?: ""
                    if (text.isNotEmpty()) {
                        canvasViewModel.updateNodeExpression(selectedNode!!.id, text)
                    }
                }
            }
        }
        mainExpressionEdit.addTextChangedListener(calculatorKeyboardSyncListener)
    }

    private fun disconnectCalculatorKeyboardFromPanelExpression() {
        val b = _binding ?: return
        val mainExpressionEdit = b.expressionEditText
        
        // Remover el listener de sincronización
        calculatorKeyboardSyncListener?.let {
            mainExpressionEdit.removeTextChangedListener(it)
            calculatorKeyboardSyncListener = null
        }
    }
}