package com.nursecenter.nurse

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.nursecenter.nurse.ui.NurseApp
import com.nursecenter.nurse.ui.theme.NurseTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Keep the screen awake while the app is open so incoming requests are never missed behind the lock screen.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContent {
            NurseTheme {
                NurseApp()
            }
        }
    }
}
