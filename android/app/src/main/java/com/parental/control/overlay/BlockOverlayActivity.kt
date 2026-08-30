package com.parental.control.overlay

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.parental.control.R

class BlockOverlayActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_block_overlay)

        val reason = intent.getStringExtra("EXTRA_REASON") ?: "Dispositivo bloqueado por horario o límite diario."
        findViewById<TextView>(R.id.tv_block_reason).text = reason

        findViewById<Button>(R.id.btn_go_home).setOnClickListener {
            val startMain = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            startActivity(startMain)
            finish()
        }
    }

    override fun onBackPressed() {
        // Impedir botón atrás para saltarse el bloqueo
        val startMain = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(startMain)
    }
}
