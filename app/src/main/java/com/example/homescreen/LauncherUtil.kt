package com.example.homescreen

import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.content.pm.ResolveInfo
import android.content.pm.ShortcutInfo

object LauncherUtil {
    fun getInstalledApplications(context: Context): List<ResolveInfo> {
        val mainIntent = Intent(Intent.ACTION_MAIN, null)

        mainIntent.addCategory(Intent.CATEGORY_LAUNCHER)

        return context.packageManager.queryIntentActivities(mainIntent, 0)
    }

    fun getInstalledShortcuts(context: Context): List<ShortcutInfo> {
        val manager = context.getSystemService(LauncherApps::class.java)
        val query = LauncherApps.ShortcutQuery()

        if (!manager.hasShortcutHostPermission()) {
            return emptyList()
        }

        query.setQueryFlags(LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED)

        return manager.getShortcuts(query, android.os.Process.myUserHandle())?: emptyList()
    }
}