# CppStudio

## 中文

CppStudio 是一款运行在 Android 上的 C/C++ 集成开发环境，目标是在移动设备上完成本地 C/C++ 开发。它集成了代码编辑器、项目文件树、基于 CMakeLists.txt 的项目结构、clangd 智能代码能力，以及基于 proot + Alpine rootfs 的工具链运行环境，可以直接在 Android 设备上编辑、构建和调试 C/C++ 项目。

### 交流

QQ 群：1095682100，欢迎加入交流反馈。

### 功能特性

- 从 GitHub/Gitee/GitLab 克隆 C/C++ 项目（支持 HTTPS/SSH 地址、私有仓库登录、克隆进度与自动导入最近项目）
- 创建 C/C++ 项目，内置多种项目模板（`hello_cpp` / `hello_c` / `cli` / `lib` / `ctest` / `threads` / `sqlite` / `sdl2`），创建即生成 `CMakeLists.txt`、`.clang-format` 与 `.gitignore`
- 基于 CMake 的项目结构与编译数据库：模板默认开启 `CMAKE_EXPORT_COMPILE_COMMANDS`，配置后生成 `build/compile_commands.json`，供 clangd 使用（同时兼容纯 Makefile 项目）
- 基于 proot + Alpine rootfs 的 C/C++ 工具链运行环境（内置 build-base / cmake / pkgconf / clang / clang-extra-tools / gdb / git）
- clangd 语言服务，支持补全、参数提示、符号高亮、诊断、悬浮提示、跳转定义和格式化
- **库管理**：精选 40+ 常见 C/C++ 库（fmt / spdlog / Boost / nlohmann-json / SQLite / SDL2 / Dear ImGui / Eigen 等），一键 apk 安装、自动给出 CMake 接法，并可在 `apk search` 里搜索仓库里的任意包
- 基于 Sora 的代码编辑器，支持标签页、文件树、文件内与全项目搜索、结构大纲和编辑器设置
- Git 面板（提交历史、分支、差异）与多种登录方式：HTTPS Token、HTTPS 账号密码、SSH 私钥、GitHub OAuth 设备码与终端 Git 配置
- 输出面板与编译弹窗显示构建、测试和工具链日志，运行中的任务可随时停止
- 项目配置支持构建类型、编译器（gcc/clang）、C 与 C++ 标准、CMake 参数、并行任务数、运行入口与运行参数
- 深色/浅色主题，强调色为浅紫（lavender），并提供多种配色预设与自定义强调色
- 插件系统：通过 ZIP 导入数据包插件（manifest + 能力目录），支持 AI 技能、工作区小工具等能力
- AI 助手（可选）：配置自己的模型服务后，可在编辑器内对话、执行命令与读写文件

### 项目结构

```text
CppStudio/
├── app/                         Android 应用
├── modules/editor-core/         编辑器模型和共享状态
├── modules/project-file-tree/   项目文件树 UI/组件
├── modules/toolchain-runtime/   proot + Alpine rootfs 运行环境集成
├── modules/clangd-lsp/          clangd 语言服务桥接
├── modules/sora-editor/         Sora 编辑器模块
├── modules/sora-editor-lsp/     Sora LSP 集成
├── modules/sora-language-textmate/
├── modules/sora-oniguruma-native/
├── modules/terminal-emulator/
├── modules/terminal-view/
├── examples/plugins/            插件示例
└── website/                     项目主页（静态页面）
```

### 插件系统

CppStudio 支持数据包插件（不执行代码）。一个插件是一个含 `manifest.json` 的目录：

```text
com.example.cpp-skills/
├── manifest.json    # 必需
├── skills/          # AI 技能（每个子目录一个 SKILL.md）
│   └── cpp-perf/SKILL.md
└── tools/           # 工作区小工具（每个子目录一个 tool.json，可选）
```

`manifest.json`：

```json
{
  "id": "com.example.cpp-skills",
  "name": "C++ 进阶技能示例",
  "version": "1.0.0",
  "description": "描述",
  "author": "作者",
  "min_app_version": 104
}
```

- `id` 必须与目录名一致（点分命名，如 `com.example.xxx`）
- `version` 为语义化版本（x.y.z）
- 支持的能力目录：`skills/`（AI 技能）、`tools/`（工作区小工具）；`templates/`、`themes/` 预留

安装方式二选一：
- **插件市场**：「插件」页 →「浏览」→ 一键安装（索引来自官方仓库 [cppstudio-plugins](https://github.com/by-yitong/cppstudio-plugins)）
- **本地 ZIP**：把目录打成 ZIP（`manifest.json` 可在 ZIP 根或唯一子目录内），在「插件」页点击「安装」导入

插件默认启用，可随时禁用或卸载。本地开发示例见 `examples/plugins/`。

```bash
cd examples/plugins/com.example.cpp-skills
zip -r ../cpp-skills.zip .
```

### 环境要求

- Android Studio 或兼容的 Gradle 构建环境
- JDK 17（或 21）
- Android SDK，compile SDK 36
- NDK `27.2.12479018`（sora-editor 的正则引擎与终端 emulator 需要编译 native 代码）
- SDK CMake `3.22.1`（`sdkmanager "cmake;3.22.1"`）
- Android 8.0+ 设备或模拟器（`minSdk 26`）

当前应用 native 打包目标为 `arm64-v8a`。Gradle 版本由 wrapper 指定（9.2.1）。

### 构建

在仓库根目录创建 `local.properties` 指向本机 SDK（Android Studio 会自动生成）：

```properties
sdk.dir=/path/to/Android/Sdk
```

然后：

```bash
./gradlew assembleDebug        # 产物：app/build/outputs/apk/debug/app-debug.apk
./gradlew installDebug         # 直接装到连接的设备
./gradlew testDebugUnitTest    # 单元测试
```

首次构建会下载 Gradle 发行版与依赖，之后是增量构建。

也可以使用 Android Studio 打开仓库根目录，然后运行 `app` 配置。

### C/C++ 项目流程

CppStudio 通过项目根目录的 `CMakeLists.txt` 识别工程（根目录只有 `Makefile` / `makefile` / `GNUmakefile` 时按 Make 项目管理）；根目录没有构建文件时，会向下找一层子目录里的 `CMakeLists.txt`——克隆下来的仓库经常把工程放在子目录里。

新建项目按所选模板写入 `CMakeLists.txt` 与源码：

- `hello_cpp`：`main.cpp` + CMakeLists，最简 C++17 程序
- `hello_c`：`main.c` + CMakeLists，最简 C11 程序
- `cli`：参数解析 + `--verbose` 的命令行程序
- `lib`：`include/` + `src/` 静态库与示例程序
- `ctest`：`ctest` + 断言，含可测的库代码
- `threads`：`std::thread` + `mutex` 并发示例
- `sqlite`：sqlite3 建表/插入/查询（需先装 sqlite）
- `sdl2`：SDL2 创建窗口与事件循环（需先装 sdl2）

构建目录固定在项目下的 `build/`，配置阶段会顺便导出编译数据库：

```bash
cmake -S . -B build -DCMAKE_BUILD_TYPE=Debug -DCMAKE_EXPORT_COMPILE_COMMANDS=ON
cmake --build build --parallel 4
```

「构建」会先配置再编译（Make 项目直接 `make -jN`），日志流式写进输出面板；「运行」定位构建产物（项目配置里的运行入口 → `build/<项目名>` → 项目根 `<项目名>` → `build/app` → `build/` 下第一个可执行文件），在终端里启动，因此 `scanf` / `std::cin` 这类标准输入也能交互。

「测试」在 CMake 项目上走 `ctest --test-dir build --output-on-failure`，Make 项目走 `make test`。

### clangd

clangd 由 proot + Alpine rootfs 里的 `clang-extra-tools` 提供（`/usr/bin/clangd`，部分版本落在 `/usr/lib/llvmXX/bin`，安装时会补软链）。启动参数固定带 `--compile-commands-dir=<项目>/build`，因此它读的是 CMake 生成的 `compile_commands.json`；同时开 `--background-index` 做跨文件索引，`--pch-storage=memory` 避免在手机上写大量 PCH 文件。

编辑器设置里提供启用开关，以及补全、参数提示、符号高亮、格式化、悬浮提示、跳转定义等分项开关，诊断由语言服务流程统一处理。

没有编译数据库时（还没构建过，或 `CMakeLists.txt` 里删掉了 `CMAKE_EXPORT_COMPILE_COMMANDS ON`），clangd 会退化到内建启发式：补全与跳转仍可用，诊断会明显变少，缺头文件/宏的告警会变多。缺补全时先确认 `build/compile_commands.json` 存在，并重新执行一次配置。

### 库管理

C 和 C++ 没有 `go get` 那样的一条命令，新手常卡在「想用 fmt 却不知道该装什么」。库管理把常用库映射到 Alpine 的 apk 包名：

- **精选库**：40+ 个库，按基础设施 / 数据与序列化 / 网络与并发 / 图形与界面 / 图像与音视频 / 测试与调试 / 数学与算法分类，卡片上直接给出 CMake 接法
- **仓库搜索**：在 rootfs 里执行 `apk search -v <关键字>`，搜到就能装，不受精选目录限制（搜不到时先 `apk update` 同步索引）

安装就是一次 `apk add`（按候选包名依次尝试，Alpine 不同版本包名会变），装在 rootfs 内：头文件进 `/usr/include`、库文件进 `/usr/lib`、可执行文件进 `/usr/bin`。已安装状态直接读 `/lib/apk/db/installed`，版本实时显示。

装完在 `CMakeLists.txt` 里按卡片提示接入，两种写法最常见：

```cmake
find_package(fmt CONFIG REQUIRED)
target_link_libraries(你的目标 PRIVATE fmt::fmt)
```

```cmake
find_package(PkgConfig REQUIRED)
pkg_check_modules(SQLITE REQUIRED sqlite3)
target_include_directories(你的目标 PRIVATE ${SQLITE_INCLUDE_DIRS})
target_link_libraries(你的目标 PRIVATE ${SQLITE_LIBRARIES})
```

卸载在同一个卡片上点「卸载」，执行 `apk del`（该库的所有候选包一起删，避免残留）。

### 说明

- 已有项目会保留自己的 `CMakeLists.txt`；模板变更只影响新建项目。
- 工具链命令会通过应用内的 proot + Alpine rootfs 运行环境在设备上执行（gcc / clang / cmake / clangd 等均运行在该 rootfs 内）。
- 停止构建时会停止 Android 进程，并对活动的 cmake / make / clangd 进程做兜底清理。
- 新建项目统一放在应用内部存储（proot 已绑定到 guest 内同名路径），外部存储的 FUSE 路径 proot 无法可靠绑定。

### 致谢

本项目参考了 [XCode](https://github.com/xiaochenzaine/XCode)（一款 Android 上的 C/C++ IDE），在其编辑器、proot 运行时与项目结构的基础上做了重构；Android 应用骨架（Kotlin + Compose + Sora 编辑器 + proot Alpine rootfs）来自 [GoStudio](https://github.com/by-yitong/GoStudio)，语言层已整体换为 C/C++（CMake、clangd、库管理）。

### 许可证

本项目基于 MIT License 开源，详见 [LICENSE](LICENSE)。

## English

CppStudio is an Android IDE focused on local C/C++ development on mobile devices. It combines a code editor, project file tree, CMakeLists.txt-based project structure, clangd language features, and a proot + Alpine rootfs toolchain runtime, so C/C++ projects can be edited, built, and debugged directly on Android.

### Community

QQ Group: 1095682100 — join for discussion and feedback.

### Features

- Clone C/C++ projects from GitHub/Gitee/GitLab (HTTPS/SSH URLs, private-repository authentication, clone progress, and automatic recent-project import)
- C/C++ project creation with built-in templates (`hello_cpp` / `hello_c` / `cli` / `lib` / `ctest` / `threads` / `sqlite` / `sdl2`); each new project gets a `CMakeLists.txt`, a `.clang-format`, and a `.gitignore`
- CMake-based project structure with a compilation database: templates enable `CMAKE_EXPORT_COMPILE_COMMANDS`, so a configure run produces `build/compile_commands.json` for clangd (plain Makefile projects are supported too)
- proot + Alpine rootfs based C/C++ toolchain runtime (build-base / cmake / pkgconf / clang / clang-extra-tools / gdb / git bundled)
- clangd language server support for completion, signature help, symbol highlight, diagnostics, hover, go-to-definition, and formatting
- **Library manager**: 40+ curated C/C++ libraries (fmt / spdlog / Boost / nlohmann-json / SQLite / SDL2 / Dear ImGui / Eigen and more) with one-tap apk install, the matching CMake snippet, plus `apk search` over the whole Alpine repository
- Sora-based code editor with tabs, file tree, in-file and project-wide search, structure outline, and editor settings
- Git panel (history, branches, diff) with HTTPS token, HTTPS credentials, SSH key, GitHub OAuth device flow, and terminal Git configuration
- Output panel and compile dialog for build/test/toolchain logs; running tasks can be stopped at any time
- Project configuration for build type, compiler (gcc/clang), C and C++ standard, CMake arguments, parallel jobs, run entry, and run arguments
- Dark/light themes with a lavender accent, several color presets, and a custom accent color
- Plugin system: install data-pack plugins from ZIP (manifest + capability dirs) — AI skills, workspace tools
- Optional AI assistant: bring your own model endpoint to chat, run commands, and edit files from the editor

### Project Structure

```text
CppStudio/
├── app/                         Android application
├── modules/editor-core/         Editor models and shared editor state
├── modules/project-file-tree/   Project tree UI/components
├── modules/toolchain-runtime/   proot + Alpine rootfs command/runtime integration
├── modules/clangd-lsp/          clangd language server bridge
├── modules/sora-editor/         Sora editor module
├── modules/sora-editor-lsp/     Sora LSP integration
├── modules/sora-language-textmate/
├── modules/sora-oniguruma-native/
├── modules/terminal-emulator/
├── modules/terminal-view/
├── examples/plugins/            Example plugins
└── website/                     Project landing page (static)
```

### Plugins

CppStudio plugins are data packs, not code. A plugin is a directory with a `manifest.json`:

```text
com.example.cpp-skills/
├── manifest.json    # required
├── skills/          # AI skills (one SKILL.md per subdirectory)
│   └── cpp-perf/SKILL.md
└── tools/           # workspace tools (one tool.json per subdirectory, optional)
```

- `id` must match the directory name (dotted form, e.g. `com.example.xxx`)
- `version` is a semantic version (x.y.z)
- Supported capability directories: `skills/` (AI skills), `tools/` (workspace tools); `templates/` and `themes/` are reserved

Install either from the plugin marketplace (index served by [cppstudio-plugins](https://github.com/by-yitong/cppstudio-plugins)) or by zipping the directory and importing it from the Plugins page. See `examples/plugins/` for a working example.

### Requirements

- Android Studio or compatible Gradle environment
- JDK 17
- Android SDK with compile SDK 36
- Android device or emulator running Android 8.0+ (`minSdk 26`)

The app currently targets `arm64-v8a` native packaging.

### Build

Clone the repository and build with Gradle:

```bash
./gradlew assembleDebug
```

For Android Studio, open the repository root and run the `app` configuration.

### C/C++ Project Workflow

CppStudio recognizes a project by the `CMakeLists.txt` in its root (a root with only `Makefile` / `makefile` / `GNUmakefile` is treated as a Make project). If the root has no build file, it looks one level down for a `CMakeLists.txt` — cloned repositories often keep the real project in a subdirectory.

New projects are generated from the selected template:

- `hello_cpp`: `main.cpp` + CMakeLists, minimal C++17 program
- `hello_c`: `main.c` + CMakeLists, minimal C11 program
- `cli`: argument parsing plus a `--verbose` flag
- `lib`: a static library in `include/` + `src/` with a demo program
- `ctest`: `ctest` + asserts around testable library code
- `threads`: `std::thread` + `mutex` example
- `sqlite`: sqlite3 create/insert/query (install sqlite first)
- `sdl2`: SDL2 window and event loop (install sdl2 first)

The build directory is always `build/`, and the configure step also exports the compilation database:

```bash
cmake -S . -B build -DCMAKE_BUILD_TYPE=Debug -DCMAKE_EXPORT_COMPILE_COMMANDS=ON
cmake --build build --parallel 4
```

Build configures and then compiles (Make projects just run `make -jN`), streaming logs into the output panel. Run locates the produced binary (configured run entry → `build/<project>` → `<root>/<project>` → `build/app` → first executable in `build/`) and starts it in a terminal, so `scanf` / `std::cin` still work interactively. Tests use `ctest --test-dir build --output-on-failure` for CMake projects and `make test` for Make projects.

### clangd

clangd comes from the `clang-extra-tools` package inside the proot + Alpine rootfs (`/usr/bin/clangd`; on some Alpine versions it lives under `/usr/lib/llvmXX/bin` and the installer adds a symlink). It is started with `--compile-commands-dir=<project>/build`, so it reads the `compile_commands.json` produced by CMake, plus `--background-index` for cross-file indexing and `--pch-storage=memory` to avoid writing PCH files on a phone.

Editor settings expose a master switch and per-feature switches for completion, signature help, symbol highlight, formatting, hover, and go-to-definition. Diagnostics are handled as part of the language server flow.

Without a compilation database (nothing built yet, or `CMAKE_EXPORT_COMPILE_COMMANDS ON` removed from `CMakeLists.txt`) clangd falls back to its built-in heuristics: completion and navigation still work, diagnostics get noticeably sparser but include/macro warnings increase. If completion is empty, check that `build/compile_commands.json` exists and re-run configure.

### Library Manager

C and C++ have no `go get`, and newcomers often get stuck at "I want fmt but don't know what to install". The library manager maps common libraries onto Alpine apk packages:

- **Curated list**: 40+ libraries grouped into infrastructure / data & serialization / networking & concurrency / graphics & UI / image & audio-video / testing & debugging / math & algorithms, each card showing the CMake snippet
- **Repository search**: runs `apk search -v <keyword>` inside the rootfs so any package can be installed, not just the curated ones (run `apk update` first if a search comes back empty)

Installing is an `apk add` (candidate package names are tried in order, since Alpine renames packages between releases). Packages land inside the rootfs: headers in `/usr/include`, libraries in `/usr/lib`, executables in `/usr/bin`. Installed state is read straight from `/lib/apk/db/installed`, so versions show up immediately.

Then wire it into `CMakeLists.txt` as the card suggests — the two common shapes:

```cmake
find_package(fmt CONFIG REQUIRED)
target_link_libraries(your_target PRIVATE fmt::fmt)
```

```cmake
find_package(PkgConfig REQUIRED)
pkg_check_modules(SQLITE REQUIRED sqlite3)
target_include_directories(your_target PRIVATE ${SQLITE_INCLUDE_DIRS})
target_link_libraries(your_target PRIVATE ${SQLITE_LIBRARIES})
```

Uninstall runs `apk del` for every candidate package of that library, so nothing is left behind.

### Notes

- Existing projects keep their own `CMakeLists.txt`; template changes only affect newly created projects.
- Toolchain commands run through the app's proot + Alpine rootfs runtime on device (gcc / clang / cmake / clangd all run inside this rootfs).
- Build cancellation stops the Android process and uses fallback cleanup for active cmake / make / clangd processes.
- New projects live in app-internal storage (bound into the guest at the same path); proot cannot reliably bind external-storage FUSE paths.

### Acknowledgements

This project references [XCode](https://github.com/xiaochenzaine/XCode) (an Android C/C++ IDE) and builds on its editor, proot runtime, and project structure. The Android app skeleton (Kotlin + Compose + Sora editor + proot Alpine rootfs) comes from [GoStudio](https://github.com/by-yitong/GoStudio); the language layer has been replaced with C/C++ (CMake, clangd, library manager).

### License

This project is licensed under the MIT License. See [LICENSE](LICENSE) for details.
