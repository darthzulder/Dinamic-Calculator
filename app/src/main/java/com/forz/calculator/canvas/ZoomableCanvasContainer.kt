package com.forz.calculator.canvas

import android.content.Context
import android.graphics.PointF
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import kotlin.math.max
import kotlin.math.min

class ZoomableCanvasContainer @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private var scaleFactor = 1.0f
    private var posX = 0f
    private var posY = 0f
    
    // Tracking para modo Panning
    private var isPanning = false
    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var activePointerId = MotionEvent.INVALID_POINTER_ID

    companion object {
        private const val MIN_ZOOM = 0.5f
        private const val MAX_ZOOM = 5.0f // Aumentado para mejor experiencia
    }

    private val scaleGestureDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScaleBegin(detector: ScaleGestureDetector): Boolean = true

        override fun onScale(detector: ScaleGestureDetector): Boolean {
            val scale = detector.scaleFactor
            val prevScale = scaleFactor
            scaleFactor = max(MIN_ZOOM, min(MAX_ZOOM, scaleFactor * scale))

            // Matemáticas para hacer zoom hacia el punto focal (donde están los dedos)
            if (scaleFactor != prevScale) {
                val focusX = detector.focusX
                val focusY = detector.focusY
                
                // Ajustamos la posición para que el punto bajo el foco se mantenga estable
                // Fórmula: NuevaPos = Foco - (Foco - ViejaPos) * (NuevoScale / ViejoScale)
                val scaleChange = scaleFactor / prevScale
                posX = focusX - (focusX - posX) * scaleChange
                posY = focusY - (focusY - posY) * scaleChange
                
                applyTransform()
            }
            return true
        }
    })

    private val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDoubleTap(e: MotionEvent): Boolean {
            // Animación simple de reset o zoom in
            val targetScale = if (scaleFactor > 1.5f) 1.0f else 2.0f
            // Nota: Para producción, aquí usarías un ValueAnimator para suavidad
            scaleFactor = targetScale
            if (targetScale == 1.0f) {
                posX = 0f
                posY = 0f
            } else {
                // Zoom hacia el punto del doble tap
                val scaleChange = targetScale / scaleFactor // Factor de cambio
                posX = e.x - (e.x - posX) * (targetScale/scaleFactor) // Corrección simple
                posY = e.y - (e.y - posY) * (targetScale/scaleFactor)
            }
            applyTransform()
            return true
        }
    })

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        // 1. Si hay múltiples dedos, es zoom -> Interceptar siempre
        if (ev.pointerCount > 1) return true

        // 2. Si es un solo dedo, verificar si tocamos un nodo hijo
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastTouchX = ev.x
                lastTouchY = ev.y
                activePointerId = ev.getPointerId(0)

                // IMPORTANTE: Hit Test.
                // Si tocamos un nodo (View que no sea el background), NO interceptamos.
                // Dejamos que el evento pase al hijo para que pueda iniciar el Drag (LongClick).
                if (isTouchingNode(ev.x, ev.y)) {
                    isPanning = false
                    return false 
                }
                // Si tocamos el fondo vacío, prepárate para Pan
                isPanning = true
            }
            MotionEvent.ACTION_MOVE -> {
                // Si nos movemos y no estamos sobre un nodo, interceptamos para hacer Pan
                if (isPanning) return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                isPanning = false
                activePointerId = MotionEvent.INVALID_POINTER_ID
            }
        }
        return super.onInterceptTouchEvent(ev)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleGestureDetector.onTouchEvent(event)
        gestureDetector.onTouchEvent(event)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (event.pointerCount == 1) {
                    lastTouchX = event.x
                    lastTouchY = event.y
                    activePointerId = event.getPointerId(0)
                    isPanning = true
                    return true
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (isPanning && event.pointerCount == 1) {
                    val pointerIndex = event.findPointerIndex(activePointerId)
                    if (pointerIndex != -1) {
                        val x = event.getX(pointerIndex)
                        val y = event.getY(pointerIndex)
                        
                        val dx = x - lastTouchX
                        val dy = y - lastTouchY

                        posX += dx
                        posY += dy
                        applyTransform()

                        lastTouchX = x
                        lastTouchY = y
                    }
                    return true
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                isPanning = false
                activePointerId = MotionEvent.INVALID_POINTER_ID
            }
        }
        return true
    }

    // Verifica si el toque cae sobre una vista hija interactuable (Nodo)
    private fun isTouchingNode(x: Float, y: Float): Boolean {
        val container = getChildAt(0) as? ViewGroup ?: return false
        
        // Convertir coordenada de pantalla (x, y) a coordenada local del contenedor interno
        val localPoint = screenToCanvas(x, y)
        
        // Buscar si algún hijo está en esa posición
        // IMPORTANTE: No verificar límites del contenedor, ya que los nodos pueden estar fuera
        // del área visible original después del paneo
        for (i in container.childCount - 1 downTo 0) {
            val child = container.getChildAt(i)
            if (child.visibility == View.VISIBLE && child.id != com.forz.calculator.R.id.connection_lines_view) {
                // Verificar si el punto está dentro de los límites del nodo
                // Usar coordenadas absolutas del canvas, no limitadas por el tamaño del contenedor
                if (localPoint.x >= child.x && localPoint.x <= child.x + child.width &&
                    localPoint.y >= child.y && localPoint.y <= child.y + child.height) {
                    return true
                }
            }
        }
        return false
    }

    private fun applyTransform() {
        val child = getChildAt(0) ?: return
        child.pivotX = 0f
        child.pivotY = 0f
        child.translationX = posX
        child.translationY = posY
        child.scaleX = scaleFactor
        child.scaleY = scaleFactor
        // Eliminé updateChildSize() porque para un "Infinite Canvas" real
        // es mejor mover la vista visualmente que cambiar sus LayoutParams (performance).
    }
    
    /**
     * Expande el canvas_container para que pueda contener todos los nodos,
     * incluso cuando están fuera del área visible original.
     * Esto permite que el drag and drop funcione en todo el espacio del canvas.
     */
    fun expandCanvasForNodes(nodes: List<com.forz.calculator.canvas.CalculationNode>) {
        val container = getChildAt(0) as? ViewGroup ?: return
        
        // Asumir un tamaño promedio de nodo (ajustar según sea necesario)
        val nodeWidth = 300f
        val nodeHeight = 100f
        
        // Tamaño mínimo del canvas para permitir movimiento libre
        val minCanvasWidth = 5000f
        val minCanvasHeight = 5000f
        
        // Calcular el tamaño necesario basado en los nodos
        var requiredWidth = minCanvasWidth
        var requiredHeight = minCanvasHeight
        
        if (nodes.isNotEmpty()) {
            // Encontrar los límites de todos los nodos
            var minX = Float.MAX_VALUE
            var minY = Float.MAX_VALUE
            var maxX = Float.MIN_VALUE
            var maxY = Float.MIN_VALUE
            
            nodes.forEach { node ->
                val nodeLeft = node.positionX
                val nodeTop = node.positionY
                val nodeRight = nodeLeft + nodeWidth
                val nodeBottom = nodeTop + nodeHeight
                
                minX = minOf(minX, nodeLeft)
                minY = minOf(minY, nodeTop)
                maxX = maxOf(maxX, nodeRight)
                maxY = maxOf(maxY, nodeBottom)
            }
            
            // Agregar padding generoso para permitir movimiento fuera de los límites
            val padding = 2000f
            val nodeRangeWidth = maxX - minX + padding * 2
            val nodeRangeHeight = maxY - minY + padding * 2
            
            // El canvas debe ser al menos tan grande como el rango de nodos
            requiredWidth = maxOf(requiredWidth, nodeRangeWidth)
            requiredHeight = maxOf(requiredHeight, nodeRangeHeight)
        }
        
        // Actualizar el tamaño del contenedor
        val layoutParams = container.layoutParams
        val newWidth = requiredWidth.toInt()
        val newHeight = requiredHeight.toInt()
        
        // Solo actualizar si el tamaño cambió significativamente (evitar actualizaciones innecesarias)
        if (kotlin.math.abs(layoutParams.width - newWidth) > 100 || 
            kotlin.math.abs(layoutParams.height - newHeight) > 100) {
            layoutParams.width = newWidth
            layoutParams.height = newHeight
            container.layoutParams = layoutParams
            // Forzar actualización del layout
            container.requestLayout()
        }
    }

    /**
     * Convierte coordenadas de Pantalla (Touch event) a Coordenadas del Canvas (Mundo interno)
     * Esencial para el Drop Event.
     */
    fun screenToCanvas(screenX: Float, screenY: Float): PointF {
        // Fórmula inversa: (Screen - Translate) / Scale
        return PointF(
            (screenX - posX) / scaleFactor,
            (screenY - posY) / scaleFactor
        )
    }
}