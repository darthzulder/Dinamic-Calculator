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
    
    // Callback para clicks simples en el canvas (fuera de nodos)
    var onCanvasClick: ((x: Float, y: Float) -> Unit)? = null
    
    // Variables para detectar clicks simples (sin pan)
    private var touchStartX = 0f
    private var touchStartY = 0f
    private var touchStartTime = 0L
    private val CLICK_THRESHOLD = 10f // píxeles
    private val CLICK_TIME_THRESHOLD = 200L // milisegundos

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
        
        override fun onSingleTapUp(e: MotionEvent): Boolean {
            // Detectar click simple solo si no se hizo pan
            val timeDiff = System.currentTimeMillis() - touchStartTime
            val distanceX = kotlin.math.abs(e.x - touchStartX)
            val distanceY = kotlin.math.abs(e.y - touchStartY)
            
            if (timeDiff < CLICK_TIME_THRESHOLD && 
                distanceX < CLICK_THRESHOLD && 
                distanceY < CLICK_THRESHOLD &&
                !isTouchingNode(e.x, e.y)) {
                // Click simple en el canvas (fuera de nodos)
                onCanvasClick?.invoke(e.x, e.y)
            }
            return false
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
                
                // Guardar posición inicial para detectar clicks simples
                touchStartX = ev.x
                touchStartY = ev.y
                touchStartTime = System.currentTimeMillis()

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
    
    private var isInitialized = false

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        if (!isInitialized && width > 0 && height > 0) {
            // Configurar tamaño inicial del canvas a 3x la pantalla
            val container = getChildAt(0)
            val params = container.layoutParams
            params.width = width * 3
            params.height = height * 3
            container.layoutParams = params
            
            // Centrar el canvas inicialmente
            // posX = (ScreenW - CanvasW) / 2 = (W - 2W) / 2 = -W/2
            posX = -width / 2.0f
            posY = -height / 2.0f
            
            applyTransform()
            isInitialized = true
        }
    }

    /**
     * Expande el canvas_container para que pueda contener todos los nodos,
     * incluso cuando están fuera del área visible original.
     * El canvas inicia con el tamaño de 2x la pantalla y crece dinámicamente.
     * Esto permite que el drag and drop funcione en todo el espacio del canvas.
     */
    fun expandCanvasForNodes(nodes: List<com.forz.calculator.canvas.CalculationNode>) {
        val container = getChildAt(0) as? ViewGroup ?: return
        
        // Asumir un tamaño promedio de nodo (ajustar según sea necesario)
        val nodeWidth = 300f
        val nodeHeight = 100f
        
        // Tamaño mínimo del canvas: 3x el tamaño de la pantalla
        val minCanvasWidth = this.width.toFloat() * 3
        val minCanvasHeight = this.height.toFloat() * 3
        
        // Si no hay nodos, asegurar el tamaño mínimo 3x
        if (nodes.isEmpty()) {
            val layoutParams = container.layoutParams
            // Solo aplicar si aún no tiene el tamaño correcto (para no invalidar layout innecesariamente)
            if (layoutParams.width < minCanvasWidth.toInt() || layoutParams.height < minCanvasHeight.toInt()) {
                layoutParams.width = minCanvasWidth.toInt()
                layoutParams.height = minCanvasHeight.toInt()
                container.layoutParams = layoutParams
                container.requestLayout()
            }
            return
        }
        
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
        
        // Agregar padding para permitir movimiento fuera de los límites actuales
        val padding = 500f
        
        // Calcular el tamaño necesario considerando que los nodos pueden estar en cualquier posición
        val requiredWidth = maxOf(minCanvasWidth, maxX + padding, -minX + minCanvasWidth + padding)
        val requiredHeight = maxOf(minCanvasHeight, maxY + padding, -minY + minCanvasHeight + padding)
        
        // Actualizar el tamaño del contenedor
        val layoutParams = container.layoutParams
        val newWidth = requiredWidth.toInt()
        val newHeight = requiredHeight.toInt()
        
        // Solo actualizar si el tamaño cambió significativamente (evitar actualizaciones innecesarias)
        if (kotlin.math.abs(layoutParams.width - newWidth) > 50 || 
            kotlin.math.abs(layoutParams.height - newHeight) > 50) {
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

    /**
     * Ajusta la posición de paneo (posX, posY) para compensar un desplazamiento de los nodos.
     * Se usa cuando los nodos se desplazan (shift) para mantener coordenadas positivas.
     * @param dx Desplazamiento en X aplicado a los nodos
     * @param dy Desplazamiento en Y aplicado a los nodos
     */
    fun adjustPan(dx: Float, dy: Float) {
        posX -= dx * scaleFactor
        posY -= dy * scaleFactor
        applyTransform()
    }
}