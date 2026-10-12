package com.mirko.stickled

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView

class MainActivity : Activity() {

    private lateinit var button: Button
    private lateinit var status: TextView
    private lateinit var swap: Switch

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pad = (24 * resources.displayMetrics.density).toInt()
        status = TextView(this).apply { textSize = 16f; gravity = Gravity.CENTER }
        swap = Switch(this).apply {
            text = "Inverti sinistra/destra"
            isChecked = getPreferences(MODE_PRIVATE).getBoolean("swap", false)
        }
        button = Button(this).apply { setOnClickListener { toggle() } }
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(pad, pad, pad, pad)
            addView(TextView(context).apply { text = "StickLed"; textSize = 28f; gravity = Gravity.CENTER })
            addView(status)
            addView(swap)
            addView(button)
        })
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        val on = SyncService.running
        button.text = if (on) "Ferma" else "Avvia"
        status.text = if (on) "Attivo: avvia il gioco, i LED seguono lo schermo" else "Fermo"
    }

    private fun toggle() {
        if (SyncService.running) {
            stopService(Intent(this, SyncService::class.java))
            button.postDelayed({ refresh() }, 300)
            return
        }
        requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 0)
        getPreferences(MODE_PRIVATE).edit().putBoolean("swap", swap.isChecked).apply()
        startActivityForResult(getSystemService(MediaProjectionManager::class.java).createScreenCaptureIntent(), 1)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode != 1 || resultCode != RESULT_OK || data == null) return
        startForegroundService(
            Intent(this, SyncService::class.java)
                .putExtra(SyncService.EXTRA_CODE, resultCode)
                .putExtra(SyncService.EXTRA_DATA, data)
                .putExtra(SyncService.EXTRA_SWAP, swap.isChecked),
        )
        button.postDelayed({ refresh() }, 500)
    }
}
