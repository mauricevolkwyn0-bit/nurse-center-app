package com.nursecenter.nurse

import android.app.Application
import com.nursecenter.nurse.data.SupabaseAuth

class NurseApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Restore the saved sign-in before anything runs, including a push waking the app while it's closed.
        SupabaseAuth.restore(this)
    }
}
