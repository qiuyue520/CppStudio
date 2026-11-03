# CppStudio

在 Android 上写 C/C++ 的本地 IDE：sora 编辑器 + CMake + clangd + proot/Alpine 工具链，全部跑在设备本地，
不需要连电脑。个人二次开发版本，基于 [GoStudio](https://github.com/by-yitong/GoStudio) 的 Android 应用骨架，
语言层整体换成了 C/C++。

- 包名：`com.xiaonai.cppstudio`（debug 版为 `com.xiaonai.cppstudio.debug`）
- 平台：Android 8.0+（`minSdk 26`），native 仅 `arm64-v8a`
- 许可：MIT（见 [LICENSE](LICENSE)）

> English version: [below](#english)

## 功能

### 项目与构建

- **从 Git 克隆**：GitHub / Gitee / GitLab，支持 HTTPS 地址、SSH 地址、私有仓库认证；克隆有进度与日志，
  克隆完自动加入「最近项目」。
- **新建项目**：内置 8 个模板，创建时直接生成 `CMakeLists.txt`、`.clang-format`、`.gitignore`：

  | 模板 | 内容 |
  |---|---|
  | `hello_cpp` | `main.cpp` + CMakeLists，最简 C++17 |
  | `hello_c` | `main.c` + CMakeLists，最简 C11 |
  | `cli` | 参数解析 + `--verbose` 的命令行程序 |
  | `lib` | `include/` + `src/` 静态库 + 调用示例 |
  | `ctest` | 可测的库代码 + `ctest` 断言用例 |
  | `threads` | `std::thread` + `mutex` 并发示例 |
  | `sqlite` | sqlite3 建表 / 插入 / 查询（需先装 sqlite） |
  | `sdl2` | SDL2 窗口与事件循环（需先装 sdl2） |

- **项目识别**：优先看根目录 `CMakeLists.txt`；只有 `Makefile` / `makefile` / `GNUmakefile` 时按 Make 项目管理；
  根目录没有构建文件会向下找一层子目录里的 `CMakeLists.txt`（克隆来的仓库常把工程放在子目录）。
- **构建**：CMake 项目先 `cmake -S . -B build -DCMAKE_BUILD_TYPE=… -DCMAKE_EXPORT_COMPILE_COMMANDS=ON`
  再 `cmake --build build --parallel N`；Make 项目直接 `make -jN`。日志流式写入输出面板，可随时停止。
- **运行**：定位产物（项目配置的运行入口 → `build/<项目名>` → 项目根 `<项目名>` → `build/app` → `build/` 下第一个可执行文件），
  在内置终端里启动，`scanf` / `std::cin` 这类标准输入可以交互。
- **测试**：CMake 项目走 `ctest --test-dir build --output-on-failure`，Make 项目走 `make test`。
- **编译数据库**：CMake 项目首次打开（或 `build/compile_commands.json` 不存在）会自动配置一次，供 clangd 使用。

### 编辑器

- 基于 sora-editor：标签页、文件树、行号、撤销/重做、块尾提示、快捷符号栏。
- TextMate 语法高亮：C、C++（含 raw string / 预处理指令）、CMake、JSON、dotenv。
- 文件内搜索替换 + 全项目搜索（结果显示文件与行号，点击跳转）。
- 代码结构大纲：优先用 clangd 的 documentSymbol，未连接时退化为本地正则解析（函数 / 类 / 结构体 / 枚举 / 宏 / 命名空间）。
- 新建文件按模板写初始内容（C/C++ 源文件、头文件、CMakeLists、Makefile、.gitignore）。
- 格式化按钮调用 rootfs 里的 `clang-format -i`，读取项目自己的 `.clang-format`。

### clangd 语言服务

- 由 Alpine 的 `clang-extra-tools` 提供（`/usr/bin/clangd`，部分版本在 `/usr/lib/llvmXX/bin`，安装时补软链）。
- 启动参数：`--compile-commands-dir=<项目>/build`、`--background-index`、`--pch-storage=memory`、
  `--header-insertion=never`、`--function-arg-placeholders=1`、`--completion-style=detailed`、`--limit-results=100`。
- 能力：补全（含参数名占位符）、签名提示、符号高亮、诊断（问题页签 + 行内标记）、悬浮提示、跳转定义、代码操作（quickfix）。
- 没有编译数据库时退化为内建启发式：补全与跳转仍可用，诊断明显变少、头文件/宏告警变多。
- 编辑器设置里有总开关与分项开关（补全 / 签名 / 高亮 / 悬浮 / 跳转 / 文档翻译）。

### 库管理

C/C++ 没有 `go get`，所以内置了一个按 Alpine apk 包名组织的库目录：

- **精选库**：50 个常用库，分 8 类（基础设施 / 数据与序列化 / 网络与并发 / 图形与界面 / 图像与音视频 / 测试与调试 / 数学与算法），
  每张卡片给出可直接粘贴的 CMake 接法，例如：

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

- **仓库搜索**：在 rootfs 里跑 `apk search -v <关键字>`，搜到就能装，不受精选目录限制（搜不到先点「同步索引」）。
- **安装 / 卸载**：一次 `apk add`（按候选包名依次尝试，Alpine 不同版本包名会变）；卸载用 `apk del`，候选包一起删。
- **状态**：直接读 rootfs 的 `/lib/apk/db/installed`，版本实时显示，支持「只看已安装」筛选。

### 工具链

全部装在 proot + Alpine rootfs 里，从「开发工具」页安装/重装，支持一键装全套或单独装：

| 组件 | apk 包 | 作用 |
|---|---|---|
| 编译器 | `build-base` | gcc / g++ / make / musl 头文件 / binutils |
| 构建系统 | `cmake` + `pkgconf` | CMake 与 `pkg_check_modules` |
| 语言服务 | `clang` + `clang-extra-tools` | clangd、clang-format |
| 调试器 | `gdb` | 命令行调试（在终端里用） |
| 版本控制 | `git` + `ca-certificates` | 克隆、提交、分支 |

安装前会测速国内 apk 镜像并自动切换到最快的源（也可在「设置 → 软件源镜像」手动选）。

### 其它

- **Git 面板**：提交历史、分支、差异；登录方式支持 HTTPS Token、HTTPS 账号密码、SSH 私钥、GitHub OAuth 设备码。
- **终端**：内置终端（terminal-emulator），可直接跑 `cmake`、`make`、`gdb`、`git`。
- **编译产物导出**：项目配置页「编译」完成后把可执行文件导出到公共 `Download/cppstudio`（Android 10+ 走 MediaStore，免权限）。
- **学习页**：C/C++ 入门、CMake/IDE 使用、实战三组课程，习题在设备内用 `g++` 编译运行判题。
- **主题**：深色 / 浅色 / 跟随系统，默认浅紫（lavender，`#A78BFA` / `#7C5CE0`），另有 4 套预设与自定义强调色。
- **插件**：ZIP 数据包插件（`manifest.json` + `skills/`、`tools/`），不执行代码；应用内置一个「JSON → C++17 结构体」工具。
- **AI 助手**（可选）：自带模型服务地址与 key，可在编辑器里对话、读写文件、执行命令。

## 项目结构

```text
CppStudio/
├── app/                         Android 应用（Compose UI、编辑器界面、AI、插件、学习页）
├── modules/editor-core/         编辑器模型、设置、共享状态
├── modules/project-file-tree/   项目文件树 UI/组件
├── modules/toolchain-runtime/   proot + Alpine rootfs、apk 镜像、包数据库
├── modules/clangd-lsp/          clangd 语言服务桥接
├── modules/sora-editor/         Sora 编辑器
├── modules/sora-editor-lsp/     Sora LSP 集成
├── modules/sora-language-textmate/  TextMate 语法
├── modules/sora-oniguruma-native/   Oniguruma 正则引擎（native）
├── modules/terminal-emulator/   终端模拟器（native）
├── modules/terminal-view/       终端视图
├── backend/                     上游自带的可选后端（崩溃上报 / 翻译，本应用默认未启用）
├── examples/plugins/            插件示例
└── website/                     项目主页（静态页面）
```

## 环境要求

- JDK 17 或 21
- Android SDK：compile SDK 36
- NDK `27.2.12479018`（正则引擎与终端模拟器要编 native）
- SDK CMake `3.22.1`（`sdkmanager "cmake;3.22.1"`）
- Gradle 由 wrapper 指定（9.2.1）

## 构建

仓库根目录的 `local.properties` 指向本机 SDK（Android Studio 会自动生成）：

```properties
sdk.dir=/path/to/Android/Sdk
```

```bash
./gradlew assembleDebug        # app/build/outputs/apk/debug/app-debug.apk
./gradlew installDebug         # 装到已连接设备
./gradlew testDebugUnitTest    # 单元测试
./gradlew assembleRelease      # 需要签名配置，见下
```

首次构建会下载 Gradle 与依赖，之后是增量构建。

## 签名与发布

Release 签名从 `local.properties` 读取（缺失则回退 debug 签名，产物同样可安装）：

```properties
CPPSTUDIO_KEYSTORE_PATH=/path/to/cppstudio-release.jks
CPPSTUDIO_KEY_ALIAS=cppstudio
CPPSTUDIO_KEY_PASSWORD=你的密码
CPPSTUDIO_STORE_PASSWORD=你的密码
```

生成密钥（只做一次，务必备份 keystore 与密码，丢了就无法覆盖升级）：

```bash
keytool -genkeypair -v -keystore cppstudio-release.jks -alias cppstudio \
  -keyalg RSA -keysize 4096 -validity 10000 -storetype PKCS12 \
  -storepass 你的密码 -keypass 你的密码 -dname "CN=CppStudio, O=xiaonai, C=CN"
```

推送 `v*` tag 会触发 [`.github/workflows/release.yml`](.github/workflows/release.yml)：
在 `ubuntu-latest` 上装 NDK 27.2 + SDK CMake 3.22.1 → `assembleRelease`（R8 压缩）→
创建 GitHub Release 并上传 `CppStudio-v<版本>.apk`。

```bash
git tag -a v0.1.3 -m "CppStudio v0.1.3" && git push origin v0.1.3
```

CI 签名用仓库 Secrets（未配置则回退 debug 签名）：

| Secret | 说明 |
|---|---|
| `CPPSTUDIO_KEYSTORE_BASE64` | keystore 的 base64（`base64 -w0 cppstudio-release.jks`） |
| `CPPSTUDIO_KEYSTORE_ALIAS` | key 别名 |
| `CPPSTUDIO_KEY_PASSWORD` | key 密码 |
| `CPPSTUDIO_STORE_PASSWORD` | keystore 密码 |

另有可选的 `WEBHOOK_URL`（自定义构建通知，未配置则跳过）。

当前发布包签名证书指纹（SHA-256，可用 `apksigner verify --print-certs` 核对）：

```text
4a68efa040a523c2edede5447b4fce23793b6e5f705105eb0f29f081fd5d0dd9
```

## 应用标识与外部服务

包名 `com.xiaonai.cppstudio`。所有可选的远端能力集中在
[`modules/editor-core/.../config/cppstudio_backend_config.kt`](modules/editor-core/src/main/kotlin/com/xiaonai/cppstudio/editor/config/cppstudio_backend_config.kt)：

```kotlin
object cppstudio_backend_config {
    const val GITHUB_REPO = "qiuyue520/CppStudio"  // 应用内更新 + 关于页链接
    const val PLUGIN_MARKET_INDEX_URL = ""          // 留空 = 插件市场「浏览」提示未配置
    const val API_BASE_URL = ""                     // 留空 = 不上报崩溃、不做文档翻译
}
```

`PLUGIN_MARKET_INDEX_URL` / `API_BASE_URL` 留空时，对应功能直接关闭并给出提示，应用不会向第三方发送数据。

## 已知限制

- native 只打包 `arm64-v8a`，32 位设备与 x86 模拟器装不上。
- 工具链与库都装在 rootfs 里，首次安装需要联网；rootfs 与项目都在应用内部存储，卸载应用会一并删除。
- CMake 项目的「运行」按可执行文件定位，纯库项目只能构建、没有可运行产物。
- 文档翻译与崩溃上报需要自建后端（见上），默认关闭。

## 致谢

- [GoStudio](https://github.com/by-yitong/GoStudio)（by-yitong）：本项目的 Android 应用骨架、Compose 界面、
  插件系统、Git 面板、学习页与 proot 运行时都来自它。非常感谢原作者把这样一套完整的移动端 IDE 骨架开源。
- [XCode](https://github.com/xiaochenzaine/XCode)（xiaochenzaine）：GoStudio 的编辑器、proot 运行时与项目结构
  又源自这个 Android C/C++ IDE，本项目的 CMake / clangd 方向实际上是回到了它最初的目标。
- [sora-editor](https://github.com/Rosemoe/sora-editor)（Rosemoe）：代码编辑器与 LSP 客户端。
- [Alpine Linux](https://alpinelinux.org/) 与 [proot](https://proot-me.github.io/)：设备内的 Linux 运行环境。
- clangd / clang-format（LLVM）、CMake：语言服务与构建。

### 本版本相对上游做了什么

语言层从 Go 换成 C/C++：项目模型改成 `CMakeLists.txt`（兼容 Makefile）、构建走 CMake、语言服务换成 clangd、
工具链换成 build-base/cmake/clang/gdb、新增库管理与 C/C++ 项目模板、课程与 AI 提示词改写、配色改为浅紫、
包名改为 `com.xiaonai.cppstudio`，并移除了上游的 AndLua 式 App 运行时与 APK 打包/布局设计器。

## 许可证

MIT License，详见 [LICENSE](LICENSE)。原始版权声明保留（`Copyright (c) 2026 xiaochenzaine`），
本仓库的修改同样以 MIT 协议发布。

---

## English

CppStudio is a local C/C++ IDE for Android: sora editor + CMake + clangd on top of a proot/Alpine toolchain,
everything runs on the device. It is a personal fork of the Android app skeleton from
[GoStudio](https://github.com/by-yitong/GoStudio) with the language layer replaced by C/C++.

- Package: `com.xiaonai.cppstudio` (debug builds: `com.xiaonai.cppstudio.debug`)
- Platform: Android 8.0+ (`minSdk 26`), `arm64-v8a` only
- License: MIT

### Features

- **Clone from Git** (GitHub / Gitee / GitLab, HTTPS + SSH, private-repo auth, progress log, recent projects)
- **New project** from 8 templates: `hello_cpp`, `hello_c`, `cli`, `lib`, `ctest`, `threads`, `sqlite`, `sdl2`.
  Each project gets a `CMakeLists.txt`, a `.clang-format` and a `.gitignore`.
- **Project detection**: `CMakeLists.txt` first, plain `Makefile` next, plus a one-level-deep fallback for cloned repos.
- **Build**: `cmake -S . -B build -DCMAKE_BUILD_TYPE=… -DCMAKE_EXPORT_COMPILE_COMMANDS=ON` then
  `cmake --build build --parallel N` (or `make -jN`); logs stream into the output panel and can be cancelled.
- **Run**: locates the binary (configured entry → `build/<name>` → `<root>/<name>` → `build/app` → first executable)
  and launches it in the built-in terminal, so `scanf` / `std::cin` work.
- **Test**: `ctest --test-dir build --output-on-failure`, or `make test` for Make projects.
- **Editor**: sora-editor with tabs, file tree, in-file and project-wide search, structure outline
  (clangd documentSymbol with a regex fallback), TextMate grammars for C / C++ / CMake / JSON / dotenv, and
  formatting through `clang-format -i` using the project's `.clang-format`.
- **clangd**: completion (with parameter placeholders), signature help, symbol highlight, diagnostics, hover,
  go-to-definition, quick fixes. Reads `build/compile_commands.json`; falls back to heuristics without one.
- **Library manager**: 50 curated libraries across 8 categories, one-tap `apk add` with the matching CMake snippet,
  `apk search` over the whole Alpine repository, uninstall via `apk del`, installed versions read from
  `/lib/apk/db/installed`, plus an "installed only" filter.
- **Toolchain**: `build-base`, `cmake` + `pkgconf`, `clang` + `clang-extra-tools` (clangd / clang-format),
  `gdb`, `git` — installed inside the rootfs, with automatic mirror speed-testing.
- **Git panel** (history, branches, diff) with HTTPS token, HTTPS credentials, SSH key and GitHub OAuth device flow.
- **Terminal**, compile dialog with binary export to `Download/cppstudio`, project configuration
  (build type, compiler, C/C++ standard, CMake/make args, parallel jobs, run entry/args).
- **Themes**: dark / light / system with a lavender accent (`#A78BFA` / `#7C5CE0`), 4 more presets and a custom accent.
- **Plugins**: ZIP data packs (`manifest.json` + `skills/`, `tools/`), no code execution; a "JSON → C++17 structs"
  tool ships built in.
- **Optional AI assistant** with your own model endpoint.

### Requirements

JDK 17/21, Android SDK 36, NDK `27.2.12479018`, SDK CMake `3.22.1`, Gradle 9.2.1 (wrapper).

### Build

```properties
# local.properties
sdk.dir=/path/to/Android/Sdk
```

```bash
./gradlew assembleDebug
./gradlew testDebugUnitTest
./gradlew assembleRelease   # add the four CPPSTUDIO_* signing keys to local.properties
```

Pushing a `v*` tag builds a signed release APK on GitHub Actions and publishes it as a GitHub Release;
the signing material comes from the `CPPSTUDIO_KEYSTORE_BASE64`, `CPPSTUDIO_KEYSTORE_ALIAS`,
`CPPSTUDIO_KEY_PASSWORD` and `CPPSTUDIO_STORE_PASSWORD` secrets. Without them the release build falls back
to debug signing (still installable).

Release certificate SHA-256 fingerprint:

```text
4a68efa040a523c2edede5447b4fce23793b6e5f705105eb0f29f081fd5d0dd9
```

### Optional remote services

All optional endpoints live in `cppstudio_backend_config.kt`. `GITHUB_REPO` powers the in-app updater and the
About-page links; `PLUGIN_MARKET_INDEX_URL` and `API_BASE_URL` (crash reports / doc translation) are empty by
default, which disables those features instead of contacting any third party.

### Limitations

`arm64-v8a` only; the toolchain needs network on first install; rootfs and projects live in app-internal storage
(uninstalling the app deletes them); library-only CMake projects have no runnable binary.

### Acknowledgements

- [GoStudio](https://github.com/by-yitong/GoStudio) by by-yitong — the Android app skeleton this project is built on.
- [XCode](https://github.com/xiaochenzaine/XCode) by xiaochenzaine — the editor, proot runtime and project layout
  that GoStudio itself derives from.
- [sora-editor](https://github.com/Rosemoe/sora-editor), [Alpine Linux](https://alpinelinux.org/),
  [proot](https://proot-me.github.io/), LLVM clangd, CMake.

### License

MIT. Original copyright notice (`Copyright (c) 2026 xiaochenzaine`) is preserved; modifications in this
repository are released under the same license.
