package com.example.homescreen

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.color.DynamicColors
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel

class SettingsActivity : AppCompatActivity() {

    private val coroutineScope = MainScope()
    private lateinit var launcherEntryManager: LauncherEntryManager
    var appList: RecyclerView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        launcherEntryManager = LauncherEntryManager(this, coroutineScope)
        setContentView(R.layout.activity_settings)

        DynamicColors.applyToActivityIfAvailable(this)

        this.appList = this.findViewById<RecyclerView>(R.id.applist).apply {
            this.adapter = launcherEntryManager
                .makeSettingsEntryAdapter(context)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        coroutineScope.cancel()
    }
}