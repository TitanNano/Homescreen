package com.example.homescreen

import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.net.Uri
import android.provider.Settings
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import androidx.databinding.DataBindingUtil
import androidx.databinding.ViewDataBinding
import androidx.recyclerview.widget.RecyclerView
import com.example.homescreen.db.UsageDatabase
import com.example.homescreen.tasks.LaunchApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import java.lang.Integer.min

open class LauncherEntryViewHolder(itemView: View) : RecyclerView.ViewHolder((itemView)), View.OnClickListener, View.OnLongClickListener,
    PopupMenu.OnMenuItemClickListener {
    private var binding = DataBindingUtil.getBinding<ViewDataBinding>(this.itemView)

    var item: LauncherEntry? = null
        get() = field

        set(value) {
            if (this.binding != null) {
                this.binding!!.setVariable(BR.item, value)
            }

            field = value
        }

    init {
        this.itemView.setOnClickListener(this)
        this.itemView.setOnLongClickListener(this)
    }

    override fun onClick(view: View?) {
        if (this.adapterPosition == RecyclerView.NO_POSITION || view == null) {
            return
        }

        val bounds = Rect()
        val item = this.item?: return

        view.getDrawingRect(bounds)

        val options = ActivityOptions.makeClipRevealAnimation(
            view,
            0,
            0,
            bounds.height(),
            bounds.width()
        )

        LaunchApplication(view.context, item, bounds, options).execute()
    }

    override fun onLongClick(v: View?): Boolean {
        if (v == null) {
            return false
        }

        val popupMenu = PopupMenu(v.context, v)

        popupMenu.menuInflater.inflate(R.menu.context_menu, popupMenu.menu)
        popupMenu.show()
        popupMenu.setOnMenuItemClickListener(this)

        return true
    }

    override fun onMenuItemClick(menuItem: MenuItem): Boolean {
        if (menuItem.itemId == R.id.hide) {
            return this.onMenuItemHide()
        }

        if (menuItem.itemId == R.id.info) {
            return this.onMenuItemInfo()
        }

        if (menuItem.itemId == R.id.settings) {
            return this.onMenuItemSettings()
        }

        return false
    }

    private fun onMenuItemHide(): Boolean {
        val item = this.item ?: return false

        val config = item.config?.apply {
            hidden = true
        } ?: return false

        CoroutineScope(Dispatchers.IO).async {
            UsageDatabase.get(itemView.context)
                .iconConfigDao()
                .addConfig(config)

            LauncherEntryManager.get(itemView.context).assembleEntries().join()
        }

        return true
    }

    private fun onMenuItemInfo(): Boolean {
        val item = item ?: return false
        val options = ActivityOptions.makeScaleUpAnimation(itemView, 0, 0, 0, 0)
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            .apply {
                addCategory(Intent.CATEGORY_DEFAULT)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                data = Uri.parse("package:${item.packageName}")
            }

        itemView.context.startActivity(intent, options.toBundle())

        return true
    }

    private fun onMenuItemSettings(): Boolean {
        val item = this.item ?: return false
        val intent = Intent(this.itemView.context, SettingsActivity::class.java)
            .apply {
                addCategory(Intent.CATEGORY_PREFERENCE)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
            }

        this.itemView.context.startActivity(intent, ActivityOptions.makeBasic().toBundle())

        return true
    }

}

open class LauncherEntryAdapter(
    protected val entries: MutableList<LauncherEntry>,
    val context: Context
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    init {
        this.setHasStableIds(true)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val context = parent.context
        val inflater = LayoutInflater.from(context)
        val view = DataBindingUtil.inflate<ViewDataBinding>(
            inflater,
            this.getItemLayout(parent),
            parent,
            false
        )

        return LauncherEntryViewHolder(view.root)
    }

    override fun getItemCount(): Int {
        return min(this.entries.count(), 8)
    }

    override fun getItemId(position: Int): Long {
        val id = this.entries[position].id

        if (!idSet.contains(id)) {
            idSet.add(id)
        }

        return idSet.indexOf(id).toLong()
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = this.entries[position]

        if (holder !is LauncherEntryViewHolder) {
            return
        }

        holder.item = item
    }

    companion object {
        val idSet: LinkedHashSet<String> = linkedSetOf()
    }

    protected open fun getItemLayout(parent: View): Int {
        if (parent.id == R.id.appgrid) {
            return R.layout.launcher_entry
        }

        return 0
    }
}