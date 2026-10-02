package com.nursecenter.nurse.ui.screens

enum class Screen {
    Home, Requests, Schedule, Messages, More, Wallet, Profile, Documents, Reviews, Support, Notifications;

    /** Detail pages opened from the More tab; they hide the app header. */
    val isSubScreen: Boolean get() = this == Wallet || this == Profile || this == Documents || this == Reviews || this == Support

    /** Full-screen pages with their own back button: the More sub-screens and Notifications (opened from the bell). */
    val isDetail: Boolean get() = isSubScreen || this == Notifications
}
