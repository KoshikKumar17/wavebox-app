cmake_minimum_required(VERSION 3.18.1)
project(wavebox_lame C)

set(LAME ${CMAKE_CURRENT_SOURCE_DIR}/lame)

# libmp3lame only (no mpglib / decoder, no frontend, no asm)
set(LAME_SRC
    ${LAME}/libmp3lame/VbrTag.c
    ${LAME}/libmp3lame/bitstream.c
    ${LAME}/libmp3lame/encoder.c
    ${LAME}/libmp3lame/fft.c
    ${LAME}/libmp3lame/gain_analysis.c
    ${LAME}/libmp3lame/id3tag.c
    ${LAME}/libmp3lame/lame.c
    ${LAME}/libmp3lame/newmdct.c
    ${LAME}/libmp3lame/presets.c
    ${LAME}/libmp3lame/psymodel.c
    ${LAME}/libmp3lame/quantize.c
    ${LAME}/libmp3lame/quantize_pvt.c
    ${LAME}/libmp3lame/reservoir.c
    ${LAME}/libmp3lame/set_get.c
    ${LAME}/libmp3lame/tables.c
    ${LAME}/libmp3lame/takehiro.c
    ${LAME}/libmp3lame/util.c
    ${LAME}/libmp3lame/vbrquantize.c
    ${LAME}/libmp3lame/version.c
)

add_library(wavelame SHARED lame_jni.c ${LAME_SRC})

target_include_directories(wavelame PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}      # our config.h
    ${LAME}/include
    ${LAME}/libmp3lame
)

target_compile_definitions(wavelame PRIVATE
    HAVE_CONFIG_H
    ieee754_float32_t=float
)

target_compile_options(wavelame PRIVATE -O3 -fno-strict-aliasing -w)

# 16 KB page alignment (required for Android 15+ / Play), plus a smaller .so
target_link_options(wavelame PRIVATE
    -Wl,-z,max-page-size=16384
    -Wl,--gc-sections
)

find_library(log-lib log)
target_link_libraries(wavelame ${log-lib} m)
