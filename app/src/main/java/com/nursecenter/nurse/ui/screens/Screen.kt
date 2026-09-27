package com.nursecenter.nurse.ui.screens

enum class Screen {
    Home, Requests, Schedule, Messages, More, Wallet, Profile, Documents, Reviews, Support;

    /** Detail pages opened from the More tab; they hide the app header. */
    val isSubScreen: Boolean get() = this == Wallet || this == Profile || this == Documents || this == Reviews || this == Support
}
