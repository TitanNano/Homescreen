package com.example.homescreen

import android.os.Bundle
import android.view.View
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.snackbar.Snackbar
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.NestedScrollView
import androidx.recyclerview.widget.RecyclerView

class SettingsActivity : AppCompatActivity() {

    var appList: RecyclerView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        setSupportActionBar(findViewById(R.id.topAppBar))

        this.appList = this.findViewById<RecyclerView>(R.id.applist).apply {
            this.adapter = LauncherEntryManager.get(context)
                .apply {
                    filterHiddenEntries = false
                    assembleEntries()
                }.makeSettingsEntryAdapter(context)
        }
    }
}