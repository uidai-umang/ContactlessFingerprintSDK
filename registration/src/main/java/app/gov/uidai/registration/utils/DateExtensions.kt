package app.gov.uidai.registration.utils

import android.annotation.SuppressLint
import java.text.SimpleDateFormat
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale


fun Date.toYYYYMMDDHHmmss(): String = SimpleDateFormat(
    "yyyyMMddHHmmss",
    Locale.ENGLISH
).format(this)

