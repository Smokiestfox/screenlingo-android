package com.screenlingo.app.capture

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.*
import android.provider.Settings
import android.view.WindowManager
import android.view.WindowInsets
import com.screenlingo.app.*
import com.screenlingo.app.engine.TranslatedBlock
import com.screenlingo.app.engine.TranslationEngine
import com.screenlingo.app.overlay.OverlayController
import kotlin.math.max
import kotlin.math.roundToInt
import java.io.FileDescriptor
import java.io.PrintWriter

/** One projection / one virtual display per explicitly authorized session. Never sticky. */
class TranslationService : Service() {
    companion object {
        const val STOP = "com.screenlingo.STOP"
        const val TOGGLE = "com.screenlingo.TOGGLE"
        private const val CHANNEL = "screen_translation"
        private const val NOTIFICATION = 100
    }
    private val main = Handler(Looper.getMainLooper())
    private lateinit var settings: AppSettings
    private var engine: TranslationEngine? = null
    private var overlay: OverlayController? = null
    private var projection: MediaProjection? = null
    private var display: VirtualDisplay? = null
    private var reader: ImageReader? = null
    private var cleanFrame: Image? = null
    private var pendingCaptureFrame: Image? = null
    private var captureSettled = false
    private var screenWidth = 1
    private var screenHeight = 1
    private var captureWidth = 1
    private var captureHeight = 1
    private var normalizedRegion: RectF? = null
    private var ready = false
    private var paused = false
    private var busy = false
    private var waitingForFrame = false
    private var captureAfterNanos = 0L
    private var closed = false
    private var revision = 0
    private var frameStarted = 0L
    private var processedFrames = 0
    private var ocrFrames = 0
    private var lastProcessedBitmap: Bitmap? = null
    private var lastTranslatedBlocks = emptyList<TranslatedBlock>()
    private var lastFrameRevision = -1
    private var stopMessage = "Đã dừng dịch màn hình"
    private val captureTick = Runnable { requestFrame() }
    private val settleCapture = Runnable {
        captureSettled = true
        val image = pendingCaptureFrame
        pendingCaptureFrame = null
        if (image != null) consumeImage(image)
    }
    private val frameTimeout = Runnable {
        if (waitingForFrame) {
            waitingForFrame = false; busy = false; overlay?.restore()
            pendingCaptureFrame?.close(); pendingCaptureFrame = null
            main.removeCallbacks(settleCapture)
            publish("Đang chờ màn hình cập nhật"); schedule()
        }
    }
    private val projectionCallback = object : MediaProjection.Callback() {
        override fun onStop() { stopMessage = "Chia sẻ màn hình đã kết thúc"; stopSelf() }
        override fun onCapturedContentResize(width: Int, height: Int) {
            if (!closed && (width != screenWidth || height != screenHeight)) resize(width, height)
        }
    }

    override fun onBind(intent: Intent?) = null
    override fun onCreate() { super.onCreate(); settings = AppSettings(this) }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == STOP) { stopSelf(); return START_NOT_STICKY }
        if (intent?.action == TOGGLE) { togglePause(); return START_NOT_STICKY }
        if (intent == null || projection != null) { if (projection == null) stopSelf(); return START_NOT_STICKY }
        try {
            val data = if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra("resultData", Intent::class.java)
                else @Suppress("DEPRECATION") intent.getParcelableExtra("resultData")
            val code = intent.getIntExtra("resultCode", Activity.RESULT_CANCELED)
            if (code != Activity.RESULT_OK || data == null || !Settings.canDrawOverlays(this)) {
                stopMessage = "Thiếu quyền chia sẻ màn hình hoặc hiển thị nổi"; stopSelf(); return START_NOT_STICKY
            }
            createNotificationChannel()
            if (Build.VERSION.SDK_INT >= 29) startForeground(NOTIFICATION, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
            else startForeground(NOTIFICATION, notification())
            projection = getSystemService(MediaProjectionManager::class.java).getMediaProjection(code, data)
            projection!!.registerCallback(projectionCallback, main)
            val size = screenSize(); screenWidth = size.first; screenHeight = size.second
            overlay = OverlayController(this, ::togglePause, { stopSelf() }, ::setRegion) { selecting ->
                invalidateFrame()
                if (!selecting) schedule(100)
            }
            if (settings.regionMode) normalizedRegion = RectF(0.05f, 0.55f, 0.95f, 0.85f)
            configureReader()
            display = projection!!.createVirtualDisplay("ScreenLingo capture", captureWidth, captureHeight,
                resources.configuration.densityDpi, DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                reader!!.surface, null, main)
            engine = TranslationEngine(settings.source, settings.target)
            publish("Đang chuẩn bị mô hình dịch…")
            engine!!.prepare(settings.wifiOnly).addOnSuccessListener {
                if (closed) return@addOnSuccessListener
                ready = true; publish("Mở ứng dụng cần dịch hoặc màn hình mẫu")
                if (settings.regionMode) overlay?.selectRegion() else schedule(250)
            }.addOnFailureListener {
                if (!closed) { stopMessage = "Chưa tải được mô hình. Mở ứng dụng để thử lại."; stopSelf() }
            }
        } catch (e: Exception) {
            android.util.Log.e("ScreenLingo", "Cannot start screen translation", e)
            stopMessage = "Không thể bắt đầu. Mở ứng dụng và cấp lại quyền chia sẻ màn hình."
            stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun screenSize(): Pair<Int, Int> {
        val wm = getSystemService(WindowManager::class.java)
        if (Build.VERSION.SDK_INT >= 30) return wm.maximumWindowMetrics.bounds.let { it.width() to it.height() }
        val metrics = android.util.DisplayMetrics()
        @Suppress("DEPRECATION") wm.defaultDisplay.getRealMetrics(metrics)
        return metrics.widthPixels to metrics.heightPixels
    }

    private fun configureReader() {
        // 720 px on the short edge keeps typical UI text legible while bounding OCR cost.
        val scale = (720f / minOf(screenWidth, screenHeight)).coerceAtMost(1f)
        captureWidth = max(1, (screenWidth * scale).roundToInt())
        captureHeight = max(1, (screenHeight * scale).roundToInt())
        reader = ImageReader.newInstance(captureWidth, captureHeight, PixelFormat.RGBA_8888, 3).also {
            it.setOnImageAvailableListener({ source -> onImage(source) }, main)
        }
    }

    private fun resize(width: Int, height: Int) {
        if (width <= 0 || height <= 0) return
        invalidateFrame(); overlay?.clear()
        screenWidth = width; screenHeight = height
        val previous = reader
        cleanFrame?.close(); cleanFrame = null
        configureReader()
        display?.resize(captureWidth, captureHeight, resources.configuration.densityDpi)
        display?.surface = reader!!.surface
        previous?.setOnImageAvailableListener(null, null); previous?.close()
        overlay?.onDisplayChanged(); schedule(250)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (Build.VERSION.SDK_INT < 34) screenSize().let { if (it.first != screenWidth || it.second != screenHeight) resize(it.first, it.second) }
    }

    private fun requestFrame() {
        if (closed || !ready || paused) return
        if (SessionState.settingsVisible) { overlay?.clear(); schedule(); return }
        if (busy || overlay?.isSelecting == true || overlay?.hasOpenMenu() == true) { schedule(); return }
        busy = true; waitingForFrame = true; frameStarted = SystemClock.elapsedRealtime()
        // A static screen may not emit another frame. Retain the latest image seen before
        // any translation was drawn, so the very first OCR pass never depends on animation.
        val retained = cleanFrame
        cleanFrame = null
        if (retained != null && overlay?.hasTranslations() != true) {
            captureAfterNanos = 0L; consumeImage(retained); return
        }
        retained?.close()
        // Drain pending images, then hide our translations. The next composited frame contains
        // original app text, preventing OCR → translate → OCR feedback on our own output.
        reader?.acquireLatestImage()?.close()
        captureAfterNanos = System.nanoTime()
        captureSettled = false
        overlay?.hideForCapture()
        // Visibility changes reach SurfaceFlinger asynchronously. Wait for composition and
        // keep only the newest candidate; the first frame after hide can still contain our text.
        main.postDelayed(settleCapture, 100)
        main.postDelayed(frameTimeout, 750)
    }

    private fun onImage(source: ImageReader) {
        val image = try { source.acquireLatestImage() } catch (_: IllegalStateException) { null } ?: return
        if (!closed && !waitingForFrame && overlay?.hasTranslations() != true && overlay?.isSelecting != true && overlay?.hasOpenMenu() != true) {
            cleanFrame?.close(); cleanFrame = image; return
        }
        if (waitingForFrame && !captureSettled && image.timestamp >= captureAfterNanos) {
            pendingCaptureFrame?.close(); pendingCaptureFrame = image; return
        }
        consumeImage(image)
    }

    private fun consumeImage(image: Image) {
        if (closed || !waitingForFrame || paused || image.timestamp < captureAfterNanos) { image.close(); return }
        waitingForFrame = false; main.removeCallbacks(frameTimeout)
        val token = revision
        val region = actualRegion()
        // Crop system chrome before OCR, so clocks / capture timers never consume translation work.
        val area = region ?: RectF(contentBounds())
        val crop = Rect(
            (area.left * captureWidth / screenWidth).toInt().coerceIn(0, captureWidth - 1),
            (area.top * captureHeight / screenHeight).toInt().coerceIn(0, captureHeight - 1),
            (area.right * captureWidth / screenWidth).toInt().coerceIn(1, captureWidth),
            (area.bottom * captureHeight / screenHeight).toInt().coerceIn(1, captureHeight)
        )
        val bitmap = try { imageToBitmap(image, crop) } catch (e: Exception) {
            image.close(); busy = false; overlay?.restore(); publish("Không đọc được khung hình. Đang thử lại…"); schedule(); return
        }
        image.close()
        // Exact pixel comparison avoids both OCR and translation on unchanged screens.
        // Keep only one completed clean bitmap in memory, never an image on disk.
        if (lastFrameRevision == token && lastProcessedBitmap?.sameAs(bitmap) == true) {
            bitmap.recycle(); busy = false; processedFrames++
            overlay?.show(lastTranslatedBlocks, region)
            publishFrame(lastTranslatedBlocks, SystemClock.elapsedRealtime() - frameStarted)
            schedule((settings.intervalMs - (SystemClock.elapsedRealtime() - frameStarted)).coerceAtLeast(100))
            return
        }
        // Restore the prior translation while OCR and translation run asynchronously.
        overlay?.restore()
        val sx = screenWidth.toFloat() / captureWidth
        val sy = screenHeight.toFloat() / captureHeight
        ocrFrames++
        engine?.process(bitmap) { result ->
            if (closed) { bitmap.recycle(); return@process }
            busy = false
            if (token != revision || paused || overlay?.isSelecting == true || SessionState.settingsVisible) {
                bitmap.recycle(); schedule(); return@process
            }
            result.fold(onSuccess = { blocks ->
                processedFrames++
                val bubble = overlay?.excludedBounds() ?: Rect()
                val usable = contentBounds()
                val mapped = blocks.map { block ->
                    TranslatedBlock(Rect(
                        ((block.bounds.left + crop.left) * sx).roundToInt(),
                        ((block.bounds.top + crop.top) * sy).roundToInt(),
                        ((block.bounds.right + crop.left) * sx).roundToInt(),
                        ((block.bounds.bottom + crop.top) * sy).roundToInt()
                    ), block.source, block.translated)
                }.filter { !bubble.contains(it.bounds.centerX(), it.bounds.centerY()) && usable.contains(it.bounds.centerX(), it.bounds.centerY()) }
                lastProcessedBitmap?.recycle()
                lastProcessedBitmap = bitmap; lastFrameRevision = token; lastTranslatedBlocks = mapped
                overlay?.show(mapped, region)
                val latency = SystemClock.elapsedRealtime() - frameStarted
                publishFrame(mapped, latency)
            }, onFailure = {
                bitmap.recycle()
                overlay?.clear(); publish("Chưa xử lý được chữ. Đang thử lại…")
                android.util.Log.w("ScreenLingo", "Frame processing failed", it)
            })
            // Keep one frame in flight and count processing time toward the requested cadence.
            schedule((settings.intervalMs - (SystemClock.elapsedRealtime() - frameStarted)).coerceAtLeast(100))
        }
    }

    private fun imageToBitmap(image: Image, crop: Rect): Bitmap {
        val plane = image.planes[0]
        val paddedWidth = plane.rowStride / plane.pixelStride
        val full = Bitmap.createBitmap(paddedWidth, image.height, Bitmap.Config.ARGB_8888)
        try {
            full.copyPixelsFromBuffer(plane.buffer)
            val cropped = Bitmap.createBitmap(full, crop.left, crop.top, crop.width(), crop.height())
            if (cropped !== full) full.recycle()
            return cropped
        } catch (e: Exception) { full.recycle(); throw e }
    }

    private fun actualRegion(): RectF? = normalizedRegion?.let {
        RectF(it.left * screenWidth, it.top * screenHeight, it.right * screenWidth, it.bottom * screenHeight)
    }

    private fun publishFrame(blocks: List<TranslatedBlock>, latency: Long) {
        SessionState.publish(SessionSnapshot(true, false,
            if (blocks.isEmpty()) "Chưa thấy chữ trong vùng dịch" else "Đang dịch ${Languages.find(settings.source).name} → ${Languages.find(settings.target).name}",
            latency, blocks.size))
    }

    private fun contentBounds(): Rect {
        val area = Rect(0, 0, screenWidth, screenHeight)
        if (Build.VERSION.SDK_INT >= 30) {
            val insets = getSystemService(WindowManager::class.java).maximumWindowMetrics.windowInsets
                .getInsetsIgnoringVisibility(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
            area.set(insets.left, insets.top, screenWidth - insets.right, screenHeight - insets.bottom)
        }
        return area
    }

    private fun setRegion(rect: RectF?) {
        normalizedRegion = rect?.let { RectF(
            (it.left / screenWidth).coerceIn(0f, 1f), (it.top / screenHeight).coerceIn(0f, 1f),
            (it.right / screenWidth).coerceIn(0f, 1f), (it.bottom / screenHeight).coerceIn(0f, 1f)
        ) }
        invalidateFrame(); overlay?.clear(); schedule(100)
    }

    private fun togglePause() {
        if (projection == null || closed) return
        paused = !paused; invalidateFrame(); overlay?.setPaused(paused); overlay?.clear(); overlay?.restore()
        publish(if (paused) "Đã tạm dừng · Chạm bong bóng để tiếp tục" else "Đang chờ chữ trên màn hình")
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION, notification())
        if (!paused) schedule(100)
    }

    private fun invalidateFrame() {
        lastProcessedBitmap?.recycle(); lastProcessedBitmap = null; lastTranslatedBlocks = emptyList(); lastFrameRevision = -1
        if (waitingForFrame) busy = false
        revision++; waitingForFrame = false
        pendingCaptureFrame?.close(); pendingCaptureFrame = null
        main.removeCallbacks(captureTick); main.removeCallbacks(frameTimeout); main.removeCallbacks(settleCapture)
        // An OCR task still owns its bitmap until its completion callback.
    }

    private fun schedule(delay: Long = if (::settings.isInitialized) settings.intervalMs else 900) {
        main.removeCallbacks(captureTick)
        if (!closed && !paused && ready) main.postDelayed(captureTick, delay)
    }

    private fun publish(message: String) { SessionState.publish(SessionSnapshot(true, paused, message)) }
    private fun createNotificationChannel() {
        getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel(CHANNEL, "Dịch màn hình", NotificationManager.IMPORTANCE_LOW))
    }
    private fun notification(): Notification {
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        fun control(action: String, code: Int) = PendingIntent.getService(this, code, Intent(this, TranslationService::class.java).setAction(action), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return Notification.Builder(this, CHANNEL).setSmallIcon(com.screenlingo.app.R.drawable.ic_translate)
            .setContentTitle(if (paused) "ScreenLingo · Tạm dừng" else "ScreenLingo · Đang dịch màn hình")
            .setContentText("${Languages.find(settings.source).name} → ${Languages.find(settings.target).name} · Xử lý trên thiết bị")
            .setContentIntent(open).setOngoing(true)
            .addAction(Notification.Action.Builder(null, if (paused) "Tiếp tục" else "Tạm dừng", control(TOGGLE, 1)).build())
            .addAction(Notification.Action.Builder(null, "Dừng", control(STOP, 2)).build()).build()
    }

    override fun onTaskRemoved(rootIntent: Intent?) { stopSelf(); super.onTaskRemoved(rootIntent) }
    override fun dump(fd: FileDescriptor, writer: PrintWriter, args: Array<out String>?) {
        val state = SessionState.current
        writer.println("ScreenLingo running=${state.running} ready=$ready paused=$paused busy=$busy waiting=$waitingForFrame")
        writer.println("display=${screenWidth}x$screenHeight capture=${captureWidth}x$captureHeight region=${actualRegion()}")
        writer.println("frames=$processedFrames ocrFrames=$ocrFrames blocks=${state.blockCount} latencyMs=${state.latencyMs} message=${state.message}")
        writer.println("cacheHits=${engine?.lastCacheHits} cacheMisses=${engine?.lastCacheMisses}")
    }
    override fun onDestroy() {
        closed = true; revision++; main.removeCallbacksAndMessages(null)
        reader?.setOnImageAvailableListener(null, null)
        cleanFrame?.close(); cleanFrame = null
        pendingCaptureFrame?.close(); pendingCaptureFrame = null
        lastProcessedBitmap?.recycle(); lastProcessedBitmap = null
        display?.release(); display = null
        reader?.close(); reader = null
        projection?.unregisterCallback(projectionCallback); projection?.stop(); projection = null
        overlay?.close(); overlay = null
        engine?.close(); engine = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        SessionState.publish(SessionSnapshot(message = stopMessage))
        super.onDestroy()
    }
}
