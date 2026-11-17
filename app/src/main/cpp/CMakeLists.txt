cmake_minimum_required(VERSION 3.22)
project(girassol-vulkan LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Flags de otimização para ARM64
if(ANDROID_ABI STREQUAL "arm64-v8a")
    set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -O3 -march=armv8.2-a+dotprod+i8mm+fp16")
endif()

# Encontrar bibliotecas nativas
find_library(vulkan-lib vulkan)
find_library(log-lib log)
find_library(android-lib android)

# Biblioteca nativa Vulkan (renderização)
add_library(girassol-vulkan SHARED
    JniBridge.cpp
    VulkanRenderer.cpp
)

target_link_libraries(girassol-vulkan
    ${vulkan-lib}
    ${log-lib}
    ${android-lib}
)

target_include_directories(girassol-vulkan PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}
)

# Biblioteca nativa LLM (inferência)
add_library(girassol-llm SHARED
    LlmEngine.cpp
)

target_link_libraries(girassol-llm
    ${log-lib}
    ${android-lib}
)

target_include_directories(girassol-llm PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}
)
