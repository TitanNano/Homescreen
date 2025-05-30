package com.example.homescreen

import android.content.Context
import android.view.ContextMenu
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.databinding.DataBindingUtil
import androidx.databinding.ViewDataBinding
import androidx.recyclerview.widget.RecyclerView
import com.example.homescreen.db.UsageDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async

class SettingsEntryAdapter(
    entries: MutableList<LauncherEntry>,
    launcherEntryManager: LauncherEntryManager,
    context: Context,
    coroutineScope: CoroutineScope,
) : LauncherEntryAdapter(entries, launcherEntryManager, context, coroutineScope) {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val context = parent.context
        val inflater = LayoutInflater.from(context)
        val view = DataBindingUtil.inflate<ViewDataBinding>(
            inflater,
            this.getItemLayout(parent),
            parent,
            false
        )

        return SettingsEntryViewHolder(view.root, launcherEntryManager, coroutineScope)
    }

    override fun getItemCount(): Int {
        return this.entries.count()
    }

    override fun getItemLayout(parent: View): Int {
        if (parent.id == R.id.applist) {
            return R.layout.list_item_settings
        }

        return super.getItemLayout(parent)
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = this.entries[position]

        if (holder !is SettingsEntryViewHolder) {
            return
        }

        holder.item = item
    }
}

class SettingsEntryViewHolder(itemView: View, launcherEntryManager: LauncherEntryManager, coroutineScope: CoroutineScope) : LauncherEntryViewHolder(itemView, launcherEntryManager, coroutineScope) {
    init {
        this.itemView.setOnClickListener(this)
    }

    override fun onClick(view: View?) {
        val item = this.item ?: return

        val config = item.config?.apply {
            hidden = !hidden
        } ?: return

        CoroutineScope(Dispatchers.IO).async {
            UsageDatabase.get(itemView.context)
                .iconConfigDao()
                .addConfig(config)

            launcherEntryManager.assembleEntriesAsync().await()
        }
    }

    override fun onLongClick(v: View?): Boolean {
        return false
    }
}