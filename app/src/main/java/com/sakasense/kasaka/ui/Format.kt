package com.sakasense.kasaka.ui

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private val dateTime = SimpleDateFormat("MMM d, yyyy · h:mm a", Locale.US)
private val shortDate = SimpleDateFormat("MMM d", Locale.US)

fun formatDateTime(ms: Long): String = dateTime.format(Date(ms))
fun formatShortDate(ms: Long): String = shortDate.format(Date(ms))

fun relativeTime(ms: Long, now: Long = System.currentTimeMillis()): String {
    val mins = (now - ms) / 60_000
    return when {
        mins < 1 -> "just now"
        mins < 60 -> "${mins}m ago"
        mins < 24 * 60 -> "${mins / 60}h ago"
        mins < 7 * 24 * 60 -> "${mins / (24 * 60)}d ago"
        else -> formatShortDate(ms)
    }
}

fun greeting(): String = when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
    in 4..11 -> "Good morning"
    in 12..17 -> "Good afternoon"
    else -> "Good evening"
}

fun formatPh(v: Double) = String.format(Locale.US, "%.1f", v)
