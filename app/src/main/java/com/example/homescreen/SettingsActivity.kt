package com.example.homescreen

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.color.DynamicColors

class SettingsActivity : AppCompatActivity() {

    var appList: RecyclerView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        DynamicColors.applyToActivityIfAvailable(this)

        this.appList = this.findViewById<RecyclerView>(R.id.applist).apply {
            this.adapter = LauncherEntryManager
                .get(context)
                .makeSettingsEntryAdapter(context)
        }
    }
}