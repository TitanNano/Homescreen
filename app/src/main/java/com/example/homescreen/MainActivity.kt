package com.example.homescreen

import android.animation.AnimatorSet
import android.annotation.TargetApi
import android.content.pm.LauncherApps
import android.os.AsyncTask
import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.animation.doOnEnd
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import com.example.homescreen.databinding.MainActivityBinding
import com.example.homescreen.views.SearchFragmentAdapter

class MainActivity : AppCompatActivity(), SearchFragmentAdapter {

    private val activeTasks: MutableList<ICancelTask> = mutableListOf()

    private lateinit var binding: MainActivityBinding
    private lateinit var fragmentFactory: LauncherFragmentFactory
    private lateinit var launcherFragment: LauncherFragment

    private var searchFragment: SearchFragment? = null

    public val fragmentViewId get() = this.binding.root.id

    override fun onCreate(savedInstanceState: Bundle?) {
        this.fragmentFactory = LauncherFragmentFactory(this)
        this.supportFragmentManager.fragmentFactory = this.fragmentFactory

        super.onCreate(savedInstanceState)

        this.launcherFragment = fragmentFactory.instantiate(classLoader)
        this.binding = MainActivityBinding.inflate(this.layoutInflater)

        setContentView(this.binding.root)

        this.supportFragmentManager.commit {
            setReorderingAllowed(true)
            add(binding.root.id, launcherFragment, "main")
        }
    }

    override fun onStart() {
        super.onStart()

        LauncherEntryManager.get(this).entriesReady {
            this.launcherFragment.scrollToBottom()
        }
    }

    @TargetApi(Build.VERSION_CODES.O)
    override fun onResume() {
        super.onResume()

        val intent = this.intent

        if (intent.action != LauncherApps.ACTION_CONFIRM_PIN_SHORTCUT) {
            this.launcherFragment.scrollToBottom()
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
                    this.launcherFragment.scrollToTop()
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

    fun getLauncherEntryManager(): LauncherEntryManager {
        return LauncherEntryManager.get(this)
    }

    fun blurLauncher(radius: Float) {
        this.launcherFragment.blurLauncher(radius)
    }

    fun requireSearchFragment(): SearchFragment {
        this.searchFragment?.let { return it }

        val searchFragment: SearchFragment = this.fragmentFactory.instantiate(this.classLoader)

        this.searchFragment = searchFragment
        return searchFragment
    }

    // ------- Pull to search fragment adapter -------

    override fun requireFragment(): Fragment {
        return this.requireSearchFragment()
    }

    override fun createEnterAnimation(): AnimatorSet {
        return this.requireSearchFragment().enterAnimation(reverse = false).also { set ->
            set.doOnEnd {
                if (requireSearchFragment().isHidden) {
                    return@doOnEnd
                }

                this.requireSearchFragment().focusInput()
            }
        }
    }

    override fun attachFragment() {
        this.supportFragmentManager.apply {
            commit {
                val fragment = requireSearchFragment()
                setReorderingAllowed(true)
                add(fragmentViewId, fragment, "search")
                addToBackStack(null)
            }

            executePendingTransactions()
        }
    }

    override fun detachFragment() {
        this.supportFragmentManager.commit {
            val fragment = requireSearchFragment()
            remove(fragment)
        }
    }

    override fun popFragment() {
        this.supportFragmentManager.popBackStack()
    }
}
