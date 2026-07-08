package app.gov.uidai.capture.ui.camera.model

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes

sealed interface UIMessage {
    @get:StringRes
    val titleRes: Int
    @get:StringRes
    val descriptionRes: Int
    @get:DrawableRes
    val imageRes: Int
}