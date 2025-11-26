package com.dz.calculator.fragments.largeLand

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.ImageView
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.dz.calculator.AboutActivity
import com.dz.calculator.App
import com.dz.calculator.utils.HapticAndSound
import com.dz.calculator.MainActivity
import com.dz.calculator.OnMainActivityListener
import com.dz.calculator.R
import com.dz.calculator.history.HistoryService
import com.dz.calculator.settings.SettingsActivity
import com.dz.calculator.settings.Config
import com.dz.calculator.calculator.CalculatorViewModel
import com.dz.calculator.calculator.Evaluator
import com.dz.calculator.calculator.TrigonometricFunction
import com.dz.calculator.databinding.FragmentLargeLandBinding
import com.dz.calculator.expression.ExpressionViewModel
import com.dz.calculator.expression.ExpressionViewModel.cursorPositionStart
import com.dz.calculator.expression.ExpressionViewModel.expression
import com.dz.calculator.expression.ExpressionViewModel.oldExpression
import com.dz.calculator.fragments.CalculatorFragment
import com.dz.calculator.fragments.Fragments.CALCULATOR_FRAGMENT
import com.dz.calculator.fragments.Fragments.UNIT_CONVERTER_FRAGMENT
import com.dz.calculator.fragments.Fragments.currentItemMainPager
import com.dz.calculator.fragments.HistoryFragment
import com.dz.calculator.fragments.UnitConverterFragment
import com.dz.calculator.fragments.adapters.ViewPageAdapter
import com.dz.calculator.settings.Config.autoSavingResults
import com.dz.calculator.utils.InsertInExpression
import com.google.android.material.tabs.TabLayoutMediator
import kotlin.properties.Delegates.notNull

@Suppress("DEPRECATION")
class LargeLandFragment : Fragment(),
    OnMainActivityListener,
    CalculatorFragment.OnButtonClickListener,
    HistoryFragment.OnButtonClickListener,
    UnitConverterFragment.OnButtonClickListener
{

    private var binding: FragmentLargeLandBinding by notNull()
    private var hapticAndSound: HapticAndSound by notNull()


    private val historyService: HistoryService
        get() = (requireContext().applicationContext as App).historyService

    @SuppressLint("SetTextI18n")
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentLargeLandBinding.inflate(inflater, container, false)

        val views: Array<View> = arrayOf(
            binding.degreeTitleText
        )
        hapticAndSound = HapticAndSound(requireContext(), views)

        binding.expressionEditText.showSoftInputOnFocus = false
        binding.expressionEditText.requestFocus()


        val adapter = ViewPageAdapter(childFragmentManager, lifecycle)

        adapter.addFragment(UnitConverterFragment())
        adapter.addFragment(CalculatorFragment())

        binding.calculatorViewPager.adapter = adapter
        binding.calculatorViewPager.setCurrentItem(currentItemMainPager, false)
        binding.calculatorViewPager.offscreenPageLimit = 2

        TabLayoutMediator(binding.tabLayout, binding.calculatorViewPager) { tab, position ->
            tab.setIcon(
                when (position) {
                    UNIT_CONVERTER_FRAGMENT -> R.drawable.baseline_autorenew
                    CALCULATOR_FRAGMENT -> R.drawable.baseline_calculate
                    else -> throw IllegalArgumentException("Invalid position")
                }
            )
        }.attach()

        binding.calculatorViewPager.apply {
            (getChildAt(0) as RecyclerView).overScrollMode = RecyclerView.OVER_SCROLL_NEVER
        }

        binding.toolbar.let { toolbar ->
            toolbar.menu.clear()
            toolbar.inflateMenu(R.menu.history_options_menu)
        }

        binding.calculatorViewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                currentItemMainPager = position
            }
        })

        binding.toolbar.setOnMenuItemClickListener { menuItem ->
            when(menuItem.itemId) {
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
                R.id.clearHistory -> {
                    val builder: AlertDialog.Builder = AlertDialog.Builder(requireContext())
                    builder
                        .setMessage(getString(R.string.clear_history_title))
                        .setPositiveButton(getString(R.string.clear_history_clear)) { _, _ ->
                            historyService.clearHistoryData()
                        }.setNegativeButton(getString(R.string.clear_history_dismiss)) { _, _ ->
                        }

                    val dialog: AlertDialog = builder.create()
                    dialog.show()
                    true
                }
                else -> false
            }
        }

        binding.expressionEditText.onFocusChangeListener = View.OnFocusChangeListener { view, hasFocus ->
            if (!hasFocus) {
                view.requestFocus()
            }
        }

        binding.expressionEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {
            }
            override fun onTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {
            }
            override fun afterTextChanged(p0: Editable?) {
                Evaluator.setResultTextView(binding.expressionEditText, binding.resultText, ExpressionViewModel.isSelected.value ?: false, requireContext())
                binding.expressionEditText.autoSizeTextExpressionEditText(binding.expressionTextView)

                if (TrigonometricFunction.entries.any { binding.expressionEditText.text!!.contains(it.text) }){
                    binding.degreeTitleText.visibility = ImageView.VISIBLE
                } else{
                    binding.degreeTitleText.visibility = ImageView.GONE
                }
            }
        })

        binding.root.viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                binding.expressionEditText.autoSizeTextExpressionEditText(binding.expressionTextView)
                binding.root.viewTreeObserver.removeOnGlobalLayoutListener(this)
            }
        })

        ExpressionViewModel.isSelected.observe(requireActivity()){ isSelected ->
            Evaluator.setResultTextView(binding.expressionEditText, binding.resultText, isSelected, requireContext())
        }

        binding.degreeTitleText.setOnClickListener {
            CalculatorViewModel.updateDegreeModActivated()
            hapticAndSound.vibrateEffectClick()
        }

        CalculatorViewModel.isDegreeModActivated.observe(requireActivity()) { isDegreeModActivated ->
            if (isDegreeModActivated) {
                binding.degreeTitleText.text = getString(R.string.deg)
            } else {
                binding.degreeTitleText.text = getString(R.string.rad)
            }

            Evaluator.setResultTextView(binding.expressionEditText, binding.resultText, ExpressionViewModel.isSelected.value ?: false, requireContext())
        }

        return binding.root
    }

    override fun onStart() {
        super.onStart()

        hapticAndSound.setHapticFeedback()
        hapticAndSound.setSoundEffects()
        binding.calculatorViewPager.isUserInputEnabled = Config.swipeMain
        binding.expressionEditText.setText(expression)
        binding.expressionEditText.setSelection(cursorPositionStart)
    }

    override fun onStop() {
        super.onStop()

        expression = binding.expressionEditText.text.toString()
        cursorPositionStart = binding.expressionEditText.selectionStart

        if (Evaluator.isCalculated && autoSavingResults){
            val result = binding.resultText.text.toString()

            val expression: String = if (ExpressionViewModel.isSelected.value == true){
                binding.expressionEditText.text
                    .toString()
                    .substring(
                        binding.expressionEditText.selectionStart, binding.expressionEditText.selectionEnd
                    )
            } else {
                binding.expressionEditText.text.toString()
            }

            historyService.addHistoryData(expression, result)
        }
    }

    override fun onBackPressed(): Boolean {
        return if (currentItemMainPager != CALCULATOR_FRAGMENT) {
            binding.calculatorViewPager.setCurrentItem(CALCULATOR_FRAGMENT, true)
            true
        } else {
            false
        }
    }

    override fun onDigitButtonClick(digit: String) {
        InsertInExpression.enterDigit(digit, binding.expressionEditText)
    }

    override fun onDotButtonClick() {
        InsertInExpression.enterDot(binding.expressionEditText)
    }

    override fun onBackspaceButtonClick() {
        InsertInExpression.enterBackspace(binding.expressionEditText)
    }

    override fun onBackspaceButtonLongClick() {
        TODO("Not yet implemented")
    }

    override fun onClearExpressionButtonClick() {
        InsertInExpression.clearExpression(binding.expressionEditText)
    }

    override fun onOperatorButtonClick(operator: String) {
        InsertInExpression.enterOperator(operator, binding.expressionEditText)
    }

    override fun onScienceFunctionButtonClick(scienceFunction: String) {
        InsertInExpression.enterScienceFunction(scienceFunction, binding.expressionEditText)
    }

    override fun onAdditionalOperatorButtonClick(operator: String) {
        InsertInExpression.enterAdditionalOperator(operator, binding.expressionEditText)
    }

    override fun onConstantButtonClick(constant: String) {
        InsertInExpression.enterConstant(constant, binding.expressionEditText)
    }

    override fun onBracketButtonClick() {
        InsertInExpression.enterBracket(binding.expressionEditText)
    }

    override fun onDoubleBracketsButtonClick() {
        InsertInExpression.enterDoubleBrackets(binding.expressionEditText)
    }

    override fun onEqualsButtonClick() {
        if (Evaluator.isCalculated){
            val result = binding.resultText.text.toString()

            val expression: String = if (ExpressionViewModel.isSelected.value == true){
                binding.expressionEditText.text
                    .toString()
                    .substring(
                        binding.expressionEditText.selectionStart, binding.expressionEditText.selectionEnd
                    )
            } else {
                binding.expressionEditText.text.toString()
            }

            oldExpression = expression
            InsertInExpression.setExpression(result, binding.expressionEditText)
            historyService.addHistoryData(expression, result)
        }
    }

    override fun onEqualsButtonLongClick() {
        if (oldExpression.isNotEmpty()){
            InsertInExpression.setExpression(oldExpression, binding.expressionEditText)
        }
    }

    override fun onExpressionTextClick(expression: String) {
        InsertInExpression.insertHistoryExpression(expression, binding.expressionEditText)
    }

    override fun onResultTextClick(result: String) {
        InsertInExpression.insertHistoryResult(result, binding.expressionEditText)
    }

    override fun onUnitResultTextClick(result: String) {
        InsertInExpression.setExpression(result, binding.expressionEditText)
    }
}