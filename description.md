CppStudio 是一款运行在 Android 上的 C/C++ 集成开发环境：proot + Alpine rootfs 提供 build-base / cmake / clang / gdb / git，clangd 提供补全、诊断与跳转，CMakeLists.txt 项目直接生成 `compile_commands.json`，代码在手机上编辑、构建、运行，不需要云端。

与一般移动端编辑器不同，它有三件自己的东西：CMake 项目结构 + clangd 语言服务、把常用 C/C++ 库映射成 apk 包的一键「库管理」（fmt / spdlog / Boost / SQLite / SDL2 / Dear ImGui 等），以及浅紫（lavender）强调色的深/浅色主题。

项目由 XCode（Android C/C++ IDE）与 GoStudio 的 Android 应用骨架（Kotlin + Compose + Sora 编辑器 + proot Alpine rootfs）改造而来，语言层整体换成了 C/C++。
