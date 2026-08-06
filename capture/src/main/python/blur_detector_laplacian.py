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
    yuv = np.frombuffer(nv21_bytes, dtype=np.uint8).reshape((height * 3 // 2, width))
    bgr = cv2.cvtColor(yuv, cv2.COLOR_YUV2BGR_NV21)
    gray = cv2.cvtColor(bgr, cv2.COLOR_BGR2GRAY)

    laplacian = cv2.Laplacian(gray, cv2.CV_64F)
    unclipped_mask = gray < CLIP_THRESHOLD

    total_px = gray.size
    unclipped_px = int(np.count_nonzero(unclipped_mask))
    clipped_fraction = 1.0 - (unclipped_px / total_px)

    values = laplacian[unclipped_mask]
    variance = float(values.var()) if values.size > 0 else 0.0

    debug_print(
        f"[BlurLaplacian] clipped_fraction={clipped_fraction:.3f} "
        f"unclipped_px={unclipped_px}/{total_px} variance={variance:.2f}"
    )
    return variance