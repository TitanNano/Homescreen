package com.example.homescreen

import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.RecyclerView
import com.example.homescreen.databinding.LauncherFragmentBinding
import com.example.homescreen.views.SearchFragmentAdapter
import kotlin.math.roundToInt

class LauncherFragment(private val pullToSearchAdapter: SearchFragmentAdapter) : Fragment() {

    private var grid: RecyclerView? = null
    private lateinit var binding: LauncherFragmentBinding

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        this.binding = LauncherFragmentBinding.inflate(this.layoutInflater)

        return this.binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        if (savedInstanceState != null) {
            return
        }

        this.binding.pullToSearch.apply {
            adapter = pullToSearchAdapter
        }
    }

    override fun onStart() {
        super.onStart()

        this.grid = this.binding.appgrid.also {
            it.adapter = this.context?.let { context ->
                (context as MainActivity).getLauncherEntryManager()
                    .makeLauncherEntryAdapter(context)
            }
        }
    }

    fun scrollToBottom() {
        // old method, no longer used, will be removed
    }

    fun scrollToTop() {
        // old method, no longer used, will be removed
    }

    fun blurLauncher(radius: Float) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return
        }

        val renderEffect = if (radius > 0f) RenderEffect.createBlurEffect(
            radius,
            radius,
            Shader.TileMode.CLAMP
        ) else null

        this.binding.root.setRenderEffect(renderEffect)
        this.activity?.window?.setBackgroundBlurRadius(radius.roundToInt())
    }
}