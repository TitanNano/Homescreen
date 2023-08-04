package com.example.homescreen.views

import android.animation.AnimatorSet
import android.content.Context
import android.util.AttributeSet
import android.util.Log
import android.view.GestureDetector
import android.view.MotionEvent
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.animation.doOnEnd
import androidx.fragment.app.Fragment

class PullToSearchView(
    context: Context,
    attrs: AttributeSet? = null,
    flags: Int
) : ConstraintLayout(context, attrs, flags) {

    private val gestureDetector: GestureDetector
    private var searchFragmentAnimation: AnimatorSet? = null

    var adapter: SearchFragmentAdapter? = null

    init {
        this.gestureDetector = GestureDetector(context, object: GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent): Boolean = this@PullToSearchView.onDown()

            override fun onScroll(
                e1: MotionEvent,
                e2: MotionEvent,
                distanceX: Float,
                distanceY: Float
            ): Boolean = this@PullToSearchView.onScroll(e1, e2, distanceX, distanceY)
        }).also { it.setIsLongpressEnabled(false) }

        this.filterTouchesWhenObscured = true
    }

    constructor(context: Context, attrs: AttributeSet?): this(context, attrs, 0)

    fun onHandleTouch(ev: MotionEvent?): Boolean {
        val result = when (ev?.action) {
            MotionEvent.ACTION_UP -> this.onStopSearchPull()
            else -> false
        }

        if (result) {
            return true
        }

        if (ev != null && this.gestureDetector.onTouchEvent(ev)) {
            return true
        }

        return false
    }

    override fun onInterceptTouchEvent(ev: MotionEvent?): Boolean {
        if (this.onHandleTouch(ev)) {
            return true
        }

        return super.onInterceptTouchEvent(ev)
    }

    override fun onTouchEvent(ev: MotionEvent?): Boolean {
        if (this.onHandleTouch(ev)) {
            return true
        }

        return super.onTouchEvent(ev)
    }

    private fun onDown(): Boolean {
        return false
    }

    private fun onScroll(
        e1: MotionEvent,
        e2: MotionEvent,
        distanceX: Float,
        distanceY: Float
    ): Boolean {
        val adapter = this.adapter ?: return false

        if (this.searchFragmentAnimation == null) {
            val animation = adapter.apply {
                attachFragment()
            }.createEnterAnimation()

            this.searchFragmentAnimation = animation.apply { start(); pause() }
        }

        val searchFragmentAnimation = this.searchFragmentAnimation
            ?: throw IllegalStateException("missing search animation")

        val yOffset = (e2.y - e1.y).coerceAtLeast(0f)
        val yOffsetPercent = (yOffset / resources.displayMetrics.density / 200).coerceAtMost(1f)
        val playTime = (searchFragmentAnimation.totalDuration * yOffsetPercent).toLong()

        searchFragmentAnimation.currentPlayTime = playTime
        return true
    }

    private fun onStopSearchPull(): Boolean {
        val adapter = this.adapter ?: return false
        val searchFragmentAnimation = this.searchFragmentAnimation

        if (searchFragmentAnimation === null) {
            adapter.detachFragment()
            return false
        }

        val progess =
            searchFragmentAnimation.currentPlayTime.toFloat() / searchFragmentAnimation.totalDuration.toFloat()

        if (progess == 0f) {
            searchFragmentAnimation.apply {
                cancel()
            }

            this.searchFragmentAnimation = null
            adapter.detachFragment()

            return true
        }

        if (progess < 0.5f) {
            searchFragmentAnimation.apply {
                reverse()
                resume()

                doOnEnd {
                    adapter.detachFragment()
                }
            }

            this.searchFragmentAnimation = null

            return true
        }

        searchFragmentAnimation.resume()

        this.searchFragmentAnimation = null
        return true
    }
}

interface SearchFragmentAdapter {
    fun requireFragment(): Fragment
    fun createEnterAnimation(): AnimatorSet
    fun attachFragment()
    fun detachFragment()
    fun popFragment()
}