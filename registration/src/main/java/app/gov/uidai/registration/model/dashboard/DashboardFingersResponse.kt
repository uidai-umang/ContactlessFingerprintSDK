package app.gov.uidai.registration.model.dashboard

import com.google.gson.annotations.SerializedName

data class DashboardFingersResponse(
    @SerializedName("total_fingers") val totalFingers: Int,
    @SerializedName("resident_count") val residentCount: Int,
    @SerializedName("by_finger_type") val byFingerType: Map<String, Int>,
    @SerializedName("residents_by_finger_count") val residentsByFingerCount: Map<String, Int>
)
