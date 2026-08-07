# capture/src/main/python/blur_detector_laplacian.py
import cv2
import numpy as np

_DEBUG_ENABLED = __debug__
def debug_print(message):
    if _DEBUG_ENABLED:
        print(message)

# Pixels at or above this value are treated as clipped/blown-out highlight
# -- confirmed via real device testing: harsh direct light on skin produces
# a specular highlight that clips to near-255, and that clipped region
# contributes near-zero Laplacian variance since there's no texture left to
# measure there. Averaging it in drags down an otherwise genuinely sharp
# finger's score. 250 leaves a small margin below true 255 saturation to
# catch near-clipped pixels too, not just fully-clipped ones.
CLIP_THRESHOLD = 250


def main(nv21_bytes, width, height):
    """Called once per frame from LaplacianBlurMethod.kt via Chaquopy.
    Returns a single float: the masked Laplacian variance, used directly
    as the live/Stage-2 blur confidence score."""

    # STEP 1 -- Decode the raw camera buffer.
    # NV21 is Y plane + interleaved VU plane, height*1.5 rows tall by
    # convention. Reshape gives the raw planar layout; cvtColor below does
    # the actual colorspace conversion.
    yuv = np.frombuffer(nv21_bytes, dtype=np.uint8).reshape((height * 3 // 2, width))

    # STEP 2 -- NV21 -> BGR -> grayscale.
    # Laplacian variance only needs luminance, not color, so grayscale is
    # the actual input the sharpness measurement runs against.
    bgr = cv2.cvtColor(yuv, cv2.COLOR_YUV2BGR_NV21)
    gray = cv2.cvtColor(bgr, cv2.COLOR_BGR2GRAY)

    # STEP 3 -- Run the Laplacian operator over the whole frame.
    # This produces a per-pixel second-derivative map -- high values where
    # intensity changes sharply (ridge edges), near-zero in flat regions
    # (out-of-focus blur, OR a blown-out clipped highlight -- both look
    # "flat" to this operator, which is exactly the problem being solved
    # below).
    laplacian = cv2.Laplacian(gray, cv2.CV_64F)

    # STEP 4 -- Build the clipping mask.
    # True for every pixel BELOW the clip threshold (i.e. pixels that still
    # carry real intensity information). Pixels at/above CLIP_THRESHOLD are
    # excluded entirely from the variance calculation in Step 6.
    unclipped_mask = gray < CLIP_THRESHOLD

    # STEP 5 -- Compute how much of the frame is clipped, for diagnostics.
    # Not used in the returned score itself -- purely informational, logged
    # below so real device sessions can be checked for how often/how badly
    # clipping is actually occurring.
    total_px = gray.size
    unclipped_px = int(np.count_nonzero(unclipped_mask))
    clipped_fraction = 1.0 - (unclipped_px / total_px)

    # STEP 6 -- The actual fix: variance computed ONLY over unclipped pixels.
    # A naive .var() over the whole frame lets a blown highlight's near-zero
    # Laplacian values drag down the average even when the rest of the
    # finger is genuinely sharp. Masking them out means the score reflects
    # actual ridge sharpness, not an unrelated lighting artifact.
    values = laplacian[unclipped_mask]
    variance = float(values.var()) if values.size > 0 else 0.0

    # STEP 7 -- Log the full picture for later tuning: how much was
    # clipped, how many pixels the variance was actually computed over, and
    # the final score -- lets a device session's real logcat output be
    # cross-checked against what was actually happening in-frame.
    debug_print(
        f"[BlurLaplacian] clipped_fraction={clipped_fraction:.3f} "
        f"unclipped_px={unclipped_px}/{total_px} variance={variance:.2f}"
    )

    # STEP 8 -- Return the score. LaplacianBlurMethod.kt compares this
    # directly against minVariance -- no other transformation happens on
    # the Kotlin side.
    return variance