package com.example.homescreen

import android.content.Context
import android.util.AttributeSet
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.GridLayoutManager as AndroidGridLayoutManager

class GridLayoutManager(
    context: Context?,
    attrs: AttributeSet?,
    defStyleAttr: Int,
    defStyleRes: Int
) : AndroidGridLayoutManager(context, attrs, defStyleAttr, defStyleRes) {
    private val onceLayoutCompletedHandlers: MutableList<() -> Unit> = mutableListOf()

    override fun onLayoutCompleted(state: RecyclerView.State?) {
        super.onLayoutCompleted(state)

        this.onceLayoutCompletedHandlers.onEach { it() }.clear()
    }

    fun onceLayoutCompleted(callback: () -> Unit) = this.onceLayoutCompletedHandlers.add(callback)
}