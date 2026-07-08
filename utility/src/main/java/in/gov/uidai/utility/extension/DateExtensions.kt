package `in`.gov.uidai.utility.extension

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun Date.toYYYYMMDDHHmmss(): String = SimpleDateFormat(
    "yyyyMMddHHmmss",
    Locale.ENGLISH
).format(this)