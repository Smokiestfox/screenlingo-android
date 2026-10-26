package com.screenlingo.app

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionConfig
import android.media.projection.MediaProjectionManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import com.google.mlkit.nl.translate.TranslateRemoteModel
import com.screenlingo.app.capture.TranslationService
import com.screenlingo.app.ui.*

class MainActivity : Activity() {
    private lateinit var settings: AppSettings
    private lateinit var primary: TextView
    private lateinit var status: TextView
    private lateinit var badge: TextView
    private lateinit var sourceLabel: TextView
    private lateinit var targetLabel: TextView
    private lateinit var regionSwitch: Switch
    private lateinit var intervalLabel: TextView
    private lateinit var modelLabel: TextView
    private var downloading = false
    private var translator: Translator? = null
    private var generation = 0
    private var pendingStart = false
    private val listener: (SessionSnapshot) -> Unit = { snapshot -> runOnUiThread { render(snapshot) } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settings = AppSettings(this)
        pendingStart = savedInstanceState?.getBoolean("pendingStart") ?: false
        buildUi()
    }

    override fun onStart() { super.onStart(); SessionState.settingsVisible = true; SessionState.subscribe(listener) }
    override fun onStop() { SessionState.settingsVisible = false; SessionState.unsubscribe(listener); super.onStop() }
    override fun onDestroy() { generation++; translator?.close(); super.onDestroy() }
    override fun onSaveInstanceState(outState: Bundle) { outState.putBoolean("pendingStart", pendingStart); super.onSaveInstanceState(outState) }

    private fun buildUi() {
        val root = column().apply {
            setBackgroundColor(Palette.background)
            setPadding(dp(24), dp(12), dp(24), dp(20))
            setOnApplyWindowInsetsListener { view, insets ->
                view.setPadding(dp(24) + insets.systemWindowInsetLeft, dp(12) + insets.systemWindowInsetTop,
                    dp(24) + insets.systemWindowInsetRight, dp(20) + insets.systemWindowInsetBottom)
                insets
            }
        }
        val header = row()
        header.addView(label("▤", 26f, Palette.mint, true).apply {
            gravity = Gravity.CENTER; background = shape(Palette.panel, 12)
            contentDescription = "Biểu tượng ScreenLingo"
        }, LinearLayout.LayoutParams(dp(44), dp(44)))
        header.addView(label("ScreenLingo", 20f, bold = true), LinearLayout.LayoutParams(0, -2, 1f).apply { leftMargin = dp(12) })
        badge = label("●  SẴN SÀNG", 10f, Palette.mint, true).apply {
            background = shape(Palette.panel, 30); setPadding(dp(10), dp(8), dp(10), dp(8))
        }
        header.addView(badge)
        root.item(header)
        val content = column()
        val scroll = ScrollView(this).apply { isFillViewport = false; isVerticalScrollBarEnabled = false; addView(content) }
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = dp(10) })
        content.space(16)
        content.item(label("KHÔNG CÒN RÀO CẢN NGÔN NGỮ", 10f, Palette.mint, true))
        content.item(label("Hiểu mọi\nmàn hình.", 42f, bold = true).apply { setLineSpacing(0f, 1.02f) }, top = 12)
        content.item(label("Dịch ngay trên ứng dụng bạn đang dùng.\nĐọc liền mạch, không cần chuyển qua lại.", 14f, Palette.muted).apply { setLineSpacing(dp(4).toFloat(), 1f) }, top = 14)
        val preview = column().apply {
            background = shape(Palette.panel, 22, Palette.border); setPadding(dp(18), dp(16), dp(18), dp(16))
        }
        preview.item(label("BẢN XEM TRƯỚC                                  EN → VI", 10f, Palette.muted, true))
        preview.item(label("The world is full of stories.", 15f, Palette.muted), top = 17)
        preview.item(label("Thế giới đầy ắp những câu chuyện.", 16f, Palette.mint, true).apply {
            background = shape(0xFF213D36.toInt(), 10); setPadding(dp(12), dp(12), dp(12), dp(12))
        }, top = 10)
        preview.item(label("●  Tự nhận diện · Dịch trên thiết bị", 11f, Palette.muted), top = 14)
        content.item(preview, top = 24)

        content.item(label("NGÔN NGỮ", 11f, Palette.muted, true), top = 24)
        val langs = row()
        val sourceCard = languageCard("DỊCH TỪ", settings.source) { selectLanguage(true) }
        sourceLabel = sourceCard.getChildAt(1) as TextView
        val targetCard = languageCard("DỊCH SANG", settings.target) { selectLanguage(false) }
        targetLabel = targetCard.getChildAt(1) as TextView
        langs.addView(sourceCard, LinearLayout.LayoutParams(0, dp(86), 1f))
        langs.addView(label("→", 20f, Palette.mint).apply { gravity = Gravity.CENTER }, LinearLayout.LayoutParams(dp(38), -2))
        langs.addView(targetCard, LinearLayout.LayoutParams(0, dp(86), 1f))
        content.item(langs, top = 10)

        content.item(label("PHIÊN DỊCH CỦA BẠN", 11f, Palette.muted, true), top = 24)
        val options = column().apply {
            background = shape(Palette.panel, 20); setPadding(dp(16), dp(4), dp(16), dp(4))
        }
        regionSwitch = Switch(this).apply {
            isChecked = settings.regionMode
            contentDescription = "Chỉ dịch vùng được chọn"
            setOnCheckedChangeListener { _, enabled ->
                if (!SessionState.current.running) settings.regionMode = enabled
            }
        }
        options.item(optionRow("Dịch một vùng", "Phù hợp hội thoại game, phụ đề", regionSwitch), 76)
        options.item(View(this).apply { setBackgroundColor(Palette.border) }, 1)
        intervalLabel = label(intervalName(), 13f, Palette.mint, true)
        options.item(optionRow("Nhịp cập nhật", "Tự chờ khi khung trước chưa xử lý xong", intervalLabel).apply {
            setOnClickListener { chooseInterval() }; isFocusable = true
        }, 76)
        content.item(options, top = 10)
        modelLabel = action("↓  Tải mô hình ngôn ngữ", false) { prepareModels(false) }
        content.item(modelLabel, top = 12)
        content.item(action("Đọc thử trên màn hình mẫu  ↗") { startActivity(Intent(this, ReadingActivity::class.java)) }, top = 10)
        content.item(label("Xử lý bằng Google ML Kit trên thiết bị. Mô hình cần tải lần đầu; ảnh màn hình không được lưu vào bộ nhớ hay gửi tới máy chủ dịch.", 11f, Palette.muted).apply { setLineSpacing(dp(3).toFloat(), 1f) }, top = 14)
        content.item(label("Translation powered by Google", 10f, Palette.muted), top = 10)
        content.space(24)

        status = label("Sẵn sàng khi bạn cần", 12f, Palette.muted).apply { gravity = Gravity.CENTER }
        root.item(status, top = 8)
        primary = action("Bắt đầu dịch màn hình  →", true) { primaryAction() }
        root.item(primary, 56, 12)
        setContentView(root)
    }

    private fun languageCard(title: String, code: String, click: () -> Unit) = column().apply {
        background = shape(Palette.panel, 16, Palette.border)
        setPadding(dp(14), dp(16), dp(10), dp(14))
        item(label(title, 10f, Palette.muted, true))
        item(label("${Languages.find(code).name}  ⌄", 15f, bold = true), top = 8)
        setOnClickListener { click() }; isFocusable = true
    }

    private fun optionRow(title: String, subtitle: String, trailing: View) = row().apply {
        val copy = column().apply {
            item(label(title, 15f, bold = true))
            item(label(subtitle, 11f, Palette.muted), top = 6)
        }
        addView(copy, LinearLayout.LayoutParams(0, -2, 1f))
        addView(trailing)
    }

    private fun selectLanguage(source: Boolean) {
        if (locked()) return
        val choices = if (source) Languages.sources else Languages.targets
        AlertDialog.Builder(this).setTitle(if (source) "Ngôn ngữ gốc" else "Ngôn ngữ bản dịch")
            .setItems(choices.map { "${it.name} · ${it.nativeName}" }.toTypedArray()) { _, index ->
                val selected = choices[index]
                if (source) settings.source = selected.code else settings.target = selected.code
                sourceLabel.text = "${Languages.find(settings.source).name}  ⌄"
                targetLabel.text = "${Languages.find(settings.target).name}  ⌄"
                modelLabel.text = "↓  Tải mô hình ngôn ngữ"
            }.setNegativeButton("Đóng", null).show()
    }

    private fun chooseInterval() {
        if (locked()) return
        val intervals = listOf(500L, 900L, 1500L, 2500L)
        AlertDialog.Builder(this).setTitle("Nhịp cập nhật mục tiêu")
            .setSingleChoiceItems(arrayOf("0,5 giây · nhanh", "0,9 giây · cân bằng", "1,5 giây · tiết kiệm", "2,5 giây · đọc sách"), intervals.indexOf(settings.intervalMs)) { dialog, which ->
                settings.intervalMs = intervals[which]; intervalLabel.text = intervalName(); dialog.dismiss()
            }.setNegativeButton("Đóng", null).show()
    }

    private fun intervalName() = "${settings.intervalMs / 1000.0} s  ⌄"
    private fun locked(): Boolean {
        if (SessionState.current.running || downloading) { toast("Dừng phiên dịch hoặc chờ tải mô hình để thay đổi."); return true }
        return false
    }

    private fun primaryAction() {
        if (SessionState.current.running) { startService(Intent(this, TranslationService::class.java).setAction(TranslationService.STOP)); return }
        if (downloading) {
            generation++; downloading = false; pendingStart = false; translator?.close(); translator = null
            modelLabel.text = "↓  Tải mô hình ngôn ngữ"; render(SessionState.current); return
        }
        pendingStart = true
        if (!Settings.canDrawOverlays(this)) {
            AlertDialog.Builder(this).setTitle("Cho phép hiển thị bản dịch")
                .setMessage("ScreenLingo dùng quyền hiển thị trên ứng dụng khác để đặt bong bóng và bản dịch lên màn hình. Bật quyền cho ScreenLingo ở trang tiếp theo.")
                .setPositiveButton("Mở cài đặt") { _, _ ->
                    startActivityForResult(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")), 42)
                }.setNegativeButton("Để sau") { _, _ -> pendingStart = false }.show()
            return
        }
        prepareModels(true)
    }

    private fun prepareModels(startAfter: Boolean) {
        if (downloading || SessionState.current.running) return
        if (settings.source == settings.target) { toast("Chọn hai ngôn ngữ khác nhau."); pendingStart = false; return }
        // Check local models before checking connectivity: offline sessions must still start.
        downloading = true
        val token = ++generation
        RemoteModelManager.getInstance().getDownloadedModels(TranslateRemoteModel::class.java)
            .addOnCompleteListener { task ->
                if (token != generation || isDestroyed) return@addOnCompleteListener
                downloading = false
                val installed = if (task.isSuccessful) task.result.map { it.language }.toSet() else emptySet()
                val needed = setOf(settings.source, settings.target) - "en"
                downloadModels(startAfter, installed.containsAll(needed))
            }
    }

    private fun downloadModels(startAfter: Boolean, alreadyDownloaded: Boolean) {
        val connectivity = getSystemService(ConnectivityManager::class.java)
        val caps = connectivity.getNetworkCapabilities(connectivity.activeNetwork)
        if (!alreadyDownloaded && settings.wifiOnly && caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) != true) {
            AlertDialog.Builder(this).setTitle("Tải mô hình dịch")
                .setMessage("Mô hình có thể cần vài chục MB. Cho phép tải qua kết nối hiện tại? Mô hình đã tải sẽ dùng lại khi ngoại tuyến.")
                .setPositiveButton("Cho phép tải") { _, _ -> settings.wifiOnly = false; prepareModels(startAfter) }
                .setNegativeButton("Đợi Wi-Fi") { _, _ -> pendingStart = false }.show()
            return
        }
        downloading = true
        val token = ++generation
        modelLabel.text = "Đang kiểm tra / tải mô hình…"
        status.text = "Lần đầu có thể mất vài phút. Giữ kết nối mạng."
        primary.text = "Huỷ chờ tải mô hình"
        val client = Translation.getClient(TranslatorOptions.Builder().setSourceLanguage(settings.source).setTargetLanguage(settings.target).build())
        translator = client
        client.downloadModelIfNeeded(DownloadConditions.Builder().apply { if (settings.wifiOnly) requireWifi() }.build())
            .addOnSuccessListener {
                client.close()
                if (token != generation || isDestroyed) return@addOnSuccessListener
                translator = null; downloading = false
                modelLabel.text = "✓  Mô hình đã sẵn sàng · dùng được offline"
                render(SessionState.current)
                if (startAfter) requestNotificationAndCapture()
            }.addOnFailureListener {
                client.close()
                if (token != generation || isDestroyed) return@addOnFailureListener
                translator = null; downloading = false; pendingStart = false
                render(SessionState.current)
                status.text = "Chưa tải được mô hình. Kiểm tra mạng rồi thử lại."
                modelLabel.text = "↻  Thử tải lại mô hình"
            }
    }

    private fun requestNotificationAndCapture() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 43)
        } else requestCapture()
    }

    private fun requestCapture() {
        pendingStart = false
        val manager = getSystemService(MediaProjectionManager::class.java)
        val intent = if (Build.VERSION.SDK_INT >= 34) manager.createScreenCaptureIntent(MediaProjectionConfig.createConfigForDefaultDisplay()) else manager.createScreenCaptureIntent()
        startActivityForResult(intent, 44)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 43) requestCapture()
    }

    @Deprecated("Android activity result compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 42 && pendingStart) {
            if (Settings.canDrawOverlays(this)) prepareModels(true)
            else { pendingStart = false; toast("Cần quyền hiển thị để đặt bản dịch lên màn hình.") }
        }
        if (requestCode == 44 && resultCode == RESULT_OK && data != null) {
            startForegroundService(Intent(this, TranslationService::class.java)
                .putExtra("resultCode", resultCode).putExtra("resultData", data))
        }
    }

    private fun render(snapshot: SessionSnapshot) {
        if (!::primary.isInitialized || downloading) return
        badge.text = if (snapshot.running) { if (snapshot.paused) "●  TẠM DỪNG" else "●  ĐANG DỊCH" } else "●  SẴN SÀNG"
        primary.text = if (snapshot.running) "Dừng dịch màn hình  ■" else "Bắt đầu dịch màn hình  →"
        status.text = snapshot.message + (snapshot.latencyMs?.let { " · $it ms · ${snapshot.blockCount} vùng chữ" } ?: "")
        regionSwitch.isEnabled = !snapshot.running
    }
    private fun toast(message: String) { Toast.makeText(this, message, Toast.LENGTH_LONG).show() }
}
