import cv2
import numpy as np
import os
from PIL import Image, ImageOps

def create_foreground_only_image(image, mask):
    """
    Create an image with only the foreground (fingerprint) visible on white background.
    This prepares the image for Zero-DCE enhancement.
    """
    # Create white background
    white_bg_image = np.ones_like(image) * 255

    # Copy foreground pixels
    mask_3d = mask[:, :, np.newaxis]
    foreground_only = np.where(mask_3d, image, white_bg_image)

    return foreground_only.astype(np.uint8)

def adaptive_hist_equalize(image):
    """
    Apply Adaptive Histogram Equalization (AHE) to the image.
    """
    hist, bins = np.histogram(image.flatten(), 256, [0, 256])
    cdf = hist.cumsum()
    cdf_normalized = cdf * hist.max() / cdf.max()
    cdf_m = np.ma.masked_equal(cdf, 0)
    cdf_m = (cdf_m - cdf_m.min()) * 255 / (cdf_m.max() - cdf_m.min())
    cdf = np.ma.filled(cdf_m, 0).astype('uint8')
    img2 = cdf[image]
    return img2

def get_blur_map(image, mask, win_size=15, sv_num=5, subtract_mean=True):
    """
    Enhanced blur detection function (red=blur, blue=focus).
    Uses U²-Net TFLite mask instead of SAM.
    """
    img_gray = cv2.cvtColor(image, cv2.COLOR_RGB2GRAY)

    # 3) SVD-based focus analysis
    H, W = img_gray.shape

    # Build mirrored version
    pad = win_size
    new_H = H + 2 * pad
    new_W = W + 2 * pad
    new_img = np.zeros((new_H, new_W), dtype=np.uint8)

    # Copy the interior
    new_img[pad:pad+H, pad:pad+W] = img_gray

    # Mirror borders
    new_img[0:pad, pad:pad+W] = img_gray[pad:0:-1, :]
    new_img[pad+H:new_H, pad:pad+W] = img_gray[H-1:H-pad-1:-1, :]
    new_img[:, 0:pad] = new_img[:, pad*2:pad:-1]
    new_img[:, pad+W:new_W] = new_img[:, W+pad-1:W-1:-1]

    # Allocate focus quality map (high values = good focus)
    focus_map = np.zeros((H, W), dtype=np.float32)
    min_sv = 1.0
    max_sv = 0.0

    # Slide over each pixel
    for i in range(H):
        for j in range(W):
            # Extract patch
            patch = new_img[i:i + 2*pad, j:j + 2*pad].astype(np.float32)

            # Zero-center the patch for better SVD analysis
            if subtract_mean:
                patch = patch - patch.mean()

            try:
                U, s, VT = np.linalg.svd(patch, full_matrices=False)
            except np.linalg.LinAlgError:
                s = np.zeros(min(patch.shape), dtype=np.float32)

            # Sum top-sv_num singular values
            top_sv_sum = np.sum(s[:sv_num])
            total_sv_sum = np.sum(s)

            if total_sv_sum == 0:
                beta1 = 0.0
            else:
                beta1 = top_sv_sum / total_sv_sum

            focus_map[i, j] = beta1

            if beta1 < min_sv:
                min_sv = beta1
            if beta1 > max_sv:
                max_sv = beta1

    # Apply mask if using SAM
    if mask is not None:
        focus_map = focus_map * mask
        # Recalculate min/max only for masked region
        masked_values = focus_map[mask > 0]
        if len(masked_values) > 0:
            min_sv = masked_values.min()
            max_sv = masked_values.max()

    # Normalize to [0,1]
    if max_sv > min_sv:
        focus_map = (focus_map - min_sv) / (max_sv - min_sv)
    else:
        focus_map[:] = 0.0

    # Convert to academic standard visualization (red=blur, blue=focus)
    blur_map = 1-focus_map
    return blur_map

def apply_adaptive_mean_thresholding(image, method="GAUSSIAN", block_size=7, subtraction_const=20):
    """
    Function to apply adaptive mean thresholding to extract fingerprint edges.
    """
    if len(image.shape) == 3:
        image_gray = cv2.cvtColor(image.astype(np.uint8), cv2.COLOR_RGB2GRAY)
    else:
        image_gray = image.astype(np.uint8)

    if method == "GAUSSIAN":
        thresholded_image = cv2.adaptiveThreshold(image_gray, 255, cv2.ADAPTIVE_THRESH_GAUSSIAN_C,
                                                  cv2.THRESH_BINARY, block_size, subtraction_const)
    elif method == "MEAN":
        thresholded_image = cv2.adaptiveThreshold(image_gray, 255, cv2.ADAPTIVE_THRESH_MEAN_C,
                                                  cv2.THRESH_BINARY, block_size, subtraction_const)
    else:
        print("Wrong method name used!")
        return None

    return thresholded_image

def process(image_bytes, masked_image_bytes, blur_threshold=0.3):
    """
    Complete fingerprint processing pipeline with luminescence-based decision for enhancement.
    """
    print("[U2NetPreProcessing] Step 1: Read images and get mask...")
    # Decode from bytes into NumPy arrays
    image = cv2.imdecode(np.frombuffer(image_bytes, np.uint8), cv2.IMREAD_COLOR)
    masked_image = cv2.imdecode(np.frombuffer(masked_image_bytes, np.uint8), cv2.IMREAD_GRAYSCALE)
    mask = np.array(masked_image == 255, dtype=np.uint8)

    print("[U2NetPreProcessing] Step 2: Creating foreground-only image...")
    foreground_only_image = create_foreground_only_image(image, mask)
    foreground_only_image = cv2.cvtColor(foreground_only_image, cv2.COLOR_RGB2GRAY)

    print("[U2NetPreProcessing] Step 3: Adaptive histogram equalization...")
    enhanced_image = adaptive_hist_equalize(foreground_only_image)
    enhanced_image = cv2.cvtColor(enhanced_image, cv2.COLOR_GRAY2RGB)

    print("[U2NetPreProcessing] Step 4: Computing blur map...")
#     blur_map = get_blur_map(image, mask, win_size=10, sv_num=3)

    print("[U2NetPreProcessing] Step 5: Applying adaptive thresholding...")
    th_image = apply_adaptive_mean_thresholding(enhanced_image, "MEAN", 15, 1)
    inv_img = 255 - th_image
    inv_img[mask == 0] = 255

    print("[U2NetPreProcessing] Step 6: Applying blur removal...")
#     target_shape = inv_img.shape
#     if blur_map.shape != target_shape:
#         blur_map_resized = cv2.resize(blur_map, (target_shape[1], target_shape[0]), interpolation=cv2.INTER_LINEAR)
#         mask_resized = cv2.resize(mask.astype(np.uint8), (target_shape[1], target_shape[0]), interpolation=cv2.INTER_NEAREST)
#     else:
#         blur_map_resized = blur_map
#         mask_resized = mask

    print("[U2NetPreProcessing] Processing Done")
    final_image = inv_img.copy()
#     blur_pixels = blur_map_resized >= blur_threshold
#     final_result[blur_pixels] = 255
#     final_result[mask_resized == 0] = 255


    #Print statistics
#     foreground_pixels = np.sum(mask_resized > 0)
#     total_blur_pixels = np.sum(blur_pixels)
#
#     print(f"[U2NetPreProcessing] Processing Statistics:")
#     print(f"[U2NetPreProcessing] Total foreground pixels: {foreground_pixels}")
#     print(f"[U2NetPreProcessing] Blur pixels removed: {total_blur_pixels}")
#     print(f"[U2NetPreProcessing] Blur threshold: {blur_threshold}")
#     print(f"[U2NetPreProcessing] Percentage of foreground affected by blur: {100 * total_blur_pixels / foreground_pixels:.2f}%")

    _, encoded_final_image = cv2.imencode(".jpg", final_image, [int(cv2.IMWRITE_JPEG_QUALITY), 100])

    return encoded_final_image.tobytes()