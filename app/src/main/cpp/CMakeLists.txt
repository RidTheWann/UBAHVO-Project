cmake_minimum_required(VERSION 3.22.1)

project(ubahvo_native_core LANGUAGES C CXX)

if(NOT DEFINED UBAHVO_RUST_LIBRARY_ROOT OR UBAHVO_RUST_LIBRARY_ROOT STREQUAL "")
    message(FATAL_ERROR "UBAHVO_RUST_LIBRARY_ROOT must point to the generated Rust static libraries.")
endif()

set(UBAHVO_RUST_ARCHIVE "${UBAHVO_RUST_LIBRARY_ROOT}/${ANDROID_ABI}/libubahvo_native_core.a")
if(NOT EXISTS "${UBAHVO_RUST_ARCHIVE}")
    message(FATAL_ERROR "Missing Rust native core for ${ANDROID_ABI}: ${UBAHVO_RUST_ARCHIVE}")
endif()

add_library(ubahvo_rust_core STATIC IMPORTED GLOBAL)
set_target_properties(ubahvo_rust_core PROPERTIES IMPORTED_LOCATION "${UBAHVO_RUST_ARCHIVE}")

add_library(ubahvo_native SHARED native_url_core.cpp)
target_compile_features(ubahvo_native PRIVATE cxx_std_17)
target_compile_options(ubahvo_native PRIVATE -Wall -Wextra -Werror)
# Align this bridge for 16 KB kernels; this does not repair upstream FFmpeg payload libraries.
target_link_options(ubahvo_native PRIVATE "-Wl,-z,max-page-size=16384")
target_link_libraries(ubahvo_native PRIVATE ubahvo_rust_core android log dl m)
