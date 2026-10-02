package com.example.firechat.util

import android.content.Context
import android.text.format.DateFormat
import android.text.format.DateUtils
import java.util.Date

object DateFormatter {

    fun messageTime(context: Context, date: Date?): String {
        if (date == null) return ""
        val time = DateFormat.getTimeFormat(context).format(date)
        if (DateUtils.isToday(date.time)) return time
        return "${DateFormat.getDateFormat(context).format(date)} $time"
    }

    fun conversationTime(context: Context, date: Date?): String {
        if (date == null) return ""
        return if (DateUtils.isToday(date.time)) {
            DateFormat.getTimeFormat(context).format(date)
        } else {
            DateFormat.getDateFormat(context).format(date)
        }
    }
}
