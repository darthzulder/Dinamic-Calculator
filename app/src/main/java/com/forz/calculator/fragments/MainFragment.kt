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
import com.forz.calculator.fragments.handlers.CanvasDragHandler
import com.forz.calculator.fragments.handlers.NodeRenderingManager
import com.forz.calculator.fragments.handlers.NodePropertiesManager
import com.forz.calculator.fragments.handlers.KeyboardDragHandler
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

    // Estado de drag
    private var isDragInProgress = false
    private var needsRenderAfterDrag = false
    private var selectedNode: CalculationNode? = null
        
    // Handlers delegados
    private lateinit var canvasDragHandler: CanvasDragHandler
    private lateinit var nodeRenderingManager: NodeRenderingManager
    private lateinit var nodePropertiesManager: NodePropertiesManager
    private lateinit var keyboardDragHandler: KeyboardDragHandler

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
        
        initializeHandlers()
        setupToolbar()
        setupExpressionInput()
        observeViewModels()
        setupCanvasDragListener()
        setupKeyboardDragListener()
        setupNodePropertiesPanel()
    }

    private fun initializeHandlers() {
        canvasDragHandler = CanvasDragHandler(
            getBinding = { _binding },
            canvasViewModel = canvasViewModel,
            onDragStateChanged = { isDragging -> isDragInProgress = isDragging },
            onRenderRequested = { canvasViewModel.nodes.value.let { renderNodes(it) } },
            getSelectedNode = { selectedNode },
            hideNodePropertiesPanel = { nodePropertiesManager.hideNodePropertiesPanel() }
        )
        
        nodeRenderingManager = NodeRenderingManager(
            context = requireContext(),
            getBinding = { _binding },
            canvasViewModel = canvasViewModel,
            createNodeViewCallback = { node -> createNodeView(node) },
            getSelectedNode = { selectedNode }
        )
        
        nodePropertiesManager = NodePropertiesManager(
            fragment = this,
            getBinding = { _binding },
            canvasViewModel = canvasViewModel,
            onRenderRequested = { canvasViewModel.nodes.value.let { renderNodes(it) } },
            getSelectedNode = { selectedNode },
            setSelectedNode = { node -> selectedNode = node }
        )
        
        keyboardDragHandler = KeyboardDragHandler(
            getBinding = { _binding },
            canvasViewModel = canvasViewModel
        )
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
        canvasDragHandler.setupCanvasDragListener()
    }

    private fun setupKeyboardDragListener() {
        keyboardDragHandler.setupKeyboardDragListener()
    }

    private fun renderNodes(nodes: List<CalculationNode>) {
        nodeRenderingManager.renderNodes(
            nodes = nodes,
            isDragInProgress = isDragInProgress,
            onNeedsRenderAfterDrag = { needsRenderAfterDrag = true }
        )
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
        nodePropertiesManager.setupNodePropertiesPanel()
    }

    private fun showNodePropertiesPanel(node: CalculationNode) {
        nodePropertiesManager.showNodePropertiesPanel(node)
    }

    private fun hideNodePropertiesPanel() {
        nodePropertiesManager.hideNodePropertiesPanel()
    }
}