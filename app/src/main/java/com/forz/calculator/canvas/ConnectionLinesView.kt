package com.forz.calculator.canvas

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import com.forz.calculator.canvas.CalculationNode
import com.forz.calculator.canvas.NodeConnection
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Vista personalizada para dibujar las líneas de conexión entre nodos con flechas direccionales
 */
class ConnectionLinesView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val arrowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private var nodes: List<CalculationNode> = emptyList()
    private var connections: List<NodeConnection> = emptyList()
    private var nodeViews: Map<String, View> = emptyMap()

    // Espaciado entre flechas
    private val arrowSpacing = 40f
    private val arrowSize = 12f

    fun updateData(
        nodes: List<CalculationNode>,
        connections: List<NodeConnection>,
        nodeViews: Map<String, View>
    ) {
        this.nodes = nodes
        this.connections = connections
        this.nodeViews = nodeViews
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        // Dibujar cada conexión
        connections.forEach { connection ->
            val fromView = nodeViews[connection.fromNodeId]
            val toView = nodeViews[connection.toNodeId]
            
            if (fromView != null && toView != null && fromView.visibility == View.VISIBLE && toView.visibility == View.VISIBLE) {
                // Calcular las coordenadas de los puntos de conexión desde el centro de cada nodo
                val fromX = fromView.x + fromView.width / 2f
                val fromY = fromView.y + fromView.height / 2f
                val toX = toView.x + toView.width / 2f
                val toY = toView.y + toView.height / 2f
                
                // Configurar el color de la línea y las flechas
                linePaint.color = connection.color
                arrowPaint.color = connection.color
                
                // Calcular la distancia y el ángulo
                val dx = toX - fromX
                val dy = toY - fromY
                val distance = sqrt(dx * dx + dy * dy)
                val angle = atan2(dy, dx)
                
                // Calcular el punto de inicio y fin considerando el radio del nodo
                // Asumimos que los nodos son aproximadamente circulares/rectangulares
                val nodeRadius = minOf(fromView.width, fromView.height) / 2f
                val startX = fromX + cos(angle) * nodeRadius
                val startY = fromY + sin(angle) * nodeRadius
                val endX = toX - cos(angle) * nodeRadius
                val endY = toY - sin(angle) * nodeRadius
                
                // Dibujar la línea base
                canvas.drawLine(startX, startY, endX, endY, linePaint)
                
                // Dibujar flechas a lo largo de la línea
                drawArrowsAlongLine(canvas, startX, startY, endX, endY, angle, distance - nodeRadius * 2)
            }
        }
    }

    private fun drawArrowsAlongLine(
        canvas: Canvas,
        startX: Float,
        startY: Float,
        endX: Float,
        endY: Float,
        angle: Float,
        lineLength: Float
    ) {
        val numArrows = (lineLength / arrowSpacing).toInt().coerceAtLeast(1)
        val stepX = (endX - startX) / numArrows
        val stepY = (endY - startY) / numArrows
        
        for (i in 1 until numArrows) {
            val arrowX = startX + stepX * i
            val arrowY = startY + stepY * i
            drawArrow(canvas, arrowX, arrowY, angle)
        }
        
        // Dibujar una flecha final cerca del final
        val finalArrowX = endX - cos(angle) * arrowSize * 2
        val finalArrowY = endY - sin(angle) * arrowSize * 2
        drawArrow(canvas, finalArrowX, finalArrowY, angle)
    }

    private fun drawArrow(canvas: Canvas, x: Float, y: Float, angle: Float) {
        val path = Path()
        
        // Calcular los puntos de la flecha (triángulo)
        val arrowLength = arrowSize
        val arrowWidth = arrowSize * 0.6f
        
        // Punto de la punta de la flecha
        val tipX = x + cos(angle) * arrowLength
        val tipY = y + sin(angle) * arrowLength
        
        // Puntos de la base de la flecha
        val baseX1 = x - cos(angle) * arrowLength * 0.3f + sin(angle) * arrowWidth
        val baseY1 = y - sin(angle) * arrowLength * 0.3f - cos(angle) * arrowWidth
        val baseX2 = x - cos(angle) * arrowLength * 0.3f - sin(angle) * arrowWidth
        val baseY2 = y - sin(angle) * arrowLength * 0.3f + cos(angle) * arrowWidth
        
        // Crear el triángulo de la flecha
        path.moveTo(tipX, tipY)
        path.lineTo(baseX1, baseY1)
        path.lineTo(baseX2, baseY2)
        path.close()
        
        canvas.drawPath(path, arrowPaint)
    }
}

