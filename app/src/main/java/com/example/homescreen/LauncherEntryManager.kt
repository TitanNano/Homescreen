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
    private val entitiesChangedCallbackList: MutableList<() -> Unit> = mutableListOf()
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

            notifyEntitiesChangedAsync()
            notifyEntriesReady()
        }.also {
            it.invokeOnCompletion { throwable ->
                throwable?.let { t -> throw t }
            }

            this.activeAssembleTask = it
        }
    }

    fun assembleEntries(): Job = this.assembleEntriesAsync()

    private fun notifyEntitiesChangedAsync() = MainScope().async {
        adapters.forEach { it.get()?.notifyDataSetChanged() }
        entitiesChangedCallbackList.forEach { it() }
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
            notifyEntitiesChangedAsync().await()
        }.also {
            this.activeSortTask = it
        }
    }

    private suspend fun doSortEntries() = withContext(Dispatchers.Default) {
        val rawTotalUsage = UsageDatabase.get(context).entryDao()
            .getAllUsage(System.currentTimeMillis() - MONTH_3)
        val rawCurrentUsage = UsageDatabase.get(context).entryDao()
            .getAllUsage(System.currentTimeMillis() - HOURS_24)

        val totalUsage = rawTotalUsage.map { it.usage }.sum().toDouble()
        val currentStatsMap = rawCurrentUsage
            .map { Pair(it.id, it.usage) }
            .toMap()

        val statsMap = rawTotalUsage
            .map {
                val currentUsage = currentStatsMap.get(it.id) ?: 0
                val usage =
                    Math.round((it.usage - currentUsage).toDouble() / totalUsage * 100) + currentUsage

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

    fun onEntitiesChanged(callback: () -> Unit) {
        this.entitiesChangedCallbackList.add(callback)
    }

    private suspend fun notifyEntriesReady() {
        readyCallbackList.forEach { it() }
    }

    fun searchEntries(query: String): List<LauncherEntry> {
        val query = query.lowercase()

        return this.entries.filter {
            it.name.lowercase().contains(query) ||
                    it.packageName.split(".", ignoreCase = true, limit = 3).let {
                        if (it.size > 2) it.subList(1, 2) else it
                    }.any { item ->
                        item.contains(query)
                    }
        }.sortedBy {
            val packageInfo = it.packageName.split(".", ignoreCase = true, limit = 3)
            val org = packageInfo.getOrNull(1)
            var packName = packageInfo.getOrNull(2)

            if (it.name.lowercase().startsWith(query)) {
                0
            } else if (packName?.lowercase()?.startsWith(query) == true) {
                1
            } else if (org?.lowercase()?.startsWith(query) == true) {
                2
            } else {
                3
            }
        }
    }

    companion object {
        private var instance: LauncherEntryManager? = null

        const val HOURS_24: Long = 86400 * 1000
        const val MONTH_3: Long = HOURS_24 * 30 * 3

        fun get(context: Context): LauncherEntryManager {
            this.instance?.let { return it }

            return LauncherEntryManager(context).also {
                this.instance = it

                it.assembleEntries()
            }
        }
    }
}
