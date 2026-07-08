import cv2
import numpy as np

def main(nv21_data: bytes, width: int, height: int, dark_percent=0.60, dark_threshold=50, bright_percent=0.60, bright_threshold=200):
    """
    Processes an image in NV21 format passed as a byte array.

    Args:
        nv21_data: A byte array of the image in NV21 format.
        width: The width of the image.
        height: The height of the image.
    """

    # Convert byte array to numpy array
    yuv = np.frombuffer(nv21_data, dtype=np.uint8)

    # Reshape to match NV21 format: Y plane + interleaved VU plane
    yuv = yuv.reshape((height * 3 // 2, width))

    # Convert YUV (NV21) to BGR
    img = cv2.cvtColor(yuv, cv2.COLOR_YUV2BGR_NV21)

    # Calculate the histogram
    hist = cv2.calcHist([img], [0], None, [256], [0, 256])
    total_pixels = img.shape[0] * img.shape[1]

    # Percentage of pixels that are 'dark' (e.g., value < 50)
    dark_pixels = np.sum(hist[:dark_threshold])
    dark_ratio = dark_pixels / total_pixels

    # Percentage of pixels that are 'bright' (e.g., value > 200)
    bright_pixels = np.sum(hist[bright_threshold:])
    bright_ratio = bright_pixels / total_pixels

    print(f"LIGHT -- Percentage of dark pixels (<50): {dark_ratio*100:.2f}%")
    print(f"LIGHT -- Percentage of bright pixels (>200): {bright_ratio*100:.2f}%")

    if dark_ratio > dark_percent:
        # Too Dark
        return {"status": -1,"confidence": dark_ratio}
    elif bright_ratio > bright_percent:
        # Too Bright
        return {"status": 1,"confidence": bright_ratio}
    else:
        # OK
        confidence = (bright_ratio + dark_ratio) / 2
        return {"status": 0,"confidence": confidence}