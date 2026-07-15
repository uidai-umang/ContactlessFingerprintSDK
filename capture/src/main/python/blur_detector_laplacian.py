# capture/src/main/python/blur_detector_laplacian.py

import cv2
import numpy as np

def main(nv21_bytes, width, height):
    yuv = np.frombuffer(nv21_bytes, dtype=np.uint8).reshape((height * 3 // 2, width))
    bgr = cv2.cvtColor(yuv, cv2.COLOR_YUV2BGR_NV21)
    gray = cv2.cvtColor(bgr, cv2.COLOR_BGR2GRAY)
    return float(cv2.Laplacian(gray, cv2.CV_64F).var())