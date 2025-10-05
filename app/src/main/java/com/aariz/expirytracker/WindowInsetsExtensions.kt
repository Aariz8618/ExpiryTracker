package com.aariz.expirytracker

import android.view.View
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

fun View.applyHeaderInsets() {
    ViewCompat.setOnApplyWindowInsetsListener(this) { view, windowInsets ->
        val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())

        view.setPadding(
            view.paddingLeft,      // Keep original left padding
            insets.top,            // Add top inset (status bar)
            view.paddingRight,     // Keep original right padding
            view.paddingBottom     // Keep original bottom padding
        )

        windowInsets
    }
}

fun View.applyBottomNavInsets() {
    ViewCompat.setOnApplyWindowInsetsListener(this) { view, windowInsets ->
        val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())

        view.setPadding(
            view.paddingLeft,     // Keep original left padding
            view.paddingTop,      // Keep original top padding
            view.paddingRight,    // Keep original right padding
            insets.bottom         // Add bottom inset (navigation bar/gestures)
        )

        windowInsets
    }
}

fun View.applySystemBarInsets(
    top: Boolean = false,
    bottom: Boolean = false,
    left: Boolean = false,
    right: Boolean = false
) {
    ViewCompat.setOnApplyWindowInsetsListener(this) { view, windowInsets ->
        val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())

        view.setPadding(
            if (left) insets.left else view.paddingLeft,
            if (top) insets.top else view.paddingTop,
            if (right) insets.right else view.paddingRight,
            if (bottom) insets.bottom else view.paddingBottom
        )

        windowInsets
    }
}

fun View.applyHeaderInsetsAsMargin() {
    ViewCompat.setOnApplyWindowInsetsListener(this) { view, windowInsets ->
        val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())

        val layoutParams = view.layoutParams as? android.view.ViewGroup.MarginLayoutParams
        layoutParams?.let {
            it.topMargin = insets.top
            // Keep left, right, and bottom margins as is
            view.layoutParams = it
        }

        windowInsets
    }
}

fun View.applyBottomNavInsetsAsMargin() {
    ViewCompat.setOnApplyWindowInsetsListener(this) { view, windowInsets ->
        val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())

        val layoutParams = view.layoutParams as? android.view.ViewGroup.MarginLayoutParams
        layoutParams?.let {
            it.bottomMargin = insets.bottom
            // Keep left, right, and top margins as is
            view.layoutParams = it
        }

        windowInsets
    }
}

fun View.applyHeaderInsetsAndConsume() {
    ViewCompat.setOnApplyWindowInsetsListener(this) { view, windowInsets ->
        val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())

        view.setPadding(
            view.paddingLeft,
            insets.top,
            view.paddingRight,
            view.paddingBottom
        )

        // Return consumed insets
        WindowInsetsCompat.CONSUMED
    }
}

fun View.getSystemBarInsets(): Insets {
    val windowInsets = ViewCompat.getRootWindowInsets(this)
    return windowInsets?.getInsets(WindowInsetsCompat.Type.systemBars()) ?: Insets.NONE
}