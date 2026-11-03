---
name: cpp-cmake
description: C/C++ 构建与依赖管理（CMake）。当用户需要新建/修改 CMakeLists.txt、引入第三方库（find_package / pkg_check_modules）、拆分多目录、切换构建类型或生成 compile_commands.json 时使用。
---

# C/C++ 构建技能（CMake）

你是 CMake 专家，目标平台是 Android 上的 Alpine rootfs（musl + gcc/g++ + cmake + pkg-config）。

## 常用命令

```bash
cmake -B build -DCMAKE_BUILD_TYPE=Debug            # 配置（首次/改了 CMakeLists 后都要跑）
cmake --build build -j$(nproc)                     # 构建
./build/app                                        # 运行
cmake --build build --target clean                 # 清理
cmake -B build -DCMAKE_BUILD_TYPE=Release          # 发布构建
cmake -B build -DCMAKE_EXPORT_COMPILE_COMMANDS=ON  # 给 clangd 生成 compile_commands.json
cmake --install build                              # 安装（需要 install() 规则）
```

## CMakeLists 基本结构

```cmake
cmake_minimum_required(VERSION 3.16)
project(myapp LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)          # 20 也可以；C 项目用 set(CMAKE_C_STANDARD 11)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)       # 不用 gnu++ 扩展

if(NOT CMAKE_BUILD_TYPE)
    set(CMAKE_BUILD_TYPE Debug)     # 手机上默认 Debug，崩溃好定位
endif()
set(CMAKE_EXPORT_COMPILE_COMMANDS ON)

add_executable(app
    src/main.cpp
    src/util.cpp
)
target_include_directories(app PRIVATE include)
target_compile_options(app PRIVATE -Wall -Wextra)
```

要点：

- `add_executable` / `add_library` 里必须列出**所有**源文件（CMake 不会自动扫描目录）；文件多了用 `file(GLOB_RECURSE SOURCES CONFIGURE_DEPENDS src/*.cpp)`，但新增文件后记得重新 configure
- 头文件目录只用 `target_include_directories(... PRIVATE include)` 声明，不要用全局 `include_directories()`
- 加宏用 `target_compile_definitions(app PRIVATE DEBUG=1)`，不要写 `-DDEBUG`
- 静态库：`add_library(mylib STATIC src/a.cpp)`，再 `target_link_libraries(app PRIVATE mylib)`

## 引入第三方库

优先 CONFIG 模式（库自带 `xxxConfig.cmake`）：

```cmake
find_package(fmt CONFIG REQUIRED)
target_link_libraries(app PRIVATE fmt::fmt)

find_package(spdlog CONFIG REQUIRED)
target_link_libraries(app PRIVATE spdlog::spdlog)

find_package(Boost REQUIRED COMPONENTS filesystem)   # Boost 常见问题见下方
target_link_libraries(app PRIVATE Boost::filesystem)
```

pkg-config 模式（SDL2、ssl 等系统库最常用）：

```cmake
find_package(PkgConfig REQUIRED)
pkg_check_modules(SDL2 REQUIRED sdl2)
target_include_directories(app PRIVATE ${SDL2_INCLUDE_DIRS})
target_link_libraries(app PRIVATE ${SDL2_LIBRARIES})
target_compile_options(app PRIVATE ${SDL2_CFLAGS_OTHER})
```

命令行验证库是否存在：

```bash
pkg-config --list-all | grep -i sdl
pkg-config --cflags --libs sdl2
```

找不到库时的排查顺序：

1. 库没装 → 用应用里的「库管理」一键装 fmt / spdlog / boost / sdl2，或在 rootfs 里 `apk add <lib>-dev`
2. 装到了非标准前缀 → 配置时加 `-DCMAKE_PREFIX_PATH=/usr/local`（多个用分号分隔）
3. `find_package(X)` 报 `Could not find a package configuration file provided by "X"` → 该库只有 pkg-config 文件，用 `pkg_check_modules` 那套写法
4. 编译过了但链接报 `undefined reference` → 库没 `target_link_libraries`，或库出现在了目标之前（CMake 会自己排序 target，但手写 `${SDL2_LIBRARIES}` 时要放在目标后面）

## 多目录 / 子模块

```cmake
# 根 CMakeLists.txt
add_subdirectory(src)
add_subdirectory(third_party/mylib)

# src/CMakeLists.txt
add_library(core STATIC core.cpp)
target_include_directories(core PUBLIC ${CMAKE_CURRENT_SOURCE_DIR}/../include)
add_executable(app main.cpp)
target_link_libraries(app PRIVATE core)
```

`PUBLIC` 的 include 目录会传递给链接它的目标，`PRIVATE` 只作用于自己——头文件对外暴露用 `PUBLIC`。

## 构建类型

| 类型 | 含义 | 常用场景 |
| --- | --- | --- |
| `Debug` | `-g` 无优化 | 开发和调试（gdb 必须有 -g） |
| `Release` | `-O3 -DNDEBUG` | 发布 |
| `RelWithDebInfo` | `-O2 -g` | 想带优化又要能看栈 |
| `MinSizeRel` | `-Os` | 体积优先 |

自定义优化/告警不要覆盖 CMAKE_CXX_FLAGS，用 `target_compile_options`。

## compile_commands.json（clangd / 编辑器诊断）

```bash
cmake -B build -DCMAKE_EXPORT_COMPILE_COMMANDS=ON
ln -sf build/compile_commands.json compile_commands.json   # 项目根放一份软链
```

- 新增源文件、改 include 目录后重新配置一次
- 没有这个文件时，编辑器里大量「找不到头文件」是误报，不要按真错误改代码
- 需要给 clangd 额外参数时在项目根写 `.clangd`：

```yaml
CompileFlags:
  Add: [-std=c++17, -Iinclude]
```

## 常见坑

1. 改了 CMakeLists 但没重新 `cmake -B build`，直接 `cmake --build build` → 新加的源文件不参与编译
2. 在源码树里直接 `cmake .` 生成一堆文件 → 统一用 `-B build`，构建目录可在 `.gitignore` 里忽略
3. `target_link_libraries` 写成 `link_libraries`（全局污染、顺序不可控）
4. 用 `file(GLOB ...)` 但不带 `CONFIGURE_DEPENDS`，新增文件不生效
5. 中文字符串编译报错：源文件用 UTF-8，构建时设 `LANG=C.UTF-8`（bash 工具环境里已带）
