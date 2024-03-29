package com.example.homescreen

import android.animation.Animator
import android.animation.AnimatorSet
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.ActivityOptions
import android.app.WallpaperManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Rect
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.text.Editable
import android.util.Log
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowInsetsAnimation
import android.view.inputmethod.InputMethodManager
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.appcompat.widget.AppCompatEditText
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.drawable.toDrawable
import androidx.core.graphics.scale
import androidx.core.view.WindowInsetsCompat
import androidx.databinding.BindingAdapter
import androidx.databinding.ObservableInt
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.RecyclerView
import com.example.homescreen.databinding.LauncherEntryBinding
import com.example.homescreen.databinding.SearchFragmentBinding
import com.example.homescreen.tasks.LaunchApplication
import eightbitlab.com.blurview.BlurView
import kotlin.math.min
import kotlin.math.roundToInt


class SearchFragment : Fragment(R.layout.search_fragment) {

    private lateinit var binding: SearchFragmentBinding
    private lateinit var entryManager: LauncherEntryManager

    var searchText: String = ""
    private val searchResults: MutableList<LauncherEntry> = mutableListOf()
    val searchResultsAdapter = SearchResultAdapter(this.searchResults)
    private val itemCount = 4
    private lateinit var overlayColorDrawable: ColorDrawable
    val overlayColor: ObservableInt = ObservableInt(0)
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

        this.entryManager = LauncherEntryManager.get(this.requireContext()).also {
            it.onEntitiesChanged {
                this.searchEntries()
            }
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

        if (!Environment.isExternalStorageManager()) {
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                Log.e("search_pull", "external storage permission was not granted ${result}")

                this.setupBlurView()
            }.apply {
                val uri = Uri.parse("package:" + BuildConfig.APPLICATION_ID)
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, uri)

                launch(intent)
            }

            return
        }

        this.setupBlurView()
    }

    @SuppressLint("MissingPermission")
    fun setupBlurView() {
        val decorView = this.requireActivity().window.decorView
        val wallpaperManager = requireContext().getSystemService(WallpaperManager::class.java)

        var frame = this.displayFrame()
        // not considering the status bar hight at the moment
        val statusBar = 0//frame.top + 100

        val wallpaper = (wallpaperManager.drawable as BitmapDrawable).bitmap
            .let {
                val multiply = wallpaperManager.desiredMinimumHeight / it.height.toFloat()
                val width = (it.width * multiply).roundToInt()
                val height = (it.height * multiply).roundToInt()


                it.scale(width, height)
            }
            .let {
                val width = frame.width()
                val height = it.height
                // approximation of the zoom in on Xperia 10 III
                val zoomInX = (width * 0.045).roundToInt()
                // approximation of the zoom in on Xperia 10 III
                val zoomInY = (height * 0.0666).roundToInt()

                Bitmap.createBitmap(
                    it,
                    zoomInX,
                    zoomInY,
                    width - (zoomInX * 2),
                    height - (zoomInY * 2)
                )
                    .scale(width, height)
                    .let {
                        Bitmap.createBitmap(it, 0, statusBar, it.width, it.height - statusBar)
                    }.toDrawable(resources)
            }.apply {
                val scaleFactor = 5.625
                val height = frame.height()
                val width = frame.width()

                val bounds = Rect(0, 0, width, height).apply {
                    left = (left / scaleFactor).roundToInt()
                    top = (top / scaleFactor).roundToInt()
                    right = (right / scaleFactor).roundToInt()
                    bottom = (bottom / scaleFactor).roundToInt()
                }

                this.bounds = bounds
            }

        this.binding.blurRoot.setupWith(decorView as ViewGroup).setFrameClearDrawable(wallpaper)
    }

    fun enterAnimation(reverse: Boolean): AnimatorSet {
        val duration: Long = 400

        var blurStart = 0.001f
        var blurEnd = 10f
        var searchStart = -100f * resources.displayMetrics.density
        var searchEnd = 0f * resources.displayMetrics.density
        var bgStart = 0
        var bgEnd = 127

        if (reverse) {
            blurStart = blurEnd.also { blurEnd = blurStart }
            searchStart = searchEnd.also { searchEnd = searchStart }
            bgStart = bgEnd.also { bgEnd = bgStart }
        }

        val blur = ValueAnimator.ofFloat(blurStart, blurEnd).apply {
            addUpdateListener { updatedAnimation ->
                binding.blurRoot.setBlurRadius(updatedAnimation.animatedValue as Float)
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
                this@SearchFragment.updateOverlayAlpha(updatedAnimation.animatedValue as Int)
            }

            this.duration = duration
        }

        return AnimatorSet().also { anim ->
            anim.playTogether(blur, search, bg)
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

        this.searchResults.addAll(this.entryManager.searchEntries(this.searchText).let {
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

    fun displayFrame(): Rect {
        val frame = Rect()

        requireActivity().window.decorView.getWindowVisibleDisplayFrame(frame)

        return frame
    }

    fun updateOverlayAlpha(alpha: Int) {
        this.overlayColorDrawable.let {
            it.alpha = alpha
            it.color
        }.let {
            this.overlayColor.set(it)
        }
    }


    fun updateKeyboardHeight(insets: WindowInsets) {
        val compatInsets = WindowInsetsCompat.toWindowInsetsCompat(insets)
        val height = compatInsets.getInsets(WindowInsetsCompat.Type.ime()).bottom

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
        @BindingAdapter("blurOverlayColor")
        @JvmStatic
        fun updateOverlayColor(view: BlurView, color: Int) {
            Log.i("pull_search.overlay", "updating overlay color: $color")
            view.setOverlayColor(color)
        }

        @BindingAdapter("app:onKeyListener")
        @JvmStatic
        fun setOnKeyListener(view: AppCompatEditText, listener: View.OnKeyListener) {
            view.setOnKeyListener(listener)
        }
    }
}

class SearchResultAdapter(private val entries: MutableList<LauncherEntry>) :
    RecyclerView.Adapter<SearchResultViewHolder>() {
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

        LaunchApplication(view.context, item, bounds, options).execute()
    }
}