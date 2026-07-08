package app.gov.uidai.capture.domain.model

import android.graphics.Bitmap

/**
 * Processed frame after Stage 2 (Segmentation)
 */
data class SegmentedFrame(
    val processingId: Long,
    val finalBitmap: Bitmap,
    val finalMask: Bitmap? = null,
    val fullBitmap: Bitmap,
    val croppedBitmap: Bitmap,
    val timestamp: Long
)