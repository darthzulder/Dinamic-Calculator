package com.dz.calculator.history

import android.content.Context

/**
 * Stub class to maintain compatibility with existing code.
 * History functionality has been replaced by SessionService.
 */
class HistoryService(context: Context) {
    
    // Stub methods - do nothing but prevent compilation errors
    fun addHistoryData(expression: String, result: String) {
        // No-op: History functionality replaced by SessionService
    }
    
    fun clearHistoryData() {
        // No-op: History functionality replaced by SessionService
    }
    
    fun modifyHistoryData(oldExpression: String, oldResult: String, newExpression: String, newResult: String) {
        // No-op: History functionality replaced by SessionService
    }
}
