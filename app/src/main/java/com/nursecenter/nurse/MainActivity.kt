package com.nursecenter.nurse

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.nursecenter.nurse.data.ChatAlert
import com.nursecenter.nurse.ui.NurseApp
import com.nursecenter.nurse.ui.theme.NurseTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Keep the screen awake while the app is open so incoming requests are never missed behind the lock screen.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (savedInstanceState == null) ChatAlert.handleIntent(intent)
        setContent {
            NurseTheme {
                NurseApp()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        ChatAlert.handleIntent(intent)
    }

    override fun onStart() {
        super.onStart()
        ChatAlert.appVisible = true
    }

    override fun onStop() {
        super.onStop()
        ChatAlert.appVisible = false
    }
}
