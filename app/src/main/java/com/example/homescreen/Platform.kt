package com.example.homescreen

object Platform {
    val isApi26: Boolean
        get() = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O
}