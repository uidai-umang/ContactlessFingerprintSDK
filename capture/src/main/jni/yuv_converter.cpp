#include <jni.h>
#include <cstring>
#include <algorithm>
#if defined(__ARM_NEON__)
#include <arm_neon.h>
#endif

extern "C" JNIEXPORT jbyteArray JNICALL
Java_app_gov_uidai_capture_utils_nativelib_YuvConverter_yuv420ToNv21Native(
        JNIEnv *env,
        jobject /* this */,
        jobject y_buffer,
        jobject u_buffer,
        jobject v_buffer,
        jint y_row_stride,
        jint u_row_stride,
        jint v_row_stride,
        jint u_pixel_stride,
        jint v_pixel_stride,
        jint width,
        jint height) {

    auto y_src = static_cast<const uint8_t *>(env->GetDirectBufferAddress(y_buffer));
    auto u_src = static_cast<const uint8_t *>(env->GetDirectBufferAddress(u_buffer));
    auto v_src = static_cast<const uint8_t *>(env->GetDirectBufferAddress(v_buffer));

    // NEW -- query the REAL buffer sizes directly from JNI, rather than
    // trusting the rowStride*height formula. On some devices (confirmed:
    // Unisoc/SPRD HAL) the actual delivered buffer is a few bytes short of
    // that formula, and reading/writing past it is an unchecked native
    // out-of-bounds access -- a hard, uncatchable crash when it happens to
    // cross a page boundary. Clamping every read to the real capacity
    // fixes this at the root, for any device, without needing per-device
    // detection logic.
    jlong y_capacity = env->GetDirectBufferCapacity(y_buffer);
    jlong u_capacity = env->GetDirectBufferCapacity(u_buffer);
    jlong v_capacity = env->GetDirectBufferCapacity(v_buffer);

    jbyteArray nv21_output_array = env->NewByteArray(width * height * 3 / 2);
    auto nv21_output_ptr = env->GetByteArrayElements(nv21_output_array, nullptr);
    auto nv21_dst = reinterpret_cast<uint8_t *>(nv21_output_ptr);

    // 1. Copy Y Plane
    uint8_t *y_dst = nv21_dst;
    if (y_row_stride == width) {
        size_t y_needed = static_cast<size_t>(width) * height;
        size_t y_safe = std::min(y_needed, static_cast<size_t>(y_capacity));
        memcpy(y_dst, y_src, y_safe);
        if (y_safe < y_needed) {
            memset(y_dst + y_safe, 0, y_needed - y_safe);
        }
    } else {
        for (int i = 0; i < height; ++i) {
            size_t row_offset = static_cast<size_t>(i) * y_row_stride;
            size_t bytes_available = row_offset < static_cast<size_t>(y_capacity)
                                     ? std::min(static_cast<size_t>(width), static_cast<size_t>(y_capacity) - row_offset)
                                     : 0;
            memcpy(y_dst + i * width, y_src + row_offset, bytes_available);
            if (bytes_available < static_cast<size_t>(width)) {
                memset(y_dst + i * width + bytes_available, 0, width - bytes_available);
            }
        }
    }

    // 2. Interleave U and V Planes -- NOW BOUNDS-CHECKED per row
    uint8_t *vu_dst = nv21_dst + width * height;
    int uv_width = width / 2;
    int uv_height = height / 2;

    for (int row = 0; row < uv_height; ++row) {
        size_t v_row_offset = static_cast<size_t>(row) * v_row_stride;
        size_t u_row_offset = static_cast<size_t>(row) * u_row_stride;

        const uint8_t *v_row_ptr = v_src + v_row_offset;
        const uint8_t *u_row_ptr = u_src + u_row_offset;
        uint8_t *vu_row_dst_ptr = vu_dst + (row * width);

        // How many bytes are actually safe to read from THIS row, given
        // the real buffer capacity. Normally equal to the full row; only
        // differs on the last row of a short buffer.
        long v_row_bytes_safe = static_cast<long>(v_capacity) - static_cast<long>(v_row_offset);
        long u_row_bytes_safe = static_cast<long>(u_capacity) - static_cast<long>(u_row_offset);
        bool row_fully_safe =
                v_row_bytes_safe >= v_row_stride && u_row_bytes_safe >= u_row_stride;

        int col = 0;
#if defined(__ARM_NEON__)
        if (row_fully_safe) {
            // Full-speed NEON path -- UNCHANGED from before, only runs
            // when the whole row is confirmed within bounds.
            for (; col <= uv_width - 8; col += 8) {
                if (u_pixel_stride == 1 && v_pixel_stride == 1) {
                    uint8x8_t u_vec = vld1_u8(u_row_ptr + col);
                    uint8x8_t v_vec = vld1_u8(v_row_ptr + col);
                    uint8x8x2_t vu_vec;
                    vu_vec.val[0] = v_vec;
                    vu_vec.val[1] = u_vec;
                    vst2_u8(vu_row_dst_ptr + (col * 2), vu_vec);
                } else {
                    uint8x8x2_t u_deinterleaved = vld2_u8(u_row_ptr + col * u_pixel_stride);
                    uint8x8x2_t v_deinterleaved = vld2_u8(v_row_ptr + col * v_pixel_stride);
                    uint8x8x2_t vu_to_store;
                    vu_to_store.val[0] = v_deinterleaved.val[0];
                    vu_to_store.val[1] = u_deinterleaved.val[0];
                    vst2_u8(vu_row_dst_ptr + (col * 2), vu_to_store);
                }
            }
        }
        // If !row_fully_safe (only ever the last row on affected devices),
        // col stays at 0 and every pixel in this row falls through to the
        // bounds-checked scalar loop below -- this only costs extra time
        // on ONE row per frame, not the whole image.
#endif
        // Scalar path: handles any remaining columns from the NEON loop
        // (row_fully_safe case), AND the entire row when !row_fully_safe.
        // Bounds-checked per pixel ONLY in the rare short-row case.
        for (; col < uv_width; ++col) {
            size_t v_offset = static_cast<size_t>(col) * v_pixel_stride;
            size_t u_offset = static_cast<size_t>(col) * u_pixel_stride;

            uint8_t v_val = (row_fully_safe || static_cast<long>(v_offset) < v_row_bytes_safe)
                            ? *(v_row_ptr + v_offset) : 0;
            uint8_t u_val = (row_fully_safe || static_cast<long>(u_offset) < u_row_bytes_safe)
                            ? *(u_row_ptr + u_offset) : 0;

            vu_row_dst_ptr[col * 2] = v_val;
            vu_row_dst_ptr[col * 2 + 1] = u_val;
        }
    }

    env->ReleaseByteArrayElements(nv21_output_array, nv21_output_ptr, 0);
    return nv21_output_array;
}