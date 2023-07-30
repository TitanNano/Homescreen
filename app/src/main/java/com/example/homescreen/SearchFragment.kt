package com.example.homescreen

import android.animation.Animator
import android.animation.AnimatorSet
import android.animation.ValueAnimator
import android.app.ActivityOptions
import android.content.Context
import android.graphics.Rect
import android.os.Bundle
import android.text.Editable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.RecyclerView
import androidx.transition.Transition
import androidx.transition.TransitionValues
import com.example.homescreen.databinding.LauncherEntryBinding
import com.example.homescreen.databinding.SearchFragmentBinding
import com.example.homescreen.tasks.LaunchApplication
import kotlin.math.min

class SearchFragment : Fragment(R.layout.search_fragment) {

    private lateinit var binding: SearchFragmentBinding
    private lateinit var entryManager: LauncherEntryManager

    var searchText: String = ""
    private val searchResults: MutableList<LauncherEntry> = mutableListOf()
    val searchResultsAdapter = SearchResultAdapter(this.searchResults)
    private val itemCount = 4

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        this.binding = SearchFragmentBinding.inflate(inflater)
        this.binding.fragment = this

        this.returnTransition = object : Transition() {
            override fun captureStartValues(transitionValues: TransitionValues) {
            }

            override fun captureEndValues(transitionValues: TransitionValues) {
            }

            override fun isTransitionRequired(
                startValues: TransitionValues?,
                endValues: TransitionValues?
            ): Boolean {
                return true
            }

            override fun createAnimator(
                sceneRoot: ViewGroup,
                startValues: TransitionValues?,
                endValues: TransitionValues?
            ): Animator {
                return this@SearchFragment.enterAnimation(reverse = true)
            }
        }

        return this.binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        this.entryManager = LauncherEntryManager.get(this.requireContext()).also {
            it.onEntitiesChanged {
                this.searchEntries()
            }
        }
    }

    fun enterAnimation(reverse: Boolean): AnimatorSet {
        val duration: Long = 500

        var blurStart = 1f
        var blurEnd = 30f
        var searchStart = -100f * resources.displayMetrics.density
        var searchEnd = 0f * resources.displayMetrics.density
        var bgStart = 0
        var bgEnd = 255

        if (reverse) {
            blurStart = blurEnd.also { blurEnd = blurStart }
            searchStart = searchEnd.also { searchEnd = searchStart }
            bgStart = bgEnd.also { bgEnd = bgStart }
        }

        val blur = ValueAnimator.ofFloat(blurStart, blurEnd).apply {
            addUpdateListener { updatedAnimation ->
                (this@SearchFragment.requireActivity() as MainActivity).blurLauncher(
                    updatedAnimation.animatedValue as Float
                )
            }

            this.duration = duration
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
                this@SearchFragment.binding.root.background.alpha =
                    updatedAnimation.animatedValue as Int
            }

            this.duration = duration
        }

        return AnimatorSet().also { anim ->
            anim.playTogether(blur, search, bg)
        }
    }

    private fun searchEntries() {
        val oldCount = this.searchResults.size
        this.searchResults.clear()

        if (this.searchText.isEmpty()) {
            this.searchResultsAdapter.notifyItemRangeRemoved(0, oldCount)
            return
        }

        this.searchResults.addAll(this.entryManager.searchEntries(this.searchText).let {
            it.slice(0 until min(it.size, this.itemCount))
        })

        if (this.searchResults.size == 0) {
            this.searchResultsAdapter.notifyItemRangeRemoved(0, oldCount)
            return
        }

        if (this.searchResults.size < oldCount) {
            this.searchResultsAdapter.notifyItemRangeRemoved(this.searchResults.size - 1, oldCount - this.searchResults.size)
        }

        if (this.searchResults.size > oldCount) {
            this.searchResultsAdapter.notifyItemRangeInserted(oldCount, this.searchResults.size - oldCount)
        }

        this.searchResultsAdapter.notifyItemRangeChanged(0, oldCount)
    }

    fun onSearchTextChange(editable: Editable?) {
        this.searchEntries()
    }

    fun focusInput() {
        this.binding.searchBar.requestFocus()
        val imm = this.requireContext().getSystemService(InputMethodManager::class.java) ?: return

        imm.showSoftInput(this.binding.searchBar, 0)
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)

        this.searchText = ""
        this.searchEntries()
    }
}

class SearchResultAdapter(private val entries: MutableList<LauncherEntry>): RecyclerView.Adapter<SearchResultViewHolder>() {
    init {
        setHasStableIds(true)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SearchResultViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.launcher_entry, null)

        return SearchResultViewHolder(view)
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

class SearchResultViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
    private val binding: LauncherEntryBinding = LauncherEntryBinding.bind(itemView)

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

    private fun onClick(view: View?) {
        if (this.layoutPosition == RecyclerView.NO_POSITION || view == null) {
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

        (view.context as MainActivity).apply {
            supportFragmentManager.popBackStackImmediate()
            blurLauncher(0f)
        }

        LaunchApplication(view.context, item, bounds, options).execute()
    }
}