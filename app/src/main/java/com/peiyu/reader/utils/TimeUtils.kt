package com.peiyu.reader.utils

import kotlin.math.abs

fun Long.toTimeAgo(): String {
    val curTime = System.currentTimeMillis()
    val time = this
    val seconds = abs(System.currentTimeMillis() - time) / 1000f
    val end = if (time < curTime) "Ââ? else "Âê?

    val start = when {
        seconds < 60 -> "${seconds.toInt()}Áß?
        seconds < 3600 -> {
            val minutes = seconds / 60f
            "${minutes.toInt()}ÂàÜÈíü"
        }
        seconds < 86400 -> {
            val hours = seconds / 3600f
            "${hours.toInt()}Â∞èÊó∂"
        }
        seconds < 604800 -> {
            val days = seconds / 86400f
            "${days.toInt()}Â§?
        }
        seconds < 2_628_000 -> {
            val weeks = seconds / 604800f
            "${weeks.toInt()}Âë?
        }
        seconds < 31_536_000 -> {
            val months = seconds / 2_628_000f
            "${months.toInt()}Êú?
        }
        else -> {
            val years = seconds / 31_536_000f
            "${years.toInt()}Âπ?
        }
    }
    return start + end
}

fun Int.toDurationTime(): String {
    val totalSeconds = this / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60

    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%02d:%02d".format(minutes, seconds)
    }
}
