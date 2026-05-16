package com.demo.foundations.ui

import android.content.Context
import android.widget.Toast

/**
 * Thin UI helper exposed to every layer so business code never touches
 * raw [Toast].makeText calls.
 */
object Toaster {
    fun short(context: Context, text: CharSequence) {
        Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
    }
}
