package com.iftl.threedee.viewer.ui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.iftl.threedee.viewer.R

/** Uses the same drawable resources as the controls, so the visual guide stays in sync. */
object ModelHelpDialog {
    fun show(context: Context) {
        val density = context.resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()
        fun text(resource: Int, heading: Boolean = false) =
            TextView(context).apply {
                setText(resource)
                textSize = if (heading) 15f else 13f
                setTextColor(ContextCompat.getColor(context,
                    if (heading) R.color.text_primary else R.color.text_secondary))
                if (heading) typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                setLineSpacing(dp(3).toFloat(), 1f)
            }

        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(8), dp(24), dp(8))
        }
        content.addView(text(R.string.help_intro))

        fun row(icon: Int, title: Int, description: Int) {
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.TOP
                setPadding(0, dp(20), 0, 0)
            }
            row.addView(ImageView(context).apply {
                setImageResource(icon)
                imageTintList = ColorStateList.valueOf(ContextCompat.getColor(context, R.color.accent))
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }, LinearLayout.LayoutParams(dp(24), dp(24)).apply { marginEnd = dp(16) })
            val words = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
            words.addView(text(title, heading = true))
            words.addView(text(description), LinearLayout.LayoutParams(-1, -2).apply {
                topMargin = dp(4)
            })
            row.addView(words, LinearLayout.LayoutParams(0, -2, 1f))
            content.addView(row)
        }

        row(R.drawable.ic_interact, R.string.interact, R.string.help_interact)
        row(R.drawable.ic_labels, R.string.labels, R.string.help_labels)
        row(R.drawable.ic_close, R.string.close, R.string.help_close)
        row(R.drawable.ic_fullscreen, R.string.fullscreen, R.string.help_fullscreen)
        row(R.drawable.ic_fullscreen_exit, R.string.restore_size, R.string.help_restore)
        content.addView(text(R.string.help_reopen), LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = dp(24)
        })
        val scroll = ScrollView(context).apply { addView(content) }
        MaterialAlertDialogBuilder(context)
            .setTitle(R.string.help_title)
            .setView(scroll)
            .setPositiveButton(R.string.got_it, null)
            .show()
    }
}
