package com.peiyu.reader.utils

import android.view.MenuItem
import android.widget.ImageButton
import androidx.annotation.DrawableRes
import com.peiyu.reader.R

fun MenuItem.setIconCompat(@DrawableRes iconRes: Int) {
    setIcon(iconRes)
    actionView?.findViewById<ImageButton>(R.id.item)?.setImageDrawable(icon)
}
