package com.example.homescreen

import android.annotation.TargetApi
import android.content.pm.LauncherApps
import android.os.AsyncTask
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.NestedScrollView
import androidx.recyclerview.widget.RecyclerView


class MainActivity : AppCompatActivity() {

    private var grid: RecyclerView? = null
    private val activeTasks: MutableList<ICancelTask> = mutableListOf()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        this.grid = this.findViewById<RecyclerView>(R.id.appgrid)?.also {
            it.adapter = LauncherEntryManager.get(this).makeLauncherEntryAdapter(this)
        }
    }

    override fun onStart() {
        super.onStart()

        LauncherEntryManager.get(this).entriesReady {
            (this.grid?.layoutManager as GridLayoutManager).onceLayoutCompleted {
                (this.grid?.parent as NestedScrollView).fullScroll(View.FOCUS_DOWN)
            }
        }
    }

    @TargetApi(Build.VERSION_CODES.O)
    override fun onResume() {
        super.onResume()

        val intent = this.intent

        if (intent.action != LauncherApps.ACTION_CONFIRM_PIN_SHORTCUT) {
            (this.grid?.parent as NestedScrollView).fullScroll(View.FOCUS_DOWN)
            return
        }

        val manager = this.getSystemService(LauncherApps::class.java) ?: return
        val request = manager.getPinItemRequest(intent)

        if (!request.isValid) {
            return
        }

        request.shortcutInfo?.let {
            LauncherEntryManager.get(this).addShortcut(it)
                .invokeOnCompletion {
                    (grid?.parent as NestedScrollView).scrollTo(0, 0)
                }
        }

        request.accept()
    }

    override fun onDestroy() {
        super.onDestroy()

        this.activeTasks.forEach {
            if (it.getStatus() != AsyncTask.Status.RUNNING) {
                return@forEach
            }

            it.cancel(true)
        }
    }
}
