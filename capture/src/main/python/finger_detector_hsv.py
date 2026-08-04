import cv2
import numpy as np

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

    def skin_mask_ycrcb(self, bgr_frame):
        """YCrCb decouples luminance from chrominance — resists yellow/warm
        lighting shifts far better than HSV's hue channel."""
        ycrcb = cv2.cvtColor(bgr_frame, cv2.COLOR_BGR2YCrCb)
        lower_skin = np.array([0, 133, 77], dtype=np.uint8)
        upper_skin = np.array([255, 173, 127], dtype=np.uint8)
        mask = cv2.inRange(ycrcb, lower_skin, upper_skin)
        kernel = cv2.getStructuringElement(cv2.MORPH_ELLIPSE, (5, 5))
        mask = cv2.erode(mask, kernel, iterations=1)
        mask = cv2.dilate(mask, kernel, iterations=2)
        mask = cv2.GaussianBlur(mask, (5, 5), 0)
        return mask

    def find_finger_contour(self, mask, frame_area):
        contours, _ = cv2.findContours(mask, cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_SIMPLE)
        if not contours:
            return None, None, None
        contour = max(contours, key=cv2.contourArea)
        area = cv2.contourArea(contour)
        area_ratio = area / frame_area
        return contour, area, area_ratio

    def geometry_check(self, contour):
        """Real, structural check — catches finger+background merged blobs
        that pure color masking can't distinguish. A single vertical finger
        has a distinctly taller-than-wide shape and moderate fill extent."""
        x, y, w, h = cv2.boundingRect(contour)
        if w == 0 or h == 0:
            return False
        aspect_ratio = float(h) / w
        extent = float(cv2.contourArea(contour)) / (w * h)
        is_valid = (aspect_ratio >= 1.0) and (extent >= 0.35)
        debug_print(f"[FingerDetect] AspectRatio: {aspect_ratio:.2f} | Extent: {extent:.2f} | Valid: {is_valid}")
        return is_valid

    def texture_check(self, roi, contour, x, y):
        """Laplacian variance + edge density, measured ONLY inside the
        contour shape — not the rectangular bounding box. Prevents
        background pixels in the box's corners from inflating the reading."""
        if roi.size == 0 or roi.shape[0] < 5 or roi.shape[1] < 5:
            debug_print("[FingerDetect] ROI too small for texture analysis")
            return False, False

        mask = np.zeros(roi.shape[:2], dtype=np.uint8)
        shifted_contour = contour - [x, y]
        cv2.drawContours(mask, [shifted_contour], -1, 255, thickness=cv2.FILLED)

        gray = cv2.cvtColor(roi, cv2.COLOR_BGR2GRAY)
        laplacian = cv2.Laplacian(gray, cv2.CV_64F)
        masked_lap_values = laplacian[mask == 255]
        lap_var = masked_lap_values.var() if masked_lap_values.size > 0 else 0.0
        lap_passed = (lap_var > self.texture_threshold_min) and (lap_var < self.texture_threshold_max)

        edges = cv2.Canny(gray, 50, 150)
        masked_edges = cv2.bitwise_and(edges, edges, mask=mask)
        mask_pixel_count = np.count_nonzero(mask)
        edge_density = (np.count_nonzero(masked_edges) / mask_pixel_count) * 100.0 if mask_pixel_count > 0 else 0.0
        edge_passed = (edge_density > self.edge_density_threshold_min) and (edge_density < self.edge_density_threshold_max)

        debug_print(f"[FingerDetect] LapVar: {lap_var:.2f} | EdgeDensity: {edge_density:.2f}%")
        return lap_passed, edge_passed

    def edge_fallback_mask(self, gray_frame):
        """Fallback when color-based masking finds nothing usable — detects
        object BOUNDARIES via CLAHE-enhanced local contrast + Canny, rather
        than color. Catches the same-colored-background case HSV/YCrCb
        structurally cannot."""
        clahe = cv2.createCLAHE(clipLimit=2.0, tileGridSize=(8, 8))
        enhanced = clahe.apply(gray_frame)
        edges = cv2.Canny(enhanced, 50, 150)
        kernel = cv2.getStructuringElement(cv2.MORPH_ELLIPSE, (5, 5))
        closed = cv2.morphologyEx(edges, cv2.MORPH_CLOSE, kernel, iterations=2)
        filled = cv2.dilate(closed, kernel, iterations=1)
        return filled

    def area_ratio_status(self, area_ratio):
        if area_ratio < self.good_distance_ratio_min:
            return 2
        elif area_ratio > self.max_finger_ratio:
            return 3
        elif area_ratio > self.good_distance_ratio_max:
            return 1
        return 0

    def process(self, nv21_bytes, width, height, rotation_degrees):
        bgr_frame = self.nv21_to_bgr(nv21_bytes, width, height)

        # Rotate to upright BEFORE any geometry-dependent check — the raw
        # buffer is in sensor-native orientation, and geometry_check's
        # aspect-ratio assumption only holds once the frame is genuinely
        # portrait-upright.
        if rotation_degrees == 90:
            bgr_frame = cv2.rotate(bgr_frame, cv2.ROTATE_90_CLOCKWISE)
        elif rotation_degrees == 180:
            bgr_frame = cv2.rotate(bgr_frame, cv2.ROTATE_180)
        elif rotation_degrees == 270:
            bgr_frame = cv2.rotate(bgr_frame, cv2.ROTATE_90_COUNTERCLOCKWISE)

        frame_area = bgr_frame.shape[0] * bgr_frame.shape[1]

        mask = self.skin_mask_ycrcb(bgr_frame)
        contour, area, area_ratio = self.find_finger_contour(mask, frame_area)

        used_fallback = False
        # Fallback ONLY if color mask found nothing, or found something that
        # fails geometry (the merged-blob signature) — not on every frame,
        # keeping the fast path fast.
        if contour is None or (contour is not None and not self.geometry_check(contour)):
            debug_print("[FingerDetect] Color mask insufficient — trying edge fallback")
            gray = cv2.cvtColor(bgr_frame, cv2.COLOR_BGR2GRAY)
            edge_mask = self.edge_fallback_mask(gray)
            fallback_contour, fallback_area, fallback_ratio = self.find_finger_contour(edge_mask, frame_area)
            if fallback_contour is not None and self.geometry_check(fallback_contour):
                contour, area, area_ratio = fallback_contour, fallback_area, fallback_ratio
                used_fallback = True

        if contour is None:
            debug_print("[FingerDetect] FINAL status=-1 (no contour, fallback also failed)")
            return {"status": -1, "box": None, "confidence": 0.0}

        debug_print(f"[FingerDetect] Area Ratio: {area_ratio:.3f} | usedFallback={used_fallback}")
        
        if area_ratio < self.min_finger_ratio:
            debug_print(f"[FingerDetect] FINAL status=-1 (below min_finger_ratio)")
            return {"status": -1, "box": None, "confidence": area_ratio}

        x, y, w, h = cv2.boundingRect(contour)
        box = [x, y, w, h]

        if not self.geometry_check(contour):
            debug_print("[FingerDetect] Geometry check FAILED")
            return {"status": -1, "box": box, "confidence": area_ratio}

        roi = bgr_frame[y:y+h, x:x+w]
        lap_passed, edge_passed = self.texture_check(roi, contour, x, y)
        if not lap_passed or not edge_passed:
            debug_print("[FingerDetect] Texture check FAILED")
            return {"status": -1, "box": box, "confidence": area_ratio}

        status = self.area_ratio_status(area_ratio)
        debug_print(f"[FingerDetect] FINAL status={status} confidence={area_ratio:.3f}")
        return {"status": status, "box": box, "confidence": area_ratio}

fd = FingerDetector()

def main(
        nv21_bytes, width, height, rotation_degrees,
        min_finger_ratio, max_finger_ratio,
        good_distance_ratio_min, good_distance_ratio_max,
        texture_threshold_min, texture_threshold_max,
        edge_density_threshold_min, edge_density_threshold_max
):
    fd.min_finger_ratio = min_finger_ratio
    fd.max_finger_ratio = max_finger_ratio
    fd.good_distance_ratio_min = good_distance_ratio_min
    fd.good_distance_ratio_max = good_distance_ratio_max
    fd.texture_threshold_min = texture_threshold_min
    fd.texture_threshold_max = texture_threshold_max
    fd.edge_density_threshold_min = edge_density_threshold_min
    fd.edge_density_threshold_max = edge_density_threshold_max
    return fd.process(nv21_bytes, width, height, rotation_degrees)