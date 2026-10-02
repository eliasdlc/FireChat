package com.example.firechat.util

import android.content.Context
import android.text.format.DateFormat
import android.text.format.DateUtils
import java.util.Date

object DateFormatter {

    fun conversationTime(context: Context, date: Date?): String {
        if (date == null) return ""
        return if (DateUtils.isToday(date.time)) {
            DateFormat.getTimeFormat(context).format(date)
        } else {
            DateFormat.getDateFormat(context).format(date)
        }
    }
}
