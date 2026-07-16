import cv2
import numpy as np
import sys

# Debug gate — checks Python's own optimization flag as a zero-config default.
# Running via `python -O` or equivalent strips debug output automatically.
# For explicit control from Kotlin, this can be overridden by calling
# set_debug_enabled(True/False) once at app startup — see note below.
_DEBUG_ENABLED = __debug__

def set_debug_enabled(enabled: bool):
    global _DEBUG_ENABLED
    _DEBUG_ENABLED = enabled

def debug_print(message):
    if _DEBUG_ENABLED:
        print(message)


class FingerDetector:
    def nv21_to_bgr(self, nv21_bytes, width, height):
        yuv = np.frombuffer(nv21_bytes, dtype=np.uint8).reshape(height + height // 2, width)
        return cv2.cvtColor(yuv, cv2.COLOR_YUV2BGR_NV21)

    def skin_mask(self, bgr_frame):
        hsv = cv2.cvtColor(bgr_frame, cv2.COLOR_BGR2HSV)
        lower_skin = np.array([0, 48, 80], dtype=np.uint8)
        upper_skin = np.array([20, 255, 255], dtype=np.uint8)
        mask = cv2.inRange(hsv, lower_skin, upper_skin)
        mask = cv2.GaussianBlur(mask, (5, 5), 0)
        mask = cv2.erode(mask, None, iterations=2)
        mask = cv2.dilate(mask, None, iterations=2)
        return mask

    def find_finger_contour(self, mask, frame_area):
        contours, _ = cv2.findContours(mask, cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_SIMPLE)
        if not contours:
            return None, None, None
        contour = max(contours, key=cv2.contourArea)
        area = cv2.contourArea(contour)
        area_ratio = area / frame_area
        return contour, area, area_ratio

    def texture_check(self, roi):
        """Hybrid texture metric: Laplacian variance + edge density."""
        if roi.size == 0 or roi.shape[0] < 5 or roi.shape[1] < 5:
            debug_print("[FingerDetect] ROI too small for texture analysis")
            return 0.0, 0.0
        gray = cv2.cvtColor(roi, cv2.COLOR_BGR2GRAY)
        laplacian = cv2.Laplacian(gray, cv2.CV_64F)
        lap_var = laplacian.var()
        _, stddev = cv2.meanStdDev(laplacian)
        lap_passed = (lap_var > self.texture_threshold_min) and (lap_var < self.texture_threshold_max)
        edges = cv2.Canny(gray, 50, 150)
        edge_density = (np.count_nonzero(edges) / edges.size) * 100.0
        edge_passed = (edge_density > self.edge_density_threshold_min) and (edge_density < self.edge_density_threshold_max)
        debug_print(f"[FingerDetect] LapVar: {lap_var:.2f} | EdgeDensity: {edge_density:.2f}% | StdDev: {float(stddev[0][0]):.2f}")
        return lap_passed, edge_passed

    def area_ratio_status(self, area_ratio):
        if area_ratio < self.good_distance_ratio_min:
            return 2
        elif area_ratio > self.max_finger_ratio:
            return 3
        elif area_ratio > self.good_distance_ratio_max:
            return 1
        return 0

    # ---------------- Public API ---------------- #
    def process(self, nv21_bytes, width, height):
        bgr_frame = self.nv21_to_bgr(nv21_bytes, width, height)
        frame_area = width * height
        mask = self.skin_mask(bgr_frame)
        contour, area, area_ratio = self.find_finger_contour(mask, frame_area)

        if contour is None:
            debug_print(f"[FingerDetect] FINAL status=-1 (no contour) confidence={area_ratio}")
            return {"status": -1, "box": None, "confidence": area_ratio}

        debug_print(f"[FingerDetect] Area Ratio: {area_ratio:.3f} | Area: {area}")

        if area_ratio < self.min_finger_ratio:
            debug_print(f"[FingerDetect] FINAL status=-1 (below min_finger_ratio) confidence={area_ratio:.3f}")
            return {"status": -1, "box": None, "confidence": area_ratio}

        x, y, w, h = cv2.boundingRect(contour)
        box = [x, y, w, h]
        roi = bgr_frame[y:y+h, x:x+w]

        lap_passed, edge_passed = self.texture_check(roi)
        if not lap_passed or not edge_passed:
            debug_print("[FingerDetect] Texture check FAILED")
            debug_print(f"[FingerDetect] FINAL status=-1 (texture failed) confidence={area_ratio:.3f}")
            return {"status": -1, "box": box, "confidence": area_ratio}

        status = self.area_ratio_status(area_ratio)
        debug_print(f"[FingerDetect] FINAL status={status} confidence={area_ratio:.3f}")
        return {"status": status, "box": box, "confidence": area_ratio}


fd = FingerDetector()

def main(
        nv21_bytes,
        width,
        height,
        min_finger_ratio,
        max_finger_ratio,
        good_distance_ratio_min,
        good_distance_ratio_max,
        texture_threshold_min,
        texture_threshold_max,
        edge_density_threshold_min,
        edge_density_threshold_max
):
    fd.min_finger_ratio = min_finger_ratio
    fd.max_finger_ratio = max_finger_ratio
    fd.good_distance_ratio_min = good_distance_ratio_min
    fd.good_distance_ratio_max = good_distance_ratio_max
    fd.texture_threshold_min = texture_threshold_min
    fd.texture_threshold_max = texture_threshold_max
    fd.edge_density_threshold_min = edge_density_threshold_min
    fd.edge_density_threshold_max = edge_density_threshold_max
    result = fd.process(nv21_bytes, width, height)
    return result