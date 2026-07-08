cmake_minimum_required(VERSION 3.22.1)

project("image_native_libs")


# --- YUV Converter Library ---
add_library(
        yuv_converter
        SHARED
        yuv_converter.cpp
)

# --- Bitmap Rotator Library ---
add_library(
        bitmap_rotator
        SHARED
        bitmap_rotator.cpp
)

# Enable NEON instructions for ARM architectures
if (ANDROID_ABI STREQUAL "armeabi-v7a")
    target_compile_options(yuv_converter PRIVATE -mfpu=neon -O3 -D__ARM_NEON__)
elseif (ANDROID_ABI STREQUAL "arm64-v8a")
    target_compile_options(yuv_converter PRIVATE -O3 -D__ARM_NEON__)
endif ()

# --- Find required Android libraries ---
find_library(
        log-lib
        log
)

find_library(
        jnigraphics-lib
        jnigraphics
)

# --- Link libraries ---
target_link_libraries(
        yuv_converter
        ${log-lib}
)

target_link_libraries(
        bitmap_rotator
        ${log-lib}
        ${jnigraphics-lib}
)