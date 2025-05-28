package com.example.homescreen

import android.app.ActivityOptions
import android.content.Context
import android.content.pm.LauncherApps
import android.content.pm.ShortcutInfo
import android.graphics.Rect
import com.example.homescreen.db.UsageDatabase
import com.example.homescreen.db.UsageEntry
import kotlinx.coroutines.*
import java.lang.ref.WeakReference
import kotlin.math.roundToInt

class LauncherEntryManager(val context: Context, private val coroutineScope: CoroutineScope) {
    private val entries: MutableList<LauncherEntry> = mutableListOf()
    private val allEntries: MutableList<LauncherEntry> = mutableListOf()

    private var activeSortTask: Job? = null
    private var activeAssembleTask: Job? = null
    private val readyCallbackList: MutableList<suspend () -> Unit> = mutableListOf()
    private val entitiesChangedCallbackList: MutableList<() -> Unit> = mutableListOf()
    private val adapters = mutableSetOf<WeakReference<LauncherEntryAdapter>>()

    init {
        assembleEntriesAsync().start()
    }

    fun makeLauncherEntryAdapter(context: Context): LauncherEntryAdapter {
        val adapter = LauncherEntryAdapter(this.entries, launcherEntryManager = this, context, coroutineScope)

        this.adapters.add(WeakReference(adapter))

        return adapter
    }

    fun makeSettingsEntryAdapter(context: Context): SettingsEntryAdapter {
        val adapter = SettingsEntryAdapter(this.allEntries, launcherEntryManager = this, context, coroutineScope)

        this.adapters.add(WeakReference(adapter))

        return adapter
    }

    fun assembleEntriesAsync(): Deferred<Unit> {
        this.activeAssembleTask?.cancel()

        return coroutineScope.async(Dispatchers.IO) {
            doAssembleEntriesAsync().await().let {
                allEntries.apply {
                    clear()
                    addAll(it.first)
                }
                entries.apply {
                    clear()
                    addAll(it.second)
                }
            }

            doSortEntriesAsync().await()

            launch {
                notifyEntitiesChangedAsync().await()
            }

            launch {
                notifyEntriesReadyAsync().await()
            }

            Unit
        }.also {
            it.invokeOnCompletion { throwable ->
                throwable?.let { t -> throw t }
            }

            this.activeAssembleTask = it
        }
    }

    private fun notifyEntitiesChangedAsync() = coroutineScope.async(Dispatchers.Main) {
        adapters.forEach { it.get()?.notifyDataSetChanged() }
        entitiesChangedCallbackList.forEach { it() }
    }

    private fun doAssembleEntriesAsync() = coroutineScope.async(Dispatchers.IO) {

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

        Pair(launcherEntries, launcherEntries.filter {
            it.loadConfig(context)

            !it.config!!.hidden
        })
    }

    fun sortEntriesAsync(): Deferred<Unit> {
        this.activeSortTask?.cancel()

        return coroutineScope.async {
                doSortEntriesAsync().await()
                notifyEntitiesChangedAsync().await()
        }.also {
            this.activeSortTask = it
        }
    }

    private fun doSortEntriesAsync() = coroutineScope.async(Dispatchers.IO) {
        val rawTotalUsage = UsageDatabase.get(context).entryDao()
            .getAllUsage(System.currentTimeMillis() - MONTH_3)
        val rawCurrentUsage = UsageDatabase.get(context).entryDao()
            .getAllUsage(System.currentTimeMillis() - HOURS_24)

        val totalUsage = rawTotalUsage.sumOf { it.usage }.toDouble()
        val currentStatsMap = rawCurrentUsage.associate { Pair(it.id, it.usage) }

        val statsMap = rawTotalUsage.associate {
            val currentUsage = currentStatsMap[it.id] ?: 0
            val usage =
                ((it.usage - currentUsage).toDouble() / totalUsage * 100).roundToInt() + currentUsage

            Pair(it.id, usage)
        }

        entries.onEach {
            it.score = statsMap[it.id] ?: 0
        }.sortByDescending { it.score }
    }

    fun addShortcut(info: ShortcutInfo): Job {
        this.entries.add(LauncherEntry.fromShortcutInfo(info, context))

        return this.sortEntriesAsync()
    }

    fun entriesReady(callback: suspend () -> Unit) {
        this.readyCallbackList.add(callback)
    }

    fun onEntitiesChanged(callback: () -> Unit) {
        this.entitiesChangedCallbackList.add(callback)
    }

    private fun notifyEntriesReadyAsync() = coroutineScope.async(Dispatchers.IO) {
        readyCallbackList.forEach { it() }
    }

    fun searchEntries(queryInput: String): List<LauncherEntry> {
        val query = queryInput.lowercase()

        return this.entries.filter {
            it.name.lowercase().contains(query) ||
                    it.packageName.split(".", ignoreCase = true, limit = 3)
                        .let { parts ->
                            if (parts.size > 2) parts.subList(1, 2) else parts
                        }.any { item ->
                            item.contains(query)
                        }
        }.sortedBy {
            val packageInfo = it.packageName.split(".", ignoreCase = true, limit = 3)
            val org = packageInfo.getOrNull(1)
            val packName = packageInfo.getOrNull(2)

            if (it.name.split("\\s".toRegex()).any { word -> word.lowercase().startsWith(query) }) {
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

    private fun recordUsage(item: LauncherEntry, context: Context, coroutineScope: CoroutineScope) = coroutineScope.async(Dispatchers.IO) {
        UsageDatabase.get(context).entryDao().addUsage(UsageEntry(item.id))
        sortEntriesAsync().await()
    }

    fun launchApplication(item: LauncherEntry, bounds: Rect, options: ActivityOptions, context: Context, scope: CoroutineScope) = scope.async(Dispatchers.Main) {
        val manager = context.getSystemService(LauncherApps::class.java) ?: return@async

        item.shortcutInfo?.let {
            manager.startShortcut(it, bounds, options.toBundle())
            recordUsage(item, context, scope).await()

            return@async
        }

        if (item.componentName == null) {
            return@async
        }

        manager.startMainActivity(item.componentName, android.os.Process.myUserHandle(), bounds, options.toBundle())
        recordUsage(item, context, scope).await()
    }

    companion object {
        const val HOURS_24: Long = 86400 * 1000
        const val MONTH_3: Long = HOURS_24 * 30 * 3
    }
}
