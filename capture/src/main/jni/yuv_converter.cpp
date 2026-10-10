#include <jni.h>
#include <cstring> // For memcpy

// Include NEON header only when compiling for ARM
#if defined(__ARM_NEON__)
#include <arm_neon.h>
#endif

extern "C" JNIEXPORT jbyteArray JNICALL
Java_app_gov_uidai_capture_utils_nativelib_YuvConverter_yuv420ToNv21Native( // <-- IMPORTANT: Change this
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

    // Get direct pointers to the pixel data
    auto y_src = static_cast<const uint8_t *>(env->GetDirectBufferAddress(y_buffer));
    auto u_src = static_cast<const uint8_t *>(env->GetDirectBufferAddress(u_buffer));
    auto v_src = static_cast<const uint8_t *>(env->GetDirectBufferAddress(v_buffer));

    // Create the output Java byte array
    jbyteArray nv21_output_array = env->NewByteArray(width * height * 3 / 2);
    auto nv21_output_ptr = env->GetByteArrayElements(nv21_output_array, nullptr);
    auto nv21_dst = reinterpret_cast<uint8_t *>(nv21_output_ptr);

    // 1. Copy Y Plane (fast path for continuous memory)
    uint8_t *y_dst = nv21_dst;
    if (y_row_stride == width) {
        memcpy(y_dst, y_src, width * height);
    } else {
        for (int i = 0; i < height; ++i) {
            memcpy(y_dst + i * width, y_src + i * y_row_stride, width);
        }
    }

    // 2. Interleave U and V Planes (NV21 format is VUVUVU...)
    uint8_t *vu_dst = nv21_dst + width * height;
    int uv_width = width / 2;
    int uv_height = height / 2;

    for (int row = 0; row < uv_height; ++row) {
        // Get pointers to the start of the current row for U and V
        const uint8_t *v_row_ptr = v_src + (row * v_row_stride);
        const uint8_t *u_row_ptr = u_src + (row * u_row_stride);
        // Get pointer to the start of the current row in the destination
        uint8_t *vu_row_dst_ptr = vu_dst + (row * width);

        int col = 0;

#if defined(__ARM_NEON__)
        // NEON-optimized path for interleaving 16 pixels (8 U and 8 V) at a time
        for (; col <= uv_width - 8; col += 8) {
            // If pixel stride is 1 (UUUUUUUU), we can do a direct load.
            if (u_pixel_stride == 1 && v_pixel_stride == 1) {
                // Load 8 U values and 8 V values into NEON registers
                uint8x8_t u_vec = vld1_u8(u_row_ptr + col);
                uint8x8_t v_vec = vld1_u8(v_row_ptr + col);

                // Create a 2-element structure of vectors to store V and U
                uint8x8x2_t vu_vec;
                vu_vec.val[0] = v_vec; // V plane first
                vu_vec.val[1] = u_vec; // U plane second

                // Store the interleaved VU data (VUVUVU...) to the destination
                vst2_u8(vu_row_dst_ptr + (col * 2), vu_vec);
            } else {
                // Slower NEON path for pixel strides > 1 (e.g., U.U.U.U...)
                // We de-interleave the source into temporary registers first.
                uint8x8x2_t u_deinterleaved = vld2_u8(u_row_ptr + col * u_pixel_stride);
                uint8x8x2_t v_deinterleaved = vld2_u8(v_row_ptr + col * v_pixel_stride);

                uint8x8x2_t vu_to_store;
                vu_to_store.val[0] = v_deinterleaved.val[0]; // V
                vu_to_store.val[1] = u_deinterleaved.val[0]; // U

                vst2_u8(vu_row_dst_ptr + (col * 2), vu_to_store);
            }
        }
#endif
        // Scalar fallback loop for remaining pixels or non-NEON builds
        for (; col < uv_width; ++col) {
            vu_row_dst_ptr[col * 2] = *(v_row_ptr + col * v_pixel_stride);
            vu_row_dst_ptr[col * 2 + 1] = *(u_row_ptr + col * u_pixel_stride);
        }
    }

    env->ReleaseByteArrayElements(nv21_output_array, nv21_output_ptr, 0);
    return nv21_output_array;
}