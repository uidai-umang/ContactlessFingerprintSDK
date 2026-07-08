import numpy as np
import cv2
import time
from collections import deque
from typing import Tuple
from scipy.signal import welch

class VideoLivenessDetector:
    def __init__(self,
                 roi: Tuple[int, int, int, int] = (0, 0, 0, 0),
                 sample_seconds: float = 2.0,
                 fps: int = 24):
        self.roi = roi
        self.sample_seconds = sample_seconds
        self.fps = fps
        self.buffer: deque[float] = deque()
        self.start_time: float | None = None
        print(f"[Liveness] Initialised: ROI={roi}, sample={sample_seconds}s, fps={fps}")

    def _select_roi(self, frame: np.ndarray) -> Tuple[int, int, int, int]:
        if any(self.roi):
            print(f"[Liveness] Using fixed ROI: {self.roi}")
            return self.roi
        h, w = frame.shape[:2]
        size = min(h, w) // 3
        x = (w - size) // 2
        y = (h - size) // 2
        roi = (x, y, size, size)
        print(f"[Liveness] Auto ROI selected: {roi}")
        return roi

    def add_frame_nv21(self, nv21_bytes: bytes, width: int, height: int) -> None:
        if self.start_time is None:
            self.start_time = time.time()
            print(f"[Liveness] First frame received at {self.start_time:.3f}")

        yuv = np.frombuffer(nv21_bytes, dtype=np.uint8)
        yuv = yuv.reshape((height + height // 2, width))
        bgr = cv2.cvtColor(yuv, cv2.COLOR_YUV2BGR_NV21)

        x, y, w, h = self._select_roi(bgr)
        roi_frame = bgr[y:y + h, x:x + w]

        avg_intensity = roi_frame[:, :, 1].mean()
        self.buffer.append(avg_intensity)
        print(f"[Liveness] Frame appended: intensity={avg_intensity:.2f}, buffer_len={len(self.buffer)}")

        max_len = int(self.sample_seconds * self.fps)
        if len(self.buffer) > max_len:
            removed = self.buffer.popleft()
            print(f"[Liveness] Buffer trimmed: removed oldest intensity={removed:.2f}")

    @property
    def ready(self) -> bool:
        elapsed = (time.time() - self.start_time) if self.start_time else 0
        ready_state = elapsed >= self.sample_seconds
        print(f"[Liveness] Ready check: elapsed={elapsed:.2f}s, ready={ready_state}")
        return ready_state

    def analyse(self, power_threshold, freq_range_min, freq_range_max) -> bool:
        if len(self.buffer) < self.fps * self.sample_seconds:
            print(f"[Liveness] Not enough data for analysis: {len(self.buffer)} samples")
            return False

        signal = np.array(self.buffer) - np.mean(self.buffer)
        freqs, psd = welch(signal, fs=self.fps, nperseg=min(256, len(signal)))
        band_mask = (freqs >= freq_range_min) & (freqs <= freq_range_max)
        band_power = psd[band_mask].sum()
        total_power = psd.sum() + 1e-8
        ratio = band_power / total_power
        print(f"[Liveness] Analysis: band_power={band_power:.4f}, total_power={total_power:.4f}, ratio={ratio:.3f}")

        is_live = band_power <= power_threshold
        print(f"[Liveness] Liveness result: {is_live}")
        status = 0 if is_live else -1
        return {
           "status": status,
           "confidence": band_power
       }

detector = VideoLivenessDetector(sample_seconds=2.0, fps=24)

def configure_detector(sample_seconds: float = 2.0, fps: int = 24):
    global detector
    if detector is None or detector.sample_seconds != sample_seconds or detector.fps != fps:
        print(f"[Liveness] Re-initializing detector. Sample Seconds: {sample_seconds}, FPS: {fps}")


def add_frame(nv21_bytes: bytes, width: int, height: int, sample_seconds: float = 2.0, fps: int = 24):
    configure_detector(sample_seconds, fps)
    detector.add_frame_nv21(nv21_bytes, width, height)

def ready():
    if detector is None:
        return False
    return detector.ready

def analyse(power_threshold=10, freq_range_min=0.75, freq_range_max=4.0):
    if detector is None:
        return {
            "status": -1,
            "confidence": 1
        }
    return detector.analyse(power_threshold, freq_range_min, freq_range_max)
