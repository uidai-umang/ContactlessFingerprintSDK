package app.gov.uidai.capture.ui.camera

import android.graphics.Point
import android.graphics.RectF

// Plain, non-Composable holder — ImageProcessor reads this from a
// background dispatcher, so it can't be Compose State<T> (only safely
// read inside composition/snapshot reads). @Volatile gives safe
// cross-thread visibility, matching the AtomicBoolean/AtomicReference
// style already used throughout ImageProcessor.
class CutoutBoundsHolder {
    @Volatile
    var rect: RectF = RectF()
    @Volatile
    var origin: Point = Point(0, 0)
}