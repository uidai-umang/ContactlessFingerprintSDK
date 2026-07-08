import cv2
import numpy as np

def bright_spot(gray_image, threshold, variance_min, variance_max, max_glare_value):
    """
    Analyzes a grayscale image for bright spots.

    Args:
        gray_image: A 2D NumPy array representing the grayscale image.
        threshold (int): The binary threshold for detecting bright spots.
        variance_min (int): The minimum variance to be considered glare.
        variance_max (int): The maximum variance to be considered glare.
    """
    # Apply binary thresholding for bright spot detection
    # The image is already grayscale, so no cvtColor is needed.
    _, binary_image = cv2.threshold(gray_image, threshold, 255, cv2.THRESH_BINARY)
    binary_variance = binary_image.var()

    failed = (variance_min < binary_variance < variance_max)
    status = -1 if failed else 0

    return {
        "status": status,
        "confidence": min(binary_variance / max_glare_value, 1)
    }

def main(nv21_data: bytes, width: int, height: int, threshold=200, variance_min=10500, variance_max=15000, max_glare_value=20000):
    """
    Processes an image in NV21 format passed as a byte array.

    Args:
        nv21_data: A byte array of the image in NV21 format.
        width: The width of the image.
        height: The height of the image.
        threshold (int): The binary threshold for detecting bright spots.
        variance_min (int): The minimum variance to be considered glare.
        variance_max (int): The maximum variance to be considered glare.
    """
    # In NV21, the Y plane (grayscale) is the first 'width * height' bytes.
    # We extract this plane directly.
    y_plane_size = width * height
    y_plane = np.frombuffer(nv21_data, dtype=np.uint8, count=y_plane_size)

    # Reshape the 1D Y plane array into a 2D image (height x width).
    gray_image = y_plane.reshape((height, width))

    final = bright_spot(gray_image, threshold, variance_min, variance_max, max_glare_value)
    return final