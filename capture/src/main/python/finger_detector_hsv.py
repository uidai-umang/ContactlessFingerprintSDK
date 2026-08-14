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
    WEIGHT_COLOR = 0.30
    WEIGHT_GEOMETRY = 0.25
    WEIGHT_TEXTURE = 0.25
    WEIGHT_CENTER = 0.20
    FINAL_SCORE_THRESHOLD = 0.55  # starting guess, needs real calibration

    # Hard gates — evaluated BEFORE weighted scoring, not folded into the
    # average. Confirmed necessary: on a real background-only wood image,
    # a candidate scored color=0.86 geom=0.72 texture=0.06, and the
    # weighted average (0.601) still cleared threshold despite texture
    # correctly flagging "no ridges here." These gates give texture and
    # ridge a veto instead of a vote.
    MIN_TEXTURE_TO_QUALIFY = 0.35
    MIN_RIDGE_TO_QUALIFY = 0.15  # untested guess, needs real calibration

    def nv21_to_bgr(self, nv21_bytes, width, height):
        """STEP 1 — Decode the raw camera buffer.
        NV21 is Android's native camera format (Y plane + interleaved
        VU plane). Converts it to a standard 3-channel BGR image that
        every downstream OpenCV function expects."""
        yuv = np.frombuffer(nv21_bytes, dtype=np.uint8).reshape(height + height // 2, width)
        bgr = cv2.cvtColor(yuv, cv2.COLOR_YUV2BGR_NV21)
        debug_print(f"[FingerDetect] STEP 1 -- decoded NV21 -> BGR, shape={bgr.shape}")
        return bgr

    def skin_mask_ycrcb(self, bgr_frame):
        """STEP 3a — Color-based candidate mask.
        Converts to YCrCb (separates brightness from color) and thresholds
        the Cr/Cb channels to a fixed skin-tone range. Cheap and fast, but
        can merge with any background that happens to share that range —
        see get_candidate_contours() for how that risk is bounded."""
        ycrcb = cv2.cvtColor(bgr_frame, cv2.COLOR_BGR2YCrCb)
        lower_skin = np.array([0, 133, 77], dtype=np.uint8)
        upper_skin = np.array([255, 173, 127], dtype=np.uint8)
        mask = cv2.inRange(ycrcb, lower_skin, upper_skin)
        # Clean up speckle noise before contour detection
        kernel = cv2.getStructuringElement(cv2.MORPH_ELLIPSE, (5, 5))
        mask = cv2.erode(mask, kernel, iterations=1)
        mask = cv2.dilate(mask, kernel, iterations=2)
        mask = cv2.GaussianBlur(mask, (5, 5), 0)
        debug_print(f"[FingerDetect] STEP 3a -- color mask built, skin-colored pixels={np.count_nonzero(mask)}")
        return mask

    def edge_mask(self, gray_frame):
        """STEP 3b — Edge-based candidate mask (fallback path).
        CLAHE boosts local contrast, then Canny finds boundaries by
        intensity gradient rather than color. Catches cases where the
        finger and background share a color but still have SOME visible
        edge between them."""
        clahe = cv2.createCLAHE(clipLimit=2.0, tileGridSize=(8, 8))
        enhanced = clahe.apply(gray_frame)
        edges = cv2.Canny(enhanced, 50, 150)
        # Stitch nearby edge fragments into closed, contourable shapes
        kernel = cv2.getStructuringElement(cv2.MORPH_ELLIPSE, (3, 3))
        closed = cv2.morphologyEx(edges, cv2.MORPH_CLOSE, kernel, iterations=1)
        debug_print(f"[FingerDetect] STEP 3b -- edge mask built, edge pixels={np.count_nonzero(closed)}")
        return closed

    def get_candidate_contours(self, mask, frame_area, min_area_ratio=0.02, max_area_ratio=0.85):
        """STEP 4 — Turn a binary mask into a list of candidate shapes.
        min_area_ratio filters out tiny noise specks. max_area_ratio
        rejects the whole-frame phantom contour confirmed in real device
        logs (area_ratio 0.95-0.998, appearing on EVERY frame regardless
        of finger presence) — a real finger should never fill this much
        of the cutout."""
        contours, _ = cv2.findContours(mask, cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_SIMPLE)
        candidates = []
        for c in contours:
            area = cv2.contourArea(c)
            ratio = area / frame_area
            if min_area_ratio <= ratio <= max_area_ratio:
                candidates.append((c, area, ratio))
        debug_print(f"[FingerDetect] STEP 4 -- {len(contours)} raw contours -> {len(candidates)} pass size filter ({min_area_ratio}-{max_area_ratio})")
        return candidates

    # ---------------- Scoring functions, each returns 0.0-1.0 ---------------- #

    def score_color(self, contour, color_mask, x, y, w, h):
        """SCORE — Color plausibility.
        Fraction of THIS candidate's own pixels that fall inside the color
        mask. Works even for edge-sourced candidates — an edge contour
        that happens to sit on skin-toned pixels still scores well here,
        without needing color to have found it as a contour in the first
        place."""
        contour_mask = np.zeros((h, w), dtype=np.uint8)
        cv2.drawContours(contour_mask, [contour - [x, y]], -1, 255, thickness=cv2.FILLED)
        color_roi = color_mask[y:y+h, x:x+w]
        overlap = cv2.bitwise_and(contour_mask, color_roi)
        contour_pixels = np.count_nonzero(contour_mask)
        score = float(np.count_nonzero(overlap) / contour_pixels) if contour_pixels > 0 else 0.0
        debug_print(f"[FingerDetect]   color_score={score:.2f}")
        return score

    def score_geometry(self, contour, area, w, h):
        """SCORE — Shape plausibility.
        Rewards a tall, finger-like aspect ratio and a moderate fill
        extent. Penalizes near-total bounding-box fill (extent > 0.95) —
        the exact signature of a merged finger+background blob or a
        whole-frame edge collapse."""
        if w == 0 or h == 0:
            return 0.0
        aspect_ratio = float(h) / w
        extent = float(area) / (w * h)
        aspect_score = min(aspect_ratio / 1.0, 1.0)
        extent_score = min(extent / 0.35, 1.0)
        if extent > 0.95:
            extent_score *= 0.5
        score = (aspect_score + extent_score) / 2.0
        debug_print(f"[FingerDetect]   geometry_score={score:.2f} (aspect={aspect_ratio:.2f} extent={extent:.2f})")
        return score

    def score_texture(self, roi_gray, contour, x, y):
        """SCORE / GATE — Ridge-like local contrast.
        Measures Laplacian variance and Canny edge density, MASKED to the
        candidate's own contour shape only (excludes background pixels
        that would otherwise leak into a rectangular bounding box). This
        is the signal that correctly identifies flat, ridge-free surfaces
        — used as a hard gate below, not just a weighted vote."""
        if roi_gray.size == 0 or roi_gray.shape[0] < 5 or roi_gray.shape[1] < 5:
            debug_print("[FingerDetect]   texture_score=0.00 (ROI too small)")
            return 0.0
        mask = np.zeros(roi_gray.shape[:2], dtype=np.uint8)
        cv2.drawContours(mask, [contour - [x, y]], -1, 255, thickness=cv2.FILLED)
        laplacian = cv2.Laplacian(roi_gray, cv2.CV_64F)
        masked_lap = laplacian[mask == 255]
        lap_var = masked_lap.var() if masked_lap.size > 0 else 0.0
        edges = cv2.Canny(roi_gray, 50, 150)
        masked_edges = cv2.bitwise_and(edges, edges, mask=mask)
        mask_px = np.count_nonzero(mask)
        edge_density = (np.count_nonzero(masked_edges) / mask_px) * 100.0 if mask_px > 0 else 0.0
        lap_score = self._band_score(lap_var, self.texture_threshold_min, self.texture_threshold_max)
        edge_score = self._band_score(edge_density, self.edge_density_threshold_min, self.edge_density_threshold_max)
        score = (lap_score + edge_score) / 2.0
        debug_print(f"[FingerDetect]   texture_score={score:.2f} (LapVar={lap_var:.2f} EdgeDensity={edge_density:.2f}%)")
        return score

    def score_center(self, contour, frame_w, frame_h):
        """SCORE — Positional plausibility.
        A real finger placed in a guided cutout should land roughly
        centered. Distance of the contour's centroid from the frame center,
        normalized to 0-1 (1.0 = dead center)."""
        M = cv2.moments(contour)
        if M["m00"] == 0:
            return 0.0
        cx = M["m10"] / M["m00"]
        cy = M["m01"] / M["m00"]
        center_x, center_y = frame_w / 2.0, frame_h / 2.0
        dist = np.sqrt((cx - center_x) ** 2 + (cy - center_y) ** 2)
        max_dist = np.sqrt(center_x ** 2 + center_y ** 2)
        score = max(0.0, 1.0 - (dist / max_dist))
        debug_print(f"[FingerDetect]   center_score={score:.2f}")
        return score

    def ridge_periodicity_score(self, gray_roi, min_period_frac=1/9, max_period_frac=1/4):
        """SCORE / GATE — Fingerprint ridge periodicity via 2D FFT.
        Faithful port of the reference Kotlin ridgePeriodicityScore(): no
        Hanning window, no fftshift — DC stays at the corner (0,0), so the
        ring is measured with TOROIDAL (wraparound) distance from the
        corner, not straight-line distance from center. This is a
        DIFFERENT signal than texture: it measures whether contrast is
        PERIODIC in the way real ridges are, not just how much contrast
        exists."""
        h, w = gray_roi.shape
        if h < 20 or w < 20:
            debug_print("[FingerDetect]   ridge_score=0.00 (ROI too small)")
            return 0.0

        # Pad to the FFT-optimal size (matches reference exactly)
        m = cv2.getOptimalDFTSize(h)
        n = cv2.getOptimalDFTSize(w)
        padded = cv2.copyMakeBorder(gray_roi, 0, m - h, 0, n - w, cv2.BORDER_CONSTANT, value=0)
        debug_print(f"[FingerDetect]   ridge: padded {h}x{w} -> {m}x{n} for DFT")

        # Build complex plane and run the DFT (OpenCV's C-style API)
        planes = padded.astype(np.float32)
        complex_img = cv2.merge([planes, np.zeros_like(planes)])
        dft = cv2.dft(complex_img)
        mag_planes = cv2.split(dft)
        magnitude = cv2.magnitude(mag_planes[0], mag_planes[1])

        ph, pw = padded.shape
        min_radius = ph * min_period_frac
        max_radius = ph * max_period_frac

        # No fftshift -> low frequencies wrap to ALL FOUR corners, not just
        # one center point. Toroidal distance measures "closest wraparound
        # copy of the origin" instead of straight center-distance.
        i_idx = np.arange(ph)
        j_idx = np.arange(pw)
        i_dist = np.minimum(i_idx, ph - i_idx)
        j_dist = np.minimum(j_idx, pw - j_idx)
        dist = np.sqrt(i_dist[:, None] ** 2 + j_dist[None, :] ** 2)

        band_mask = (dist >= min_radius) & (dist <= max_radius)
        band_energy = magnitude[band_mask].sum()
        total_energy = magnitude.sum() + 1e-6
        score = float(band_energy / total_energy)
        debug_print(f"[FingerDetect]   ridge_score={score:.4f} (band=[{min_radius:.1f},{max_radius:.1f}]px, band_energy={band_energy:.1f}, total_energy={total_energy:.1f})")
        return score

    def _band_score(self, value, min_v, max_v):
        """Helper — maps a raw measurement to 0-1: full score inside
        [min_v, max_v], linearly ramping down outside it."""
        if value < min_v:
            return max(0.0, value / min_v) if min_v > 0 else 0.0
        if value > max_v:
            return max(0.0, 1.0 - (value - max_v) / max_v)
        return 1.0

    def area_ratio_status(self, area_ratio):
        """STEP 7 — Convert the winning candidate's size into a distance
        classification (too far / too close / good / too much close),
        matching the existing FingerResultStatus codes on the Kotlin side."""
        if area_ratio < self.good_distance_ratio_min:
            return 2
        elif area_ratio > self.max_finger_ratio:
            return 3
        elif area_ratio > self.good_distance_ratio_max:
            return 1
        return 0

    # ---------------- Public API ---------------- #

    def process(self, nv21_bytes, width, height, rotation_degrees):
        """MAIN ENTRY POINT — runs the full detection pipeline on one frame:
        decode -> rotate upright -> build both candidate masks -> gate and
        score every candidate -> pick the best -> classify its distance.
        Called once per frame from the Kotlin side via main()."""
        bgr_frame = self.nv21_to_bgr(nv21_bytes, width, height)

        # STEP 2 — Rotate to upright. The raw buffer is in sensor-native
        # orientation; geometry scoring only makes sense once portrait-upright.
        if rotation_degrees == 90:
            bgr_frame = cv2.rotate(bgr_frame, cv2.ROTATE_90_CLOCKWISE)
        elif rotation_degrees == 180:
            bgr_frame = cv2.rotate(bgr_frame, cv2.ROTATE_180)
        elif rotation_degrees == 270:
            bgr_frame = cv2.rotate(bgr_frame, cv2.ROTATE_90_COUNTERCLOCKWISE)
        debug_print(f"[FingerDetect] STEP 2 -- rotated {rotation_degrees} degrees, final shape={bgr_frame.shape}")

        frame_h, frame_w = bgr_frame.shape[:2]
        frame_area = frame_h * frame_w
        gray = cv2.cvtColor(bgr_frame, cv2.COLOR_BGR2GRAY)

        color_mask = self.skin_mask_ycrcb(bgr_frame)
        edge_mask = self.edge_mask(gray)

        color_candidates = self.get_candidate_contours(color_mask, frame_area)
        edge_candidates = self.get_candidate_contours(edge_mask, frame_area)
        all_candidates = color_candidates + edge_candidates
        debug_print(f"[FingerDetect] STEP 5 -- {len(color_candidates)} color candidates + {len(edge_candidates)} edge candidates = {len(all_candidates)} total")

        if not all_candidates:
            debug_print("[FingerDetect] FINAL status=-1 (no candidates from either mask)")
            return {"status": -1, "box": None, "confidence": 0.0}

        best = None
        best_score = -1.0
        best_area_ratio = 0.0

        # STEP 6 — Gate and score every candidate, keep the best survivor
        for idx, (contour, area, area_ratio) in enumerate(all_candidates):
            x, y, w, h = cv2.boundingRect(contour)
            roi_gray = gray[y:y+h, x:x+w]
            debug_print(f"[FingerDetect] --- candidate {idx+1}/{len(all_candidates)}  area_ratio={area_ratio:.3f} box=({x},{y},{w},{h}) ---")

            t_score = self.score_texture(roi_gray, contour, x, y)
            if t_score < self.MIN_TEXTURE_TO_QUALIFY:
                debug_print(f"[FingerDetect]   REJECTED -- texture gate failed ({t_score:.2f} < {self.MIN_TEXTURE_TO_QUALIFY})")
                continue

            ridge_score = self.ridge_periodicity_score(roi_gray)
            if ridge_score < self.MIN_RIDGE_TO_QUALIFY:
                debug_print(f"[FingerDetect]   REJECTED -- ridge gate failed ({ridge_score:.4f} < {self.MIN_RIDGE_TO_QUALIFY})")
                continue

            c_score = self.score_color(contour, color_mask, x, y, w, h)
            g_score = self.score_geometry(contour, area, w, h)
            ctr_score = self.score_center(contour, frame_w, frame_h)

            total = (
                    self.WEIGHT_COLOR * c_score +
                    self.WEIGHT_GEOMETRY * g_score +
                    self.WEIGHT_TEXTURE * t_score +
                    self.WEIGHT_CENTER * ctr_score
            )
            debug_print(f"[FingerDetect]   PASSED both gates -> total={total:.3f}")

            if total > best_score:
                best_score = total
                best = (contour, x, y, w, h)
                best_area_ratio = area_ratio

        # STEP 8 — Final decision
        if best is None or best_score < self.FINAL_SCORE_THRESHOLD:
            debug_print(f"[FingerDetect] FINAL status=-1 (best_score={best_score:.3f}, threshold={self.FINAL_SCORE_THRESHOLD})")
            return {"status": -1, "box": None, "confidence": best_score if best_score > 0 else 0.0}

        _, x, y, w, h = best
        box = [x, y, w, h]

        if best_area_ratio < self.min_finger_ratio:
            debug_print(f"[FingerDetect] FINAL status=-1 (below min_finger_ratio, score={best_score:.3f})")
            return {"status": -1, "box": box, "confidence": best_area_ratio}

        status = self.area_ratio_status(best_area_ratio)
        debug_print(f"[FingerDetect] FINAL status={status} score={best_score:.3f} area_ratio={best_area_ratio:.3f} box={box}")
        return {"status": status, "box": box, "confidence": best_area_ratio}


fd = FingerDetector()

def main(
        nv21_bytes, width, height, rotation_degrees,
        min_finger_ratio, max_finger_ratio,
        good_distance_ratio_min, good_distance_ratio_max,
        texture_threshold_min, texture_threshold_max,
        edge_density_threshold_min, edge_density_threshold_max
):
    """Chaquopy entry point — called once per frame from
    FingerCheckPythonMethod.kt. Loads the per-call config values onto the
    shared detector instance, then runs the pipeline."""
    fd.min_finger_ratio = min_finger_ratio
    fd.max_finger_ratio = max_finger_ratio
    fd.good_distance_ratio_min = good_distance_ratio_min
    fd.good_distance_ratio_max = good_distance_ratio_max
    fd.texture_threshold_min = texture_threshold_min
    fd.texture_threshold_max = texture_threshold_max
    fd.edge_density_threshold_min = edge_density_threshold_min
    fd.edge_density_threshold_max = edge_density_threshold_max
    return fd.process(nv21_bytes, width, height, rotation_degrees)