package app.gov.uidai.registration.data.converter

import androidx.room.TypeConverter
import app.gov.uidai.registration.model.FingerPosition

class FingerPositionConverter {
    @TypeConverter
    fun fromFingerPosition(position: FingerPosition): String {
        return position.name
    }

    @TypeConverter
    fun toFingerPosition(position: String): FingerPosition {
        return FingerPosition.valueOf(position)
    }
}