package com.example.homescreen

import android.animation.AnimatorSet
import android.content.pm.LauncherApps
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.animation.doOnEnd
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import com.example.homescreen.databinding.MainActivityBinding
import com.example.homescreen.views.SearchFragmentAdapter
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel

class MainActivity : AppCompatActivity(), SearchFragmentAdapter {
    private lateinit var binding: MainActivityBinding
    private lateinit var fragmentFactory: LauncherFragmentFactory
    private lateinit var launcherFragment: LauncherFragment
    private lateinit var launcherEntryManager: LauncherEntryManager
    private val coroutineScope = MainScope()

    private var searchFragment: SearchFragment? = null

    public val fragmentViewId get() = this.binding.root.id

    override fun onCreate(savedInstanceState: Bundle?) {
        this.launcherEntryManager = LauncherEntryManager(this, coroutineScope)
        this.fragmentFactory = LauncherFragmentFactory(coroutineScope, launcherEntryManager,  this)
        this.supportFragmentManager.fragmentFactory = this.fragmentFactory

        super.onCreate(savedInstanceState)

        this.launcherFragment = fragmentFactory.instantiate(classLoader)
        this.binding = MainActivityBinding.inflate(this.layoutInflater)
        this.requireSearchFragment()

        setContentView(this.binding.root)

        this.supportFragmentManager.commit {
            setReorderingAllowed(true)
            add(binding.root.id, launcherFragment, "main")
        }
    }

    override fun onStart() {
        super.onStart()

        launcherEntryManager.entriesReady {
            this.launcherFragment.scrollToBottom()
        }
    }


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
            launcherEntryManager.addShortcut(it)
                .invokeOnCompletion {
                    this.launcherFragment.scrollToTop()
                }
        }

        request.accept()
    }

    override fun onDestroy() {
        coroutineScope.cancel()
        super.onDestroy()
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
        val searchFragment = this.requireSearchFragment()

        return searchFragment.enterAnimation(reverse = false).also { set ->
            set.doOnEnd {
                if (searchFragment.isCanceled) {
                    return@doOnEnd
                }

                searchFragment.apply {
                    enableExitAnimation = true
                    focusInput()
                }
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

    override fun clearCanceled() {
        this.requireSearchFragment().isCanceled = false
    }

    override fun setCanceled() {
        this.requireSearchFragment().isCanceled = true
    }
}
