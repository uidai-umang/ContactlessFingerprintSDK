#include <jni.h>
#include <android/bitmap.h>
#include <cstdint>
#include <algorithm> // For std::reverse

// Optimized for 90-degree clockwise rotation
void rotate90(const uint32_t *src, uint32_t *dst, int width, int height) {
    for (int y = 0; y < height; ++y) {
        for (int x = 0; x < width; ++x) {
            dst[(x * height) + (height - 1 - y)] = src[y * width + x];
        }
    }
}

// Optimized for 180-degree rotation
void rotate180(const uint32_t *src, uint32_t *dst, int width, int height) {
    int numPixels = width * height;
    // Copy source to destination
    memcpy(dst, src, numPixels * sizeof(uint32_t));
    // Reverse the pixel buffer in place
    std::reverse(dst, dst + numPixels);
}

// Optimized for 270-degree clockwise rotation (-90 degrees)
void rotate270(const uint32_t *src, uint32_t *dst, int width, int height) {
    for (int y = 0; y < height; ++y) {
        for (int x = 0; x < width; ++x) {
            dst[((width - 1 - x) * height) + y] = src[y * width + x];
        }
    }
}

extern "C" JNIEXPORT void JNICALL
Java_app_gov_uidai_capture_utils_nativelib_BitmapRotator_rotateBitmap(
    JNIEnv *env,
    jobject thiz,
    jobject bitmapIn,
    jobject bitmapOut,
    jint angle
) {

    AndroidBitmapInfo infoIn;
    if (AndroidBitmap_getInfo(env, bitmapIn, &infoIn) < 0) {
        return; // Error
    }

    if (infoIn.format != ANDROID_BITMAP_FORMAT_RGBA_8888) {
        return; // Only support ARGB_8888
    }

    void *pixelsIn;
    if (AndroidBitmap_lockPixels(env, bitmapIn, &pixelsIn) < 0) {
        return; // Error
    }

    void *pixelsOut;
    if (AndroidBitmap_lockPixels(env, bitmapOut, &pixelsOut) < 0) {
        AndroidBitmap_unlockPixels(env, bitmapIn);
        return; // Error
    }

    auto src = static_cast<const uint32_t *>(pixelsIn);
    auto dst = static_cast<uint32_t *>(pixelsOut);
    int width = infoIn.width;
    int height = infoIn.height;

    // Call the correct rotation function based on the angle
    switch (angle) {
        case 90:
            rotate90(src, dst, width, height);
            break;
        case 180:
            rotate180(src, dst, width, height);
            break;
        case 270:
            rotate270(src, dst, width, height);
            break;
        default:
        // For 0 degrees or other angles, just copy the bitmap
            memcpy(dst, src, width * height * sizeof(uint32_t));
            break;
    }

    AndroidBitmap_unlockPixels(env, bitmapIn);
    AndroidBitmap_unlockPixels(env, bitmapOut);
}