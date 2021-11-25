package com.example.homescreen

import android.content.ComponentName
import android.content.Context
import android.content.pm.*
import android.graphics.drawable.Drawable
import com.example.homescreen.db.IconConfig
import com.example.homescreen.db.UsageDatabase

class LauncherEntry (
    val id: String,
    val name: String,
    val componentName: ComponentName?,
    val icon: Drawable,
    val shortcutInfo: ShortcutInfo?,
    val ownerIcon: Drawable?,
    var score: Long = 0
) {
    var config: IconConfig? = null

    val packageName: String
        get() = this.componentName?.packageName ?: this.shortcutInfo?.`package` ?: ""

    fun loadConfig(context: Context): LauncherEntry {
        val config = UsageDatabase.get(context).iconConfigDao().getConfig(this.id) ?: IconConfig(this.id)

        this.config = config

        return this
    }

    companion object {
        fun fromShortcutInfo(value: ShortcutInfo, context: Context): LauncherEntry {
            val manager = context.getSystemService(LauncherApps::class.java)!!

            val id = value.id
            val name = value.shortLabel.toString()
            val icon = manager.getShortcutIconDrawable(value, 0)
            val userHandle = android.os.Process.myUserHandle()

            val ownerIcon: Drawable? = if (Platform.isApi26) {
                manager.getApplicationInfo(value.`package`, 0, userHandle).let {
                    return@let context.packageManager.getApplicationIcon(it)
                }
            } else { null }

            return LauncherEntry(id, name, null, icon, value, ownerIcon)
        }

        fun fromResolveInfo(value: ResolveInfo, context: Context): LauncherEntry {
            val name = value.loadLabel(context.packageManager).toString()
            val packageName = value.activityInfo.packageName
            val componentName = ComponentName(packageName, value.activityInfo.name)
            val icon = context.packageManager.getActivityIcon(componentName)

            return LauncherEntry(componentName.flattenToString(), name, componentName, icon, null, null)
        }
    }
}