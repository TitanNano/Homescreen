package com.example.homescreen

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentFactory
import com.example.homescreen.views.SearchFragmentAdapter

class LauncherFragmentFactory(private val pullToSearchAdapter: SearchFragmentAdapter): FragmentFactory() {
    override fun instantiate(classLoader: ClassLoader, className: String): Fragment {
        return when (className) {
            LauncherFragment::class.java.name -> LauncherFragment(this.pullToSearchAdapter)
            SearchFragment::class.java.name -> SearchFragment()

            else -> super.instantiate(classLoader, className)
        }
    }

    inline fun <reified T: Fragment> instantiate(classLoader: ClassLoader): T {
        return this.instantiate(classLoader, T::class.java.name) as T
    }
}