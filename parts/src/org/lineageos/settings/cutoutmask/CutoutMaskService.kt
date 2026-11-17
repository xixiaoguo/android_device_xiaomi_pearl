/*
 * SPDX-FileCopyrightText: 2025 Paranoid Android
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.cutoutmask

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.graphics.Insets
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.hardware.display.DisplayManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.view.Display
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
import android.view.animation.AccelerateInterpolator
import kotlin.math.min
import kotlin.math.roundToInt
import org.lineageos.settings.utils.dlog

/**
 * Draws a black oval over the punch hole while the front camera is opened.
 *
 * 本文件按反编译得到的 XiaomiParts 字节码还原（原始源码已丢失），
 * 与 `smali/org/lineageos/settings/cutoutmask/CutoutMaskService*.smali` 逐方法对齐：
 *  - 开关：Settings.System 的 `pearl_cutout_mask_enabled`，缺省值 1（开）
 *  - 触发：CameraManager.AvailabilityCallback + DisplayListener + 亮/灭屏广播
 *    + Settings.System ContentObserver，统一走 [applyState]
 *  - 遮罩是 TYPE_APPLICATION_OVERLAY 悬浮窗，只在相机使用期间存在
 */
class CutoutMaskService : Service() {

    private var windowManager: WindowManager? = null
    private var cameraManager: CameraManager? = null
    private var displayManager: DisplayManager? = null

    private val mainHandler = Handler(Looper.getMainLooper())
    private val openCameras = LinkedHashSet<String>()
    private val facingCache = HashMap<String, Boolean>()

    private var maskView: View? = null
    private var maskParams: WindowManager.LayoutParams? = null

    /** 退出动画进行中时不重复启动，也避免显示逻辑误把它当成已存在。 */
    private var isHiding = false

    /**
     * 注意：这里用的是 (cameraId, packageId) 两参数版本 —— 反编译出的
     * `onCameraOpened` 只有两个 String 形参，多写一个 clientId 就不是重写了。
     */
    private val availabilityCallback =
        object : CameraManager.AvailabilityCallback() {
            override fun onCameraOpened(cameraId: String, packageId: String) {
                dlog(TAG, "camera $cameraId opened by $packageId")
                openCameras.add(cameraId)
                applyState()
            }

            override fun onCameraClosed(cameraId: String) {
                dlog(TAG, "camera $cameraId closed")
                openCameras.remove(cameraId)
                applyState()
            }
        }

    private val displayListener =
        object : DisplayManager.DisplayListener {
            override fun onDisplayAdded(displayId: Int) = Unit

            override fun onDisplayRemoved(displayId: Int) = Unit

            // 只有主屏变化、且窗口可见时才需要重算几何。
            override fun onDisplayChanged(displayId: Int) {
                if (displayId == Display.DEFAULT_DISPLAY && maskView != null && !isHiding) {
                    updateWindow()
                }
            }
        }

    private val screenReceiver =
        object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.action) {
                    Intent.ACTION_SCREEN_ON -> applyState()
                    Intent.ACTION_SCREEN_OFF -> removeWindow(animate = false)
                }
            }
        }

    private val settingsObserver =
        object : ContentObserver(mainHandler) {
            override fun onChange(selfChange: Boolean) {
                applyState()
            }
        }

    /** 关闭开关后等退出动画播完再停服务。 */
    private val stopWhenDisabled =
        Runnable {
            if (!isEnabled()) {
                dlog(TAG, "disabled, stopping service")
                stopSelf()
            }
        }

    override fun onCreate() {
        super.onCreate()
        dlog(TAG, "onCreate")

        windowManager = getSystemService(WindowManager::class.java)
        cameraManager = getSystemService(CameraManager::class.java)
        displayManager = getSystemService(DisplayManager::class.java)

        // 反编译里注册回调没有 try/catch，这里保持一致（它们不抛受检异常）。
        cameraManager?.registerAvailabilityCallback(mainExecutor, availabilityCallback)
        displayManager?.registerDisplayListener(displayListener, mainHandler)

        runCatching {
                registerReceiver(
                    screenReceiver,
                    IntentFilter().apply {
                        addAction(Intent.ACTION_SCREEN_ON)
                        addAction(Intent.ACTION_SCREEN_OFF)
                    },
                    // 反编译里是 3 参数 registerReceiver，flags = 4（NOT_EXPORTED）
                    Context.RECEIVER_NOT_EXPORTED,
                )
            }
            .onFailure { dlog(TAG, "failed to register screen receiver: $it") }

        runCatching {
                contentResolver.registerContentObserver(
                    Settings.System.CONTENT_URI,
                    true,
                    settingsObserver,
                )
            }
            .onFailure { dlog(TAG, "failed to register settings observer: $it") }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        applyState()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        dlog(TAG, "onDestroy")
        runCatching { cameraManager?.unregisterAvailabilityCallback(availabilityCallback) }
        runCatching { displayManager?.unregisterDisplayListener(displayListener) }
        runCatching { unregisterReceiver(screenReceiver) }
        runCatching { contentResolver.unregisterContentObserver(settingsObserver) }
        mainHandler.removeCallbacks(stopWhenDisabled)
        removeWindow(animate = false)
        super.onDestroy()
    }

    /** 开关打开 + 有前摄被占用 → 显示遮罩；其余情况一律移除窗口。 */
    private fun applyState() {
        if (!isEnabled()) {
            removeWindow()
            // 让 ContentObserver 触发的关闭也能看到退出动画，动画结束后再停服务。
            mainHandler.removeCallbacks(stopWhenDisabled)
            mainHandler.postDelayed(stopWhenDisabled, DISABLE_STOP_DELAY_MS)
            return
        }
        mainHandler.removeCallbacks(stopWhenDisabled)
        if (openCameras.isEmpty()) {
            removeWindow()
            return
        }
        if (openCameras.any { isFrontCamera(it) }) {
            updateWindow()
        } else {
            removeWindow()
        }
    }

    /**
     * 窗口已存在时只 updateViewLayout，避免 remove/add 闪一下；
     * 只有 maskView 为 null 时才 addView（反编译里的两条分支）。
     */
    private fun updateWindow() {
        val wm = windowManager ?: return

        val bounds =
            computeMaskBounds()
                ?: run {
                    removeWindow()
                    return
                }

        val existing = maskView
        if (existing == null) {
            val view =
                View(this).apply {
                    background = createMaskDrawable()
                    isClickable = false
                    isFocusable = false
                }

            val params =
                WindowManager.LayoutParams(
                    bounds.width(),
                    bounds.height(),
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    FLAGS,
                    PixelFormat.TRANSLUCENT,
                )
                    .apply {
                        gravity = Gravity.TOP or Gravity.LEFT
                        x = bounds.left
                        y = bounds.top
                        setTitle(WINDOW_TITLE)
                        layoutInDisplayCutoutMode =
                            WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                        fitInsetsTypes = 0
                    }

            try {
                wm.addView(view, params)
                maskView = view
                maskParams = params
                dlog(TAG, "mask added: $bounds")
            } catch (t: Throwable) {
                dlog(TAG, "failed to add mask window: $t")
            }
            return
        }

        // 相机快速重新打开时取消正在进行的退出动画，恢复到完整大小。
        existing.animate().cancel()
        existing.scaleX = 1f
        existing.scaleY = 1f
        existing.alpha = 1f
        isHiding = false

        existing.background = createMaskDrawable()
        val params = maskParams ?: return
        params.x = bounds.left
        params.y = bounds.top
        params.width = bounds.width()
        params.height = bounds.height()
        runCatching { wm.updateViewLayout(existing, params) }
            .onFailure { dlog(TAG, "failed to update mask window: $it") }
    }

    /**
     * 移除遮罩。默认播放 200ms 的缩小 + 淡出动画，让黑圈从原尺寸收到消失；
     * 灭屏或服务销毁时用 animate = false 立即移除，避免残留动画回调。
     */
    private fun removeWindow(animate: Boolean = true) {
        val view = maskView ?: return

        if (!animate) {
            view.animate().cancel()
            removeWindowNow(view)
            return
        }

        // 已经在退出中，交给当前动画完成。
        if (isHiding) return

        // 窗口还没 attach 时动画不会执行，直接移除。
        if (!view.isAttachedToWindow) {
            removeWindowNow(view)
            return
        }

        isHiding = true
        dlog(TAG, "mask hiding")
        view.animate()
            .scaleX(0f)
            .scaleY(0f)
            .alpha(0f)
            .setDuration(HIDE_DURATION_MS)
            .setInterpolator(AccelerateInterpolator())
            .withLayer()
            .withEndAction { removeWindowNow(view) }
            .start()
    }

    private fun removeWindowNow(view: View) {
        if (maskView !== view) return
        dlog(TAG, "mask removed")
        runCatching { windowManager?.removeViewImmediate(view) }
            .onFailure { dlog(TAG, "failed to remove mask window: $it") }
        maskView = null
        maskParams = null
        isHiding = false
    }

    /**
     * 遮罩矩形 = 挖孔方块四周外扩 4dp，并与屏幕 bounds 相交。
     * 相交失败说明算出来的位置完全在屏幕外，直接放弃（返回 null）。
     */
    private fun computeMaskBounds(): Rect? {
        return try {
            val wm = windowManager ?: return null
            val metrics = wm.maximumWindowMetrics

            val displayBounds = Rect(metrics.bounds)
            if (displayBounds.isEmpty) return null

            val insets =
                metrics.windowInsets.getInsetsIgnoringVisibility(WindowInsets.Type.displayCutout())
            val edge = cutoutEdge(insets) ?: return null

            val hole = hardwareHoleBounds(displayBounds, edge)
            val halo = (HALO_DP * resources.displayMetrics.density).roundToInt()

            val out = Rect(hole)
            out.inset(-halo, -halo)
            if (!out.intersect(displayBounds)) return null
            out
        } catch (t: Throwable) {
            dlog(TAG, "cannot compute mask bounds: $t")
            null
        }
    }

    /** 挖孔在哪条边：按 displayCutout insets 谁非零判断。 */
    private fun cutoutEdge(insets: Insets): Edge? =
        when {
            insets.top > 0 -> Edge.TOP
            insets.bottom > 0 -> Edge.BOTTOM
            insets.left > 0 -> Edge.LEFT
            insets.right > 0 -> Edge.RIGHT
            else -> null
        }

    /**
     * 挖孔几何（全部按屏幕短边与 1080 的比例换算，常量取自反编译字节码）：
     *
     *  - 方块边长 `size = round(76 * scale)`，至少 1px
     *  - 方块中心距挖孔所在边缘 `offset = round(122 * scale) / 2`
     *    （122/1080 是挖孔「外径」，76/1080 是遮罩方块边长，两者都参与计算）
     *  - 垂直于该边缘的方向上，方块以屏幕中心对齐
     *
     * 之后再由 [computeMaskBounds] 四周外扩 4dp。
     */
    private fun hardwareHoleBounds(bounds: Rect, edge: Edge): Rect {
        val scale = min(bounds.width(), bounds.height()).toFloat() / REFERENCE_WIDTH

        val size = (HOLE_RATIO * scale).roundToInt().coerceAtLeast(1)
        val offset = (OUTER_RATIO * scale).roundToInt().coerceAtLeast(size)

        val halfSize = size / 2
        val halfOffset = offset / 2

        val cx = bounds.centerX() - halfSize
        val cy = bounds.centerY() - halfSize

        return when (edge) {
            Edge.TOP -> {
                val top = bounds.top + halfOffset - halfSize
                Rect(cx, top, cx + size, top + size)
            }
            Edge.BOTTOM -> {
                val top = bounds.bottom - halfOffset - halfSize
                Rect(cx, top, cx + size, top + size)
            }
            Edge.LEFT -> {
                val left = bounds.left + halfOffset - halfSize
                Rect(left, cy, left + size, cy + size)
            }
            Edge.RIGHT -> {
                val left = bounds.right - halfOffset - halfSize
                Rect(left, cy, left + size, cy + size)
            }
        }
    }

    /** 遮罩就是一个纯黑椭圆（GradientDrawable OVAL + 0xFF000000）。 */
    private fun createMaskDrawable(): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(0xFF000000.toInt())
        }

    private fun isEnabled(): Boolean = sysInt(KEY_ENABLED, DEFAULT_ENABLED) != 0

    /**
     * 兜底判断：MTK 上前摄的 camera id 未必等于 LENS_FACING 上报值，
     * 可用 `pearl_cutout_mask_front_ids`（逗号分隔）手工指定。
     *
     * 注意反编译语义：**这个列表为空时返回 true**（表示「不需要额外过滤」），
     * 只有列表非空且不含该 id 时才返回 false。
     */
    private fun isConfiguredFront(cameraId: String): Boolean {
        val configured =
            sysString(KEY_FRONT_IDS)
                ?.split(',')
                ?.map { it.trim() }
                ?.filter { it.isNotEmpty() }
                .orEmpty()
        return configured.isEmpty() || configured.contains(cameraId)
    }

    /**
     * 前摄判定（严格按字节码顺序）：
     *  1. 命中 [facingCache] 直接返回
     *  2. 拿不到 CameraManager → 走 [isConfiguredFront]
     *  3. LENS_FACING_FRONT → true；LENS_FACING_BACK / LENS_FACING_EXTERNAL → false
     *     （这两个结果会写进缓存）
     *  4. 其它情况（null 或未知值，包括读 characteristics 抛异常）→ [isConfiguredFront]，
     *     且**不写缓存**
     */
    private fun isFrontCamera(cameraId: String): Boolean {
        facingCache[cameraId]?.let { return it }

        val cm = cameraManager ?: return isConfiguredFront(cameraId)

        val facing: Int? =
            try {
                cm.getCameraCharacteristics(cameraId).get(CameraCharacteristics.LENS_FACING)
            } catch (t: Throwable) {
                dlog(TAG, "cannot read characteristics of camera $cameraId: $t")
                null
            }

        return when (facing) {
            CameraCharacteristics.LENS_FACING_FRONT -> {
                facingCache[cameraId] = true
                true
            }
            CameraCharacteristics.LENS_FACING_BACK,
            CameraCharacteristics.LENS_FACING_EXTERNAL -> {
                facingCache[cameraId] = false
                false
            }
            else -> isConfiguredFront(cameraId)
        }
    }

    private fun sysInt(key: String, def: Int): Int =
        try {
            Settings.System.getInt(contentResolver, key, def)
        } catch (t: Throwable) {
            dlog(TAG, "cannot read $key: $t")
            def
        }

    private fun sysString(key: String): String? =
        try {
            Settings.System.getString(contentResolver, key)
        } catch (t: Throwable) {
            dlog(TAG, "cannot read $key: $t")
            null
        }

    private enum class Edge {
        TOP,
        BOTTOM,
        LEFT,
        RIGHT,
    }

    companion object {
        private const val TAG = "CutoutMask"

        const val KEY_ENABLED = "pearl_cutout_mask_enabled"
        const val KEY_FRONT_IDS = "pearl_cutout_mask_front_ids"
        const val DEFAULT_ENABLED = 1

        private const val WINDOW_TITLE = "PearlCutoutMask"

        /** NOT_FOCUSABLE | NOT_TOUCHABLE | NOT_TOUCH_MODAL | LAYOUT_IN_SCREEN | LAYOUT_NO_LIMITS */
        private const val FLAGS = 824

        /** 遮罩相对挖孔方块外扩的 dp。字节码：round(4.0f * density)。 */
        private const val HALO_DP = 4f

        /** 退出动画时长：缩小 + 淡出 200ms。 */
        private const val HIDE_DURATION_MS = 200L

        /** 关闭开关后延迟停服务，给退出动画留出时间。 */
        private const val DISABLE_STOP_DELAY_MS = HIDE_DURATION_MS + 50L

        /** 比例基准宽度。 */
        private const val REFERENCE_WIDTH = 1080f

        /** 遮罩方块边长比例：76 / 1080。 */
        private const val HOLE_RATIO = 76f

        /** 挖孔外径比例：122 / 1080（决定方块中心离边缘多远）。 */
        private const val OUTER_RATIO = 122f
    }
}
