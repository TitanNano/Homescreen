package com.example.homescreen.tasks

import android.app.ActivityOptions
import android.content.Context
import android.content.pm.LauncherApps
import android.graphics.Rect
import android.os.AsyncTask
import com.example.homescreen.ICancelTask
import com.example.homescreen.LauncherEntry
import com.example.homescreen.LauncherEntryManager
import com.example.homescreen.db.UsageDatabase
import com.example.homescreen.db.UsageEntry

class LaunchApplication(private val context: Context, val item: LauncherEntry, private val bounds: Rect, val options: ActivityOptions):
    AsyncTask<Unit, Int, Boolean>(), ICancelTask {

    private fun recordUsage(item: LauncherEntry) {
        UsageDatabase.get(this.context).entryDao().addUsage(UsageEntry(item.id))
        LauncherEntryManager.get(this.context).sortEntries()
    }

    override fun doInBackground(vararg params: Unit?): Boolean {

        val item = this.item
        val manager = this.context.getSystemService(LauncherApps::class.java) ?: return false

        item.shortcutInfo?.let {
            manager.startShortcut(it, bounds, options.toBundle())
            recordUsage(item)

            return true
        }

        if (item.componentName == null) {
            return false
        }

        manager.startMainActivity(item.componentName, android.os.Process.myUserHandle(), bounds, options.toBundle())
        recordUsage(item)

        return true
    }
}