package com.dz.calculator.calculator

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import kotlin.properties.Delegates
import org.javia.arity.ArityConfig

class CalculatorViewModel : ViewModel() {
    private val _isDegreeModActivated = MutableLiveData<Boolean>()
    val isDegreeModActivated: LiveData<Boolean>
        get() = _isDegreeModActivated

    private val _isScienceModActivated = MutableLiveData<Boolean>()
    val isScienceModActivated: LiveData<Boolean>
        get() = _isScienceModActivated
    var previousScienceModActivated: Boolean by Delegates.notNull()

    var isCalculated = false

    private val _converterResult = MutableLiveData<Double?>()
    val converterResult: LiveData<Double?> get() = _converterResult

    init {
        _isScienceModActivated.value = false
        previousScienceModActivated = false
    }

    fun init(isDegreeModActivated: Boolean) {
        _isDegreeModActivated.value = isDegreeModActivated
        ArityConfig.isDegreeMode = isDegreeModActivated
    }

    fun updateDegreeModActivated() {
        val newValue = !(_isDegreeModActivated.value ?: false)
        _isDegreeModActivated.value = newValue
        ArityConfig.isDegreeMode = newValue
    }

    fun updateScienceModActivated() {
        val oldScienceModActivated = _isScienceModActivated.value!!
        val newScienceModActivated = !oldScienceModActivated

        _isScienceModActivated.value = newScienceModActivated
        previousScienceModActivated = newScienceModActivated
    }

    fun updateConverterResult(result: Double?, calculated: Boolean) {
        _converterResult.value = result
        isCalculated = calculated
        lastConverterResult = result
    }

    companion object {
        var lastConverterResult: Double? = null
    }

    private val _isHistoryActivated = MutableLiveData<Boolean>()
    val isHistoryActivated: LiveData<Boolean>
        get() = _isHistoryActivated

    fun toggleHistoryActivated() {
        _isHistoryActivated.value = !(_isHistoryActivated.value ?: false)
    }

    fun setHistoryActivated(activated: Boolean) {
        if (_isHistoryActivated.value != activated) {
            _isHistoryActivated.value = activated
        }
    }
}
