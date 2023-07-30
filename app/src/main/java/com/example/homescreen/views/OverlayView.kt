package com.example.homescreen.views

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import androidx.constraintlayout.widget.ConstraintLayout

class OverlayView(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int
): ConstraintLayout(context, attrs, defStyle) {
    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, 0);

    override fun onTouchEvent(event: MotionEvent?): Boolean {
        return true
    }
}