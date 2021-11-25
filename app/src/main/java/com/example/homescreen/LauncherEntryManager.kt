package com.example.homescreen

import android.content.Context
import android.content.pm.ShortcutInfo
import com.example.homescreen.db.UsageDatabase
import kotlinx.coroutines.*
import java.lang.ref.WeakReference

class LauncherEntryManager(val context: Context) {
    private val entries: MutableList<LauncherEntry> = mutableListOf()

    private var activeSortTask: Job? = null
    private var activeAssembleTask: Job? = null
    private val readyCallbackList: MutableList<suspend () -> Unit> = mutableListOf()
    private val adapters = mutableSetOf<WeakReference<LauncherEntryAdapter>>()

    var filterHiddenEntries = true

    fun makeLauncherEntryAdapter(context: Context): LauncherEntryAdapter {
        val adapter = LauncherEntryAdapter(this.entries, context)

        this.adapters.add(WeakReference(adapter))

        return adapter
    }

    fun makeSettingsEntryAdapter(context: Context): SettingsEntryAdapter {
        val adapter = SettingsEntryAdapter(this.entries, context)

        this.adapters.add(WeakReference(adapter))

        return adapter
    }

    private fun assembleEntriesAsync(): Deferred<Unit> {
        this.activeAssembleTask?.cancel()

        return GlobalScope.async {
            entries.clear()
            entries.addAll(doAssembleEntries())

            doSortEntries()

            notifyDataSetChangeAsync()
            notifyEntriesReady()
        }.also {
            it.invokeOnCompletion {
                it?.let { throw it }
            }

            this.activeAssembleTask = it
        }
    }

    fun assembleEntries(): Job = this.assembleEntriesAsync()

    private fun notifyDataSetChangeAsync() = MainScope().async {
        adapters.forEach { it.get()?.notifyDataSetChanged() }
    }

    private suspend fun doAssembleEntries() = withContext(Dispatchers.IO) {

        val (applicationList, shortcutList) = kotlin.run {
            val applicationList = async { LauncherUtil.getInstalledApplications(context) }
            val shortcutList = async { LauncherUtil.getInstalledShortcuts(context) }

            Pair(applicationList.await(), shortcutList.await())
        }

        val launcherEntries = emptyList<LauncherEntry>().toMutableList()
        val jobs = listOf(
            shortcutList.map { async { LauncherEntry.fromShortcutInfo(it, context) } },
            applicationList.map { async { LauncherEntry.fromResolveInfo(it, context) } }
        ).flatten()

        launcherEntries.addAll(jobs.awaitAll())

        launcherEntries.filter {
            it.loadConfig(context)

            !filterHiddenEntries || !it.config!!.hidden
        }
    }

    fun sortEntries(): Deferred<Unit> {
        this.activeSortTask?.cancel()

        return GlobalScope.async {
            doSortEntries()
            notifyDataSetChangeAsync().await()
        }.also {
            this.activeSortTask = it
        }
    }

    private suspend fun doSortEntries() = withContext(Dispatchers.Default) {
        val rawTotalUsage = UsageDatabase.get(context).entryDao()
            .getAllUsage(System.currentTimeMillis() - MONTH_3)
        val rawCurrentUsage = UsageDatabase.get(context).entryDao()
            .getAllUsage(System.currentTimeMillis() - HOURS_24)

        val totalUsage = rawTotalUsage.map{ it.usage }.sum().toDouble()
        val currentStatsMap = rawCurrentUsage
            .map { Pair(it.id, it.usage) }
            .toMap()

        val statsMap = rawTotalUsage
            .map {
                val currentUsage = currentStatsMap.get(it.id) ?: 0
                val usage = Math.round((it.usage - currentUsage).toDouble() / totalUsage * 100) + currentUsage

                Pair(it.id, usage)
            }.toMap()

        entries.onEach {
            it.score = statsMap.get(it.id) ?: 0
        }.sortByDescending { it.score }

    }

    fun addShortcut(info: ShortcutInfo): Job {
        this.entries.add(LauncherEntry.fromShortcutInfo(info, context))

        return this.sortEntries()
    }

    fun entriesReady(callback: suspend () -> Unit) {
        this.readyCallbackList.add(callback)
    }

    private suspend fun notifyEntriesReady() {
        readyCallbackList.forEach { it() }
    }

    companion object {
        private var instance: LauncherEntryManager? = null

        const val HOURS_24: Long =  86400 * 1000
        const val MONTH_3:  Long = HOURS_24 * 30 * 3

        fun get(context: Context): LauncherEntryManager {
            this.instance?.let { return it }

            return LauncherEntryManager(context).also {
                this.instance = it

                it.assembleEntries()
            }
        }
    }
}