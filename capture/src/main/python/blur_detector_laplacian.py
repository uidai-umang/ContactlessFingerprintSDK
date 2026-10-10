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

# Every device's finger crop is resized to this width before scoring.
#
# WHY THIS EXISTS -- confirmed on real devices this session: raw Laplacian
# variance measured over a WHOLE cutout (finger + background) at native
# sensor resolution produced wildly inconsistent numbers across devices --
# a genuinely sharp finger scored 12-18 on a Samsung SM-A505F (2340px-wide
# cutout) vs 257-410 on a Vivo V2153 (1670px-wide cutout) for comparable
# real sharpness, for two SEPARATE, COMPOUNDING reasons:
#   1. Background dilution -- measuring the whole cutout means most of
#      what's measured is flat, texture-less background, which drags the
#      average down regardless of how sharp the finger itself is.
#   2. Resolution dependence -- raw Laplacian variance is not scale
#      invariant. The same real ridge pattern sampled at a higher pixel
#      density spreads each edge transition across more pixels, lowering
#      the per-pixel intensity delta even at identical true sharpness.
#
# FINGER_REFERENCE_WIDTH fixes BOTH at once: segmenting to just the finger
# (see segment_finger() below) removes the background-dilution term
# entirely, and resizing that finger-only crop to a fixed width removes
# the resolution-dependence term. Verified directly against real device
# captures (see conversation record): this combination reduced a
# simulated ~15-20x cross-resolution gap down to ~1.9x for the same real
# scene, and gave a clean, consistent ~23x separation between sharp and
# heavily blurred versions of the SAME real finger images, versus a
# previous attempt at resizing the WHOLE cutout alone, which only reduced
# the same device's variance from ~15 to ~17-20 -- nowhere near enough.
FINGER_REFERENCE_WIDTH = 800

# Inward margin trimmed from the segmented finger bounding box, as a
# fraction of the box's shorter side -- the finger's own silhouette EDGE
# (skin against background) is itself a very high-contrast boundary that
# contributes large, spurious Laplacian energy unrelated to ridge
# sharpness. Trimming it out keeps the measurement honest.
FINGER_CROP_MARGIN_FRACTION = 0.08

# If less than this fraction of the frame segments as "finger", treat
# segmentation as unreliable (e.g. very low light, unusual background,
# finger filling the entire frame with no visible boundary) and fall back
# to scoring the whole frame rather than risk cropping to a bogus region.
MIN_FINGER_AREA_FRACTION = 0.05

# A finger held up to the camera in this app's expected portrait framing
# should always segment TALLER than it is wide -- every real, confirmed
# correct detection so far has landed between ~1.7:1 and ~2.3:1
# (height:width). CONFIRMED ON-DEVICE: a Samsung SM-A505F session
# produced dozens of consecutive frames segmenting at roughly 4.7:1 WIDE
# (raw box shape inferred from a post-resize scoredSize of 800x170 --
# resizing always sets width to FINGER_REFERENCE_WIDTH, so the height
# alone reveals the original aspect ratio was ~800:170). No saved frame
# from that exact session was available to inspect directly and confirm
# the specific cause (a lighting band, a shadow edge, an overexposure
# artifact -- this device has shown near-100% blown-highlight frames in
# past sessions -- or something else). Rather than guess at a cause we
# can't verify, this rejects any segmentation result that's physically
# implausible FOR THIS FRAMING, regardless of what produced it, and falls
# back to the existing, already-validated whole-frame path.
MIN_FINGER_ASPECT_RATIO = 1.0  # height / width -- must be at least this tall


def segment_finger(gray):
    """Otsu-threshold based finger segmentation. Returns (x, y, w, h) of
    the finger's bounding box in gray's own coordinate space, or None if
    segmentation isn't trustworthy. This runs INSIDE this same function
    call, on the SAME frame already being scored -- unlike the
    FingerCheckRunner-based approach considered and deliberately dropped
    earlier this session, there is no cross-loop timing/staleness concern
    here, since it's not sourced from a different frame processed by a
    different loop.
    """
    try:
        _, otsu_mask = cv2.threshold(gray, 0, 255, cv2.THRESH_BINARY_INV + cv2.THRESH_OTSU)
        contours, _ = cv2.findContours(otsu_mask, cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_SIMPLE)
        if not contours:
            return None
        largest = max(contours, key=cv2.contourArea)
        area = cv2.contourArea(largest)
        if area / gray.size < MIN_FINGER_AREA_FRACTION:
            return None
        bbox = cv2.boundingRect(largest)
        _, _, bw, bh = bbox
        if bw <= 0 or (bh / bw) < MIN_FINGER_ASPECT_RATIO:
            debug_print(
                f"[BlurLaplacian] segmentation REJECTED -- implausible aspect ratio "
                f"{bw}x{bh} (h/w={bh / bw if bw > 0 else float('inf'):.2f}, "
                f"need >= {MIN_FINGER_ASPECT_RATIO})"
            )
            return None
        return bbox
    except Exception:
        return None


def get_finger_bbox_from_rgba(rgba_bytes, width, height):
    """For DensenetBlurMethod.kt: given a Bitmap's raw ARGB_8888 pixel
    bytes (as produced by Bitmap.copyPixelsToBuffer on a bitmap already
    converted to that config), returns the finger's bounding box
    [x, y, w, h] in THIS bitmap's own coordinate space, or an EMPTY LIST
    if segmentation isn't trustworthy.

    Returns [] rather than None on failure -- Chaquopy's PyObject has no
    reliable, version-stable "is this None" check exposed to Kotlin
    (confirmed: no isNone property exists), so the Kotlin caller checks
    bboxList.size instead, which is unambiguous.

    Reuses the EXACT SAME segment_finger() this file already uses for the
    Laplacian check -- one segmentation implementation (including the
    aspect-ratio plausibility guard above), two call sites, rather than a
    second copy that could quietly drift out of sync.

    Deliberately takes the bitmap's OWN pixel data rather than reusing the
    raw NV21 buffer already segmented elsewhere in this file -- the bitmap
    DensenetBlurMethod works with has already been rotated upright, while
    the NV21-based segmentation runs on the raw, unrotated frame. Feeding
    this function the bitmap's actual bytes sidesteps any risk of a
    coordinate-space mismatch between the two orientations entirely,
    rather than requiring the caller to get a rotation transform correct.

    NOTE: this preprocessing change has NOT been empirically validated
    against real device captures the way the Laplacian fix was earlier
    this session (no .tflite model was available to test against at the
    time this was written) -- test on-device with known-sharp and
    known-blurry real captures before trusting it in production.
    """
    try:
        arr = np.frombuffer(rgba_bytes, dtype=np.uint8).reshape((height, width, 4))
        # Android's ARGB_8888 config stores bytes in R,G,B,A order per
        # pixel when read via copyPixelsToBuffer -- take the first 3
        # channels as RGB, drop alpha.
        gray = cv2.cvtColor(arr[:, :, :3], cv2.COLOR_RGB2GRAY)
        bbox = segment_finger(gray)
        if bbox is None:
            return []
        x, y, w, h = bbox
        return [int(x), int(y), int(w), int(h)]
    except Exception as e:
        debug_print(f"[BlurLaplacian] get_finger_bbox_from_rgba failed: {e}")
        return []


def main(nv21_bytes, width, height):
    """Called once per frame from LaplacianBlurMethod.kt via Chaquopy.
    Returns a single float: the masked Laplacian variance of the
    segmented-and-normalized finger crop, used directly as the live/
    Stage-2 blur confidence score."""

    # STEP 1 -- Decode the raw camera buffer.
    yuv = np.frombuffer(nv21_bytes, dtype=np.uint8).reshape((height * 3 // 2, width))

    # STEP 2 -- NV21 -> BGR -> grayscale.
    bgr = cv2.cvtColor(yuv, cv2.COLOR_YUV2BGR_NV21)
    gray = cv2.cvtColor(bgr, cv2.COLOR_BGR2GRAY)

    # STEP 3 -- Segment the finger and crop to it (with fallback).
    bbox = segment_finger(gray)
    region_label = "finger"
    if bbox is not None:
        x, y, bw, bh = bbox
        margin = int(min(bw, bh) * FINGER_CROP_MARGIN_FRACTION)
        x0, y0 = max(0, x + margin), max(0, y + margin)
        x1, y1 = min(width, x + bw - margin), min(height, y + bh - margin)
        if x1 > x0 and y1 > y0:
            scored_region = gray[y0:y1, x0:x1]
        else:
            scored_region = gray
            region_label = "wholeFrame(marginTooLarge)"
    else:
        scored_region = gray
        region_label = "wholeFrame(segmentationFailedOrImplausible)"

    # STEP 4 -- Resize to a fixed reference width -- see
    # FINGER_REFERENCE_WIDTH comment above for why this specific
    # combination (segment THEN resize) is what makes the score
    # comparable across devices.
    rh, rw = scored_region.shape
    if rw > 0 and rw != FINGER_REFERENCE_WIDTH:
        scale = FINGER_REFERENCE_WIDTH / rw
        new_h = max(1, round(rh * scale))
        interpolation = cv2.INTER_AREA if scale < 1.0 else cv2.INTER_LINEAR
        scored_region = cv2.resize(scored_region, (FINGER_REFERENCE_WIDTH, new_h), interpolation=interpolation)

    # STEP 5 -- Run the Laplacian operator over the scored region. High
    # values where intensity changes sharply (ridge edges), near-zero in
    # flat regions (out-of-focus blur, OR a blown-out clipped highlight --
    # both look "flat" to this operator, which is exactly the problem
    # being solved in Step 7).
    laplacian = cv2.Laplacian(scored_region, cv2.CV_64F)

    # STEP 6 -- Build the clipping mask (unchanged from the original
    # whole-cutout version -- see CLIP_THRESHOLD comment above).
    unclipped_mask = scored_region < CLIP_THRESHOLD

    total_px = scored_region.size
    unclipped_px = int(np.count_nonzero(unclipped_mask))
    clipped_fraction = 1.0 - (unclipped_px / total_px) if total_px > 0 else 0.0

    # STEP 7 -- Variance computed ONLY over unclipped pixels within the
    # scored (segmented + normalized) region.
    values = laplacian[unclipped_mask]
    variance = float(values.var()) if values.size > 0 else 0.0

    # STEP 8 -- Log the full picture: which region was actually scored
    # (finger vs whole-frame fallback), the post-normalization size,
    # clipping stats, and the final score.
    debug_print(
        f"[BlurLaplacian] region={region_label} scoredSize={total_px and scored_region.shape[1]}x{scored_region.shape[0]} "
        f"clipped_fraction={clipped_fraction:.3f} unclipped_px={unclipped_px}/{total_px} variance={variance:.2f}"
    )

    # STEP 9 -- Return the score. LaplacianBlurMethod.kt compares this
    # directly against minVariance -- no other transformation happens on
    # the Kotlin side. NOTE: this threshold now needs its OWN calibration
    # -- it is NOT comparable to the old whole-cutout scale (was tuned
    # around 300-350; this version's real-device baseline measured tonight
    # was ~13-14 for genuinely sharp real captures -- see conversation
    # record for the full validation).
    return variance