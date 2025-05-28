package com.example.homescreen

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentFactory
import com.example.homescreen.views.SearchFragmentAdapter
import kotlinx.coroutines.CoroutineScope

class LauncherFragmentFactory(private val coroutineScope: CoroutineScope, private val launcherEntryManager: LauncherEntryManager, private val pullToSearchAdapter: SearchFragmentAdapter): FragmentFactory() {
    override fun instantiate(classLoader: ClassLoader, className: String): Fragment {
        return when (className) {
            LauncherFragment::class.java.name -> LauncherFragment(this.pullToSearchAdapter, launcherEntryManager)
            SearchFragment::class.java.name -> SearchFragment(launcherEntryManager, coroutineScope)

            else -> super.instantiate(classLoader, className)
        }
    }

    inline fun <reified T: Fragment> instantiate(classLoader: ClassLoader): T {
        return this.instantiate(classLoader, T::class.java.name) as T
    }
}