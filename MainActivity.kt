package com.sketchtrace.app

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import android.widget.FrameLayout
import kotlin.math.max

class MainActivity : AppCompatActivity() {
    private lateinit var previewView: PreviewView
    private lateinit var overlay: ImageView
    private lateinit var status: TextView
    private var zoom = 1f
    private var rotation = 0f

    private val imagePicker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            overlay.setImageURI(uri)
            overlay.visibility = View.VISIBLE
            status.text = "الصورة جاهزة — حرّكها وكبّرها فوق الورقة"
        }
    }

    private val cameraPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startCamera() else status.text = "يجب السماح باستعمال الكاميرا لتشغيل الرسم"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(9, 13, 24)
        window.navigationBarColor = Color.rgb(9, 13, 24)
        buildInterface()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        } else {
            cameraPermission.launch(Manifest.permission.CAMERA)
        }
    }

    private fun buildInterface() {
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(9, 13, 24))
            setPadding(dp(14), dp(12), dp(14), dp(10))
        }

        val title = TextView(this).apply {
            text = "SketchTrace"
            textSize = 26f
            setTextColor(Color.rgb(64, 190, 255))
            gravity = Gravity.CENTER
            setPadding(0, dp(4), 0, dp(2))
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
        page.addView(title, LinearLayout.LayoutParams(-1, dp(42)))

        val subtitle = TextView(this).apply {
            text = "ارسم صورك الحقيقية باستعمال الكاميرا"
            textSize = 13f
            setTextColor(Color.LTGRAY)
            gravity = Gravity.CENTER
        }
        page.addView(subtitle, LinearLayout.LayoutParams(-1, dp(28)))

        val stage = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
        }
        previewView = PreviewView(this).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
        stage.addView(previewView, FrameLayout.LayoutParams(-1, -1))

        overlay = ImageView(this).apply {
            visibility = View.GONE
            scaleType = ImageView.ScaleType.FIT_CENTER
            alpha = 0.55f
            setOnTouchListener(object : View.OnTouchListener {
                var lastX = 0f
                var lastY = 0f
                override fun onTouch(v: View, event: android.view.MotionEvent): Boolean {
                    when (event.action) {
                        android.view.MotionEvent.ACTION_DOWN -> {
                            lastX = event.rawX; lastY = event.rawY
                            return true
                        }
                        android.view.MotionEvent.ACTION_MOVE -> {
                            val dx = event.rawX - lastX
                            val dy = event.rawY - lastY
                            v.translationX += dx
                            v.translationY += dy
                            lastX = event.rawX; lastY = event.rawY
                            return true
                        }
                    }
                    return true
                }
            })
        }
        stage.addView(overlay, FrameLayout.LayoutParams(-1, -1))
        page.addView(stage, LinearLayout.LayoutParams(-1, 0, 1f))

        status = TextView(this).apply {
            text = "اختر صورة لبدء الرسم"
            textSize = 13f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(0, dp(8), 0, dp(4))
        }
        page.addView(status, LinearLayout.LayoutParams(-1, dp(34)))

        val choose = makeButton("اختيار صورة من الهاتف") {
            imagePicker.launch("image/*")
        }
        page.addView(choose, LinearLayout.LayoutParams(-1, dp(48)))

        addSlider(page, "شفافية الصورة", 10, 100, 55) {
            overlay.alpha = it / 100f
        }
        addSlider(page, "حجم الصورة", 50, 200, 100) {
            zoom = it / 100f
            overlay.scaleX = zoom
            overlay.scaleY = zoom
        }

        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        val rotate = makeButton("تدوير ↻") {
            rotation = (rotation + 15f) % 360f
            overlay.rotation = rotation
        }
        val reset = makeButton("إعادة ضبط") {
            zoom = 1f; rotation = 0f
            overlay.scaleX = 1f; overlay.scaleY = 1f
            overlay.rotation = 0f
            overlay.translationX = 0f; overlay.translationY = 0f
            overlay.alpha = 0.55f
            status.text = "تمت إعادة ضبط الصورة"
        }
        actions.addView(rotate, LinearLayout.LayoutParams(0, dp(46), 1f).apply { marginEnd = dp(5) })
        actions.addView(reset, LinearLayout.LayoutParams(0, dp(46), 1f).apply { marginStart = dp(5) })
        page.addView(actions)

        val note = TextView(this).apply {
            text = "ضع الهاتف ثابتاً فوق الورقة للحصول على نتيجة أفضل"
            textSize = 11f
            setTextColor(Color.GRAY)
            gravity = Gravity.CENTER
            setPadding(0, dp(7), 0, 0)
        }
        page.addView(note, LinearLayout.LayoutParams(-1, dp(24)))

        setContentView(page)
    }

    private fun addSlider(parent: LinearLayout, label: String, min: Int, max: Int, initial: Int, onChange: (Int) -> Unit) {
        val wrap = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val text = TextView(this).apply {
            this.text = "$label: $initial%"
            textSize = 12f
            setTextColor(Color.WHITE)
        }
        val seek = SeekBar(this).apply {
            this.max = max - min
            progress = initial - min
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    val value = progress + min
                    text.text = "$label: $value%"
                    onChange(value)
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        }
        wrap.addView(text)
        wrap.addView(seek)
        parent.addView(wrap, LinearLayout.LayoutParams(-1, dp(58)))
    }

    private fun makeButton(label: String, action: () -> Unit): Button {
        return Button(this).apply {
            text = label
            textSize = 13f
            isAllCaps = false
            setTextColor(Color.WHITE)
            setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.rgb(22, 105, 165)))
            setOnClickListener { action() }
        }
    }

    private fun startCamera() {
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            try {
                val provider = future.get()
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }
                provider.unbindAll()
                provider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview)
                status.text = "الكاميرا تعمل — اختر صورة وابدأ التتبع"
            } catch (e: Exception) {
                status.text = "تعذّر تشغيل الكاميرا على هذا الجهاز"
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
