package com.example.homescreen.views

import android.app.Activity
import android.content.Context
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.fragment.app.FragmentActivity

class OverlayView(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int
): ConstraintLayout(context, attrs, defStyle) {
    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, 0);

    override fun onTouchEvent(event: MotionEvent?): Boolean {
        return true
    }

    override fun dispatchKeyEventPreIme(event: KeyEvent?): Boolean {
        if (event?.keyCode == KeyEvent.KEYCODE_BACK) {
            (context as FragmentActivity).supportFragmentManager.popBackStack()
        }
        return super.dispatchKeyEventPreIme(event)
    }
}