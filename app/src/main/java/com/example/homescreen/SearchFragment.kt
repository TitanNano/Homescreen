package com.example.homescreen

import android.animation.Animator
import android.animation.AnimatorSet
import android.animation.ValueAnimator
import android.app.ActivityOptions
import android.content.Context
import android.graphics.Rect
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.util.Log
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowInsetsAnimation
import android.view.inputmethod.InputMethodManager
import androidx.annotation.RequiresApi
import androidx.appcompat.widget.AppCompatEditText
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.WindowInsetsCompat
import androidx.databinding.BindingAdapter
import androidx.databinding.ObservableInt
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.RecyclerView
import com.example.homescreen.databinding.LauncherEntryBinding
import com.example.homescreen.databinding.SearchFragmentBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlin.math.min


class SearchFragment(private val launcherEntryManager: LauncherEntryManager, private val coroutineScope: CoroutineScope) : Fragment(R.layout.search_fragment) {

    private lateinit var binding: SearchFragmentBinding

    var searchText: String = ""
    private val searchResults: MutableList<LauncherEntry> = mutableListOf()
    val searchResultsAdapter = SearchResultAdapter(this.searchResults, launcherEntryManager)
    private val itemCount = 4
    lateinit var overlayColorDrawable: ColorDrawable
    val overlayAlpha = ObservableInt(0)
    val keyboardHeight: ObservableInt = ObservableInt(0)
    var enableExitAnimation = false
    var isCanceled = false

    fun onKey(v: View?, keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode != KeyEvent.KEYCODE_ENTER) {
            return false
        }

        val viewHolder = this@SearchFragment.binding.results.findViewHolderForAdapterPosition(0) ?: return false

        (viewHolder as SearchResultViewHolder).onClick(viewHolder.itemView)
        return true
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        this.binding = SearchFragmentBinding.inflate(inflater)
        this.binding.fragment = this

        return this.binding.root
    }

    @RequiresApi(Build.VERSION_CODES.R)
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        this.launcherEntryManager.onEntitiesChanged {
            this.searchEntries()
        }

        this.overlayColorDrawable = ResourcesCompat.getDrawable(
            resources,
            R.drawable.search_background_overlay,
            requireContext().theme
        ) as ColorDrawable

        this.requireActivity().window.decorView.setWindowInsetsAnimationCallback(object :
            WindowInsetsAnimation.Callback(DISPATCH_MODE_CONTINUE_ON_SUBTREE) {
            override fun onProgress(
                insets: WindowInsets,
                runningAnimations: MutableList<WindowInsetsAnimation>
            ): WindowInsets {
                this@SearchFragment.updateKeyboardHeight(insets)
                return insets
            }

        })
    }

    fun enterAnimation(reverse: Boolean): AnimatorSet {
        val duration: Long = 400

        var searchStart = -100f * resources.displayMetrics.density
        var searchEnd = 0f * resources.displayMetrics.density
        var bgStart = 0
        var bgEnd = 150

        if (reverse) {
            searchStart = searchEnd.also { searchEnd = searchStart }
            bgStart = bgEnd.also { bgEnd = bgStart }
        }

        val search = ValueAnimator.ofFloat(searchStart, searchEnd).apply {
            addUpdateListener { updatedAnimation ->
                this@SearchFragment.binding.searchBar.translationY =
                    updatedAnimation.animatedValue as Float
            }

            this.duration = duration
        }

        val bg = ValueAnimator.ofInt(bgStart, bgEnd).apply {
            addUpdateListener { updatedAnimation ->
                this@SearchFragment.updateOverlayAlpha(updatedAnimation.animatedValue as Int)
            }

            this.duration = duration
        }

        return AnimatorSet().also { anim ->
            anim.playTogether(search, bg)
        }
    }

    private fun searchEntries() {
        val oldResults = this.searchResults.toList()
        val oldCount = oldResults.size
        this.searchResults.clear()

        if (this.searchText.isEmpty()) {
            this.searchResultsAdapter.notifyItemRangeRemoved(0, oldCount)
            return
        }

        this.searchResults.addAll(this.launcherEntryManager.searchEntries(this.searchText).let {
            it.slice(0 until min(it.size, this.itemCount))
        })

        if (this.searchResults.size == 0) {
            this.searchResultsAdapter.notifyItemRangeRemoved(0, oldCount)
            return
        }

        if (this.searchResults.size < oldCount) {
            this.searchResultsAdapter.notifyItemRangeRemoved(
                this.searchResults.size - 1,
                oldCount - this.searchResults.size
            )
        }

        if (this.searchResults.size > oldCount) {
            this.searchResultsAdapter.notifyItemRangeInserted(
                oldCount,
                this.searchResults.size - oldCount
            )
        }

        oldResults.forEachIndexed { index, launcherEntry ->
            if (this.searchResults.getOrNull(index) == null || this.searchResults[index].id == launcherEntry.id) {
                return@forEachIndexed
            }

            this.searchResultsAdapter.notifyItemChanged(index)
        }
    }

    fun onSearchTextChange(editable: Editable?) {
        this.searchEntries()
    }

    fun focusInput() {
        this.binding.searchBar.requestFocus()
        val imm = this.requireContext().getSystemService(InputMethodManager::class.java) ?: return

        imm.showSoftInput(this.binding.searchInput, 0)
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)

        this.searchText = ""
        this.searchResults.clear()
    }

    fun updateOverlayAlpha(alpha: Int) {
        this.overlayColorDrawable.let {
            it.alpha = alpha
            it.color
        }.let {
            this.overlayAlpha.set(it)
        }
    }


    fun updateKeyboardHeight(insets: WindowInsets) {
        val compatInsets = WindowInsetsCompat.toWindowInsetsCompat(insets)
        val height = compatInsets.getInsets(WindowInsetsCompat.Type.ime()).bottom

        // navigate back when the keyboard moves out of view
        if (height < this.keyboardHeight.get()) {
            Log.i("SearchView", "keyboard is hiding")
            this.context?.let{ it as MainActivity }?.supportFragmentManager?.popBackStackImmediate()
        }

        this.keyboardHeight.set(height)
    }

    override fun onCreateAnimator(transit: Int, enter: Boolean, nextAnim: Int): Animator? {
        if (!enter && enableExitAnimation) {
            enableExitAnimation = false
            return this.enterAnimation(true)
        }

        return super.onCreateAnimator(transit, enter, nextAnim)
    }

    companion object {
        @BindingAdapter("onKeyListener")
        @JvmStatic
        fun setOnKeyListener(view: AppCompatEditText, listener: View.OnKeyListener) {
            view.setOnKeyListener(listener)
        }
    }
}

class SearchResultAdapter(private val entries: MutableList<LauncherEntry>, private val launcherEntryManager: LauncherEntryManager) :
    RecyclerView.Adapter<SearchResultViewHolder>() {

    init {
        setHasStableIds(true)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SearchResultViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.launcher_entry, null)

        return SearchResultViewHolder(view, launcherEntryManager)
    }

    override fun getItemCount(): Int {
        return this.entries.size
    }

    override fun onBindViewHolder(holder: SearchResultViewHolder, position: Int) {
        holder.item = this.entries[position]
    }

    override fun getItemId(position: Int): Long {
        val id = this.entries[position].id

        if (!idSet.contains(id)) {
            idSet.add(id)
        }

        return idSet.indexOf(id).toLong()
    }

    companion object {
        val idSet: LinkedHashSet<String> = linkedSetOf()
    }
}

class SearchResultViewHolder(itemView: View, private val launcherEntryManager: LauncherEntryManager) : RecyclerView.ViewHolder(itemView) {
    private val binding: LauncherEntryBinding = LauncherEntryBinding.bind(itemView)
    private val coroutineScope = CoroutineScope(Dispatchers.IO)

    var item: LauncherEntry?
        get() {
            return this.binding.item
        }
        set(value) {
            this.binding.item = value
        }

    init {
        this.binding.root.setOnClickListener {
            this.onClick(it)
        }
    }

    fun onClick(view: View?) {
        if (this.layoutPosition == RecyclerView.NO_POSITION || view == null) {
            return
        }

        val bounds = Rect()
        val item = this.item ?: return

        view.getDrawingRect(bounds)

        val options = ActivityOptions.makeClipRevealAnimation(
            view,
            0,
            0,
            bounds.height(),
            bounds.width()
        )

        (view.context as MainActivity).apply {
            supportFragmentManager.popBackStackImmediate()
            blurLauncher(0f)
        }

        launcherEntryManager.launchApplication(item, bounds, options, view.context, coroutineScope).start()
    }
}