package dev.labforge.app.ui.world

import android.content.Context
import android.opengl.GLSurfaceView
import android.os.Handler
import android.os.Looper
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import dev.labforge.app.core.world.WorldState

class WorldSceneView(context: Context) : GLSurfaceView(context) {
    var onEntitySelected: (String) -> Unit = {}
    var onReady: () -> Unit = {}
    var onFailure: (String) -> Unit = {}
    var onVisibleAgents: (Set<String>) -> Unit = {}
    var onFrameStats: (fps: Int, renderedAgents: Int) -> Unit = { _, _ -> }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val sceneRenderer = WorldRenderer(
        onReady = { mainHandler.post { onReady() } },
        onFailure = { message -> mainHandler.post { onFailure(message) } },
        onVisibleAgents = { ids -> mainHandler.post { onVisibleAgents(ids) } },
        onFrameStats = { fps, agents -> mainHandler.post { onFrameStats(fps, agents) } },
    )

    private val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(event: MotionEvent): Boolean = true
        override fun onSingleTapConfirmed(event: MotionEvent): Boolean {
            pick(event.x, event.y)
            return true
        }
    })
    private val scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            sceneRenderer.camera.zoom(detector.scaleFactor)
            return true
        }
    })

    private var lastX = 0f
    private var lastY = 0f
    private var lastFocusX = 0f
    private var lastFocusY = 0f
    private var hadTwoPointers = false

    init {
        setEGLContextClientVersion(3)
        setEGLConfigChooser(8, 8, 8, 8, 24, 0)
        setRenderer(sceneRenderer)
        renderMode = RENDERMODE_CONTINUOUSLY
        isFocusable = true
        isFocusableInTouchMode = true
    }

    fun setWorldState(state: WorldState) {
        sceneRenderer.world = state
    }

    fun setSelected(id: String?) {
        sceneRenderer.selectedId = id
    }

    fun focusOn(id: String) {
        val world = sceneRenderer.world ?: return
        val agent = world.agents.firstOrNull { it.id == id }
        if (agent != null) {
            sceneRenderer.camera.focus(agent.position)
            return
        }
        val directBuilding = world.buildings.firstOrNull { it.id == id }
        val companyBuilding = world.companies.firstOrNull { it.id == id }?.buildingId
            ?.let { buildingId -> world.buildings.firstOrNull { it.id == buildingId } }
        val building = directBuilding ?: companyBuilding ?: return
        sceneRenderer.camera.focus(building.position, building.height)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        parent?.requestDisallowInterceptTouchEvent(true)
        gestureDetector.onTouchEvent(event)
        scaleDetector.onTouchEvent(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastX = event.x
                lastY = event.y
                hadTwoPointers = false
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                lastFocusX = averageCoordinate(event, true)
                lastFocusY = averageCoordinate(event, false)
                hadTwoPointers = true
            }
            MotionEvent.ACTION_MOVE -> {
                if (event.pointerCount >= 2) {
                    val focusX = averageCoordinate(event, true)
                    val focusY = averageCoordinate(event, false)
                    if (hadTwoPointers && !scaleDetector.isInProgress) {
                        sceneRenderer.camera.pan(focusX - lastFocusX, focusY - lastFocusY)
                    }
                    lastFocusX = focusX
                    lastFocusY = focusY
                    hadTwoPointers = true
                } else if (!scaleDetector.isInProgress && !hadTwoPointers) {
                    sceneRenderer.camera.rotate(event.x - lastX, event.y - lastY)
                }
                lastX = event.x
                lastY = event.y
            }
            MotionEvent.ACTION_POINTER_UP -> {
                hadTwoPointers = false
                lastX = event.x
                lastY = event.y
            }
            MotionEvent.ACTION_UP -> {
                performClick()
                parent?.requestDisallowInterceptTouchEvent(false)
            }
            MotionEvent.ACTION_CANCEL -> parent?.requestDisallowInterceptTouchEvent(false)
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    fun shutdown() {
        queueEvent { sceneRenderer.releaseResources() }
        onPause()
    }

    private fun pick(x: Float, y: Float) {
        val world = sceneRenderer.world ?: return
        val ray = WorldPicking.ray(sceneRenderer.camera.snapshot(), x, y, width, height)
        val selected = WorldPicking.nearest(ray, WorldPicking.targets(world)) ?: return
        onEntitySelected(selected)
    }

    private fun averageCoordinate(event: MotionEvent, x: Boolean): Float {
        var sum = 0f
        for (index in 0 until event.pointerCount) sum += if (x) event.getX(index) else event.getY(index)
        return sum / event.pointerCount.coerceAtLeast(1)
    }
}
