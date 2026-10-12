package com.mirko.stickled

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.provider.Settings
import android.widget.Toast
import java.io.FileOutputStream
import java.io.IOException

/** Captures the screen at a tiny size and drives the Odin joystick LEDs (sysfs) with the left/right average colour. */
class SyncService : Service() {

    private var projection: MediaProjection? = null
    private var display: VirtualDisplay? = null
    private var reader: ImageReader? = null
    private var thread: HandlerThread? = null
    private var level = 1f
    private var lastLeft = -1
    private var lastRight = -1
    private var swap = false
    private var lastWrite = 0L
    private var left = 0
    private var right = 0

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (running || intent == null) return START_NOT_STICKY
        val data = intent.getParcelableExtra<Intent>(EXTRA_DATA)
        if (data == null) {
            stopSelf()
            return START_NOT_STICKY
        }
        startForeground(1, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        running = true
        swap = intent.getBooleanExtra(EXTRA_SWAP, false)

        // Same brightness the user chose in the Odin settings (0..1).
        level = Settings.System.getString(contentResolver, KEY_LEVEL)?.toFloatOrNull()?.coerceIn(0f, 1f) ?: 1f
        if (!write(LEFT, "enable", "1") || !write(RIGHT, "enable", "1")) {
            Toast.makeText(this, "Impossibile controllare i LED su questo dispositivo", Toast.LENGTH_LONG).show()
            stopSelf()
            return START_NOT_STICKY
        }

        val t = HandlerThread("stickled").also { it.start(); thread = it }
        val handler = Handler(t.looper)
        val mgr = getSystemService(MediaProjectionManager::class.java)
        val p = mgr.getMediaProjection(intent.getIntExtra(EXTRA_CODE, 0), data)
        if (p == null) {
            stopSelf()
            return START_NOT_STICKY
        }
        projection = p
        p.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() = stopSelf()
        }, handler)

        val dm = resources.displayMetrics
        val w = 96
        val h = (w * dm.heightPixels / dm.widthPixels).coerceAtLeast(2)
        val r = ImageReader.newInstance(w, h, PixelFormat.RGBA_8888, 2)
        reader = r
        display = p.createVirtualDisplay(
            "stickled", w, h, dm.densityDpi, DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            r.surface, null, handler,
        )
        r.setOnImageAvailableListener({ onFrame(it) }, handler)
        return START_NOT_STICKY
    }

    private fun onFrame(r: ImageReader) {
        val img = r.acquireLatestImage() ?: return
        try {
            val now = System.currentTimeMillis()
            if (now - lastWrite < FRAME_MS) return
            val plane = img.planes[0]
            val buf = plane.buffer
            val w = img.width
            val h = img.height
            val half = w / 2
            val sum = LongArray(6)
            for (y in 0 until h) {
                val row = y * plane.rowStride
                for (x in 0 until w) {
                    val i = row + x * 4
                    val o = if (x < half) 0 else 3
                    sum[o] += (buf.get(i).toInt() and 0xff).toLong()
                    sum[o + 1] += (buf.get(i + 1).toInt() and 0xff).toLong()
                    sum[o + 2] += (buf.get(i + 2).toInt() and 0xff).toLong()
                }
            }
            val n = (half * h).coerceAtLeast(1)
            // Smooth (EMA) so the LEDs do not flicker between frames.
            left = smooth(left, boost(sum[0] / n, sum[1] / n, sum[2] / n))
            right = smooth(right, boost(sum[3] / n, sum[4] / n, sum[5] / n))
            val (a, b) = if (swap) right to left else left to right
            if (a == lastLeft && b == lastRight) return
            lastLeft = a
            lastRight = b
            lastWrite = now
            setColor(LEFT, a)
            setColor(RIGHT, b)
        } finally {
            img.close()
        }
    }

    /** The LED driver takes "mode-R:G:B" (mode 1 = static), scaled by the brightness level. */
    private fun setColor(side: String, color: Int) {
        val r = (Color.red(color) * level).toInt()
        val g = (Color.green(color) * level).toInt()
        val b = (Color.blue(color) * level).toInt()
        write(side, "brightness", "1-$r:$g:$b")
    }

    private fun write(side: String, node: String, value: String): Boolean = try {
        FileOutputStream("/sys/class/$side/led/$node").use { it.write(value.toByteArray()) }
        true
    } catch (e: IOException) {
        false
    }

    /** Average colours look washed out on LEDs: push saturation and brightness up. */
    private fun boost(r: Long, g: Long, b: Long): Int {
        val hsv = FloatArray(3)
        Color.RGBToHSV(r.toInt(), g.toInt(), b.toInt(), hsv)
        hsv[1] = (hsv[1] * 1.4f).coerceAtMost(1f)
        hsv[2] = (hsv[2] * 1.5f).coerceAtMost(1f)
        return Color.HSVToColor(hsv)
    }

    private fun smooth(old: Int, new: Int): Int {
        fun mix(shift: Int) = ((((old shr shift) and 0xff) + ((new shr shift) and 0xff) * 2) / 3) shl shift
        return mix(16) or mix(8) or mix(0)
    }

    private fun notification(): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("sync", "StickLed", NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
        )
        return Notification.Builder(this, "sync")
            .setContentTitle("StickLed attivo")
            .setContentText("I LED seguono i colori dello schermo")
            .setSmallIcon(android.R.drawable.presence_online)
            .setContentIntent(open)
            .setOngoing(true)
            .build()
    }

    /** Put back what the Odin settings app has saved: colours, on/off, same as the settings screen would. */
    private fun restore() {
        val on = Settings.System.getString(contentResolver, KEY_ENABLED)?.split(",") ?: listOf("1", "1")
        val colors = Settings.System.getString(contentResolver, KEY_COLORS)?.split(",") ?: return
        listOf(LEFT, RIGHT).forEachIndexed { i, side ->
            val c = runCatching { Color.parseColor(colors[i]) }.getOrNull() ?: return@forEachIndexed
            write(side, "enable", on.getOrElse(i) { "1" })
            setColor(side, c)
        }
    }

    override fun onDestroy() {
        if (running) {
            display?.release()
            reader?.close()
            projection?.stop()
            thread?.quit()
            restore()
        }
        running = false
        super.onDestroy()
    }

    companion object {
        const val EXTRA_CODE = "code"
        const val EXTRA_DATA = "data"
        const val EXTRA_SWAP = "swap"
        const val KEY_COLORS = "joystick_led_light_picker_color"
        const val KEY_ENABLED = "joystick_light_enabled"
        const val KEY_LEVEL = "led_light_brightness_percent"
        const val LEFT = "sn3112l"
        const val RIGHT = "sn3112r"
        // ponytail: ~20 updates/s. Raise if the LEDs look jerky.
        private const val FRAME_MS = 50L

        @Volatile
        var running = false
    }
}
