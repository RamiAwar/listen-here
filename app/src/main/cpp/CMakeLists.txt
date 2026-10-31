cmake_minimum_required(VERSION 3.22)
project(whistle_jni C)

add_library(needle STATIC IMPORTED)
set_target_properties(needle PROPERTIES
    IMPORTED_LOCATION ${CMAKE_CURRENT_SOURCE_DIR}/needle/${ANDROID_ABI}/libneedle.a)

add_library(whistle_jni SHARED whistle_jni.c)
target_include_directories(whistle_jni PRIVATE needle)
# The engine is C++ inside; pull in the static C++ runtime so we ship one .so.
target_link_libraries(whistle_jni needle c++_static c++abi m)
target_link_options(whistle_jni PRIVATE -Wl,--gc-sections -Wl,--exclude-libs,ALL)
