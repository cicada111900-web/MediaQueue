package com.example.mediaqueue

import android.app.Service
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import android.widget.TextView
import android.widget.Toast

class FloatingButtonService : Service() {

    private lateinit var windowManager: WindowManager
    private var floatingButton: TextView? = null
    private var startX = 0
    private var startY = 0
    private var touchX = 0f
    private var touchY = 0f

    override fun onCreate() {
        super.onCreate()

        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return
        }

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        val button = TextView(this).apply {
            text = "MQ"
            textSize = 16f
            setTextColor(0xFFFFFFFF.toInt())
            setBackgroundColor(0xFF2525A0.toInt())
            gravity = Gravity.CENTER
            setPadding(18, 14, 18, 14)
            elevation = 12f
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 20
            y = 250
        }

        button.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = params.x
                    startY = params.y
                    touchX = event.rawX
                    touchY = event.rawY
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    params.x = startX + (event.rawX - touchX).toInt()
                    params.y = startY + (event.rawY - touchY).toInt()
                    windowManager.updateViewLayout(button, params)
                    true
                }

                MotionEvent.ACTION_UP -> {
                    val moved = kotlin.math.abs(event.rawX - touchX) > 10 ||
                        kotlin.math.abs(event.rawY - touchY) > 10

                    if (!moved) {
                        openClipboardLink()
                    }
                    true
                }

                else -> false
            }
        }

        try {
            windowManager.addView(button, params)
            floatingButton = button
        } catch (_: Exception) {
            stopSelf()
        }
    }

    private fun openClipboardLink() {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE)
            as ClipboardManager

        val clip: ClipData? = clipboard.primaryClip
        val text = clip?.takeIf { it.itemCount > 0 }
            ?.getItemAt(0)?.coerceToText(this)?.toString()?.trim()

        if (text.isNullOrBlank()) {
            Toast.makeText(this, "Copy a link first", Toast.LENGTH_SHORT).show()
            return
        }

        val url = Regex("https?://\\S+").find(text)?.value

        if (url == null) {
            Toast.makeText(this, "No link found in clipboard", Toast.LENGTH_SHORT).show()
            return
        }

        val intent = Intent(this, MainActivity::class.java).apply {
            putExtra(Intent.EXTRA_TEXT, url)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }

        startActivity(intent)
    }

    override fun onDestroy() {
        floatingButton?.let {
            try {
                windowManager.removeView(it)
            } catch (_: Exception) {
            }
        }
        floatingButton = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
