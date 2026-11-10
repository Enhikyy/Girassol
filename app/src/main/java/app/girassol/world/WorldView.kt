package app.girassol.world

import android.annotation.SuppressLint
import android.content.Context
import android.opengl.GLSurfaceView
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import app.girassol.core.activity.ActivityHub

class WorldView(context: Context, activities: ActivityHub) : GLSurfaceView(context) {
    private val worldRenderer = WorldRenderer(activities)
    private var previousX = 0f
    private var previousY = 0f
    private val scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            queueEvent { worldRenderer.zoom(detector.scaleFactor) }
            return true
        }
    })

    init {
        setEGLContextClientVersion(3)
        setEGLConfigChooser(8, 8, 8, 8, 24, 0)
        setRenderer(worldRenderer)
        renderMode = RENDERMODE_CONTINUOUSLY
        contentDescription = "Campus tridimensional do Girassol"
    }

    fun focusZone(id: String) = queueEvent { worldRenderer.focusZone(id) }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> { previousX = event.x; previousY = event.y }
            MotionEvent.ACTION_MOVE -> if (event.pointerCount == 1 && !scaleDetector.isInProgress) {
                val dx = event.x - previousX; val dy = event.y - previousY
                queueEvent { worldRenderer.pan(-dx * 0.018f, -dy * 0.018f) }
                previousX = event.x; previousY = event.y
            }
            MotionEvent.ACTION_POINTER_DOWN -> { previousX = event.x; previousY = event.y }
        }
        return true
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        onResume()
    }

    override fun onDetachedFromWindow() {
        onPause()
        super.onDetachedFromWindow()
    }
}
