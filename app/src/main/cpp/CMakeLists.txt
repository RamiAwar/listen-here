cmake_minimum_required(VERSION 3.22)
project(listenhere C CXX)

# Reproducible builds: keep the checkout path out of the binary (ggml asserts embed __FILE__),
# so a build in any folder (yours, F-Droid's) produces identical bytes.
get_filename_component(REPO_ROOT "${CMAKE_CURRENT_SOURCE_DIR}/../../../.." ABSOLUTE)
string(APPEND CMAKE_C_FLAGS " -ffile-prefix-map=${REPO_ROOT}=.")
string(APPEND CMAKE_CXX_FLAGS " -ffile-prefix-map=${REPO_ROOT}=.")

# The GitHub build also bundles Cactus Whistle, a prebuilt engine F-Droid can't ship.
option(WITH_WHISTLE "Link the prebuilt Cactus Whistle engine" OFF)

# whisper.cpp, built from source (third_party/whisper.cpp, pinned submodule).
set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_SERVER OFF CACHE BOOL "" FORCE)
set(GGML_OPENMP OFF CACHE BOOL "" FORCE)  # plain pthreads; no libomp to ship
set(GGML_NATIVE OFF CACHE BOOL "" FORCE)  # cross-compiling: target a known baseline
# ARMv8.2 with fp16 and dot product: every arm64 phone since ~2018 (Cortex-A55/A75 onward).
set(GGML_CPU_ARM_ARCH "armv8.2-a+fp16+dotprod" CACHE STRING "" FORCE)
add_subdirectory(${CMAKE_CURRENT_SOURCE_DIR}/../../../../third_party/whisper.cpp whisper EXCLUDE_FROM_ALL)

set(SOURCES whisper_jni.c)
if (WITH_WHISTLE)
    list(APPEND SOURCES whistle_jni.c)
endif ()

add_library(listenhere_jni SHARED ${SOURCES})
target_link_libraries(listenhere_jni whisper m)
target_compile_options(listenhere_jni PRIVATE -O3)
target_link_options(listenhere_jni PRIVATE -Wl,--gc-sections -Wl,--exclude-libs,ALL)

if (WITH_WHISTLE)
    add_library(needle STATIC IMPORTED)
    set_target_properties(needle PROPERTIES
        IMPORTED_LOCATION ${CMAKE_CURRENT_SOURCE_DIR}/needle/${ANDROID_ABI}/libneedle.a)
    target_include_directories(listenhere_jni PRIVATE needle)
    target_link_libraries(listenhere_jni needle)
endif ()
