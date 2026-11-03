package com.jmwl.cppstudio.learn

/**
 * CppStudio 使用指南轨道（平台相关，只做概念说明）。
 *
 * 讲的是「这个 App 本身怎么用」：装环境、认工具链、装库、导出产物、排查报错。
 *
 * 说明：lesson id 沿用移植前的字符串（`cppstudio-app-*`），因为学习进度按
 * lesson id 存在 SharedPreferences 里；改 id 会让老用户的完成记录和断点失效。
 * 内容已按 C/C++ 重写，id 与主题的对应关系见每个 learn_lesson 上方的注释。
 */

private fun guide_text(md: String) = learn_block.text(md.trimIndent())
private fun guide_code(src: String) = learn_block.code(normalize_lesson_code(src))
private fun guide_tip(text: String) = learn_block.callout("tip", text)
private fun guide_note(text: String) = learn_block.callout("note", text)
private fun guide_warn(text: String) = learn_block.callout("warn", text)

internal fun cppstudio_learn_tracks(): List<learn_track> = listOf(
    learn_track(
        id = "cppstudio-guide",
        title = "CppStudio 使用指南",
        subtitle = "环境安装、工具链、库管理、产物导出与常见错误排查",
        accent_color = 0xFF5CCFE6L,
        category = "CppStudio 使用",
        lessons = listOf(
            // id 原来是「App 项目结构」，现在讲安装与首次启动。
            learn_lesson(
                id = "cppstudio-app-basic",
                title = "安装与环境准备",
                summary = "第一次打开 App 要装哪些东西。",
                est_minutes = 5,
                steps = listOf(
                    learn_step.concept(
                        "cppstudio-app-basic-c",
                        "三步开机",
                        listOf(
                            guide_text("第一次启动会先进**「Alpine 基础环境安装」**页：下载并解压内置的 Alpine rootfs（一个迷你 Linux 环境，几十 MB），装完自动进主界面。rootfs 是 CppStudio 自己的一份根文件系统，之后所有编译、终端、Git 操作都在它里面跑。"),
                            guide_text("进主界面后，底部只有三个页签：**项目**（项目列表 + 新建/导入/克隆）、**学习**（就是你正在看的这些课）、**设置**（主题、编辑器、AI、Git 等）。环境相关的入口不在这三个页签里，而是首页的**开发工具**卡片。"),
                            guide_text("**开发工具**页是所有环境的入口：一张一键安装卡 + 六个单项卡。"),
                            guide_text(
                                """
                                - **C/C++ 工具链**：一键装好下面五项，最省事；
                                - **编译器（build-base）**：gcc / g++ / make / musl 头文件 + binutils，没有它什么都不能编译；
                                - **CMake**：CMake + pkgconf，负责配置与构建；
                                - **clangd**：语言服务器，补全、诊断、跳转都靠它；
                                - **clang-format**：按 `.clang-format` 格式化代码；
                                - **gdb**：命令行调试器；**git**：版本控制。
                                """.trimIndent()
                            ),
                            guide_note("这些组件都装在 App 私有目录里的 rootfs 中，不会改动手机系统，也不需要 root。代价是它们**只在这个 App 内可见**：手机自带的终端里没有 gcc。"),
                            guide_tip("第一次装 build-base 要下载几十 MB，建议连 Wi-Fi；装好之后断开网络也能编译。卸载 App 会把 rootfs 里的工具链和库一起删掉。")
                        )
                    )
                )
            ),
            // id 原来是「组件与事件」，现在讲工具链与编译器。
            learn_lesson(
                id = "cppstudio-app-events",
                title = "工具链与编译器",
                summary = "gcc/g++/CMake 各自负责什么，产物是什么架构。",
                est_minutes = 6,
                steps = listOf(
                    learn_step.concept(
                        "cppstudio-app-events-c",
                        "一次编译到底发生了什么",
                        listOf(
                            guide_text("你点「编译」时，本质上是这两条命令在 proot 里执行（这也是你可以在终端里手敲的东西）："),
                            guide_code(
                                """
                                # 1. 配置：读 CMakeLists.txt，生成构建文件与 compile_commands.json
                                cmake -B build -DCMAKE_BUILD_TYPE=Debug

                                # 2. 构建：真正调用 g++ 编译、链接
                                cmake --build build -j4
                                """
                            ),
                            guide_text("分工是这样的：**CMake** 只负责「怎么构建」，**g++** 才负责把 `.cpp` 翻成机器码，**链接器**再把目标文件和库拼成可执行文件。所以报错要分清是谁在报：`CMake Error` 是构建脚本的问题，`error: ...` 是编译错误，`undefined reference` 是链接错误。"),
                            guide_note("C++ 标准不是在命令行上传的，而是写在 `CMakeLists.txt` 里的 `set(CMAKE_CXX_STANDARD 17)`。想换标准就改这一行（或者用项目配置页的 C++ 标准项），改完要重新配置。"),
                            guide_text("工具链组件与它们对应的命令："),
                            guide_code(
                                """
                                gcc --version        # C 编译器（build-base）
                                g++ --version        # C++ 编译器（build-base）
                                cmake --version      # 构建系统
                                pkg-config --libs fmt  # 查第三方库的编译参数（pkgconf）
                                clangd --version     # 语言服务器
                                clang-format --version
                                gdb --version
                                git --version
                                """
                            ),
                            guide_warn("产物是 **Android ARM64 + Alpine musl** 的可执行文件：拷到电脑（x86 + glibc）上一定跑不起来，这不是代码的问题。要跨平台分发，得在对应平台上重新编译。"),
                            guide_tip("`build/CMakeCache.txt` 会记住上次的配置（构建类型、标准、参数）。改了配置却「不生效」时，删掉 `build/` 重新配置最省时间。")
                        )
                    )
                )
            ),
            // id 原来是「App 生命周期」，现在讲库管理。
            learn_lesson(
                id = "cppstudio-app-lifecycle",
                title = "库管理",
                summary = "第三方库一键安装与 CMake 接法。",
                est_minutes = 6,
                steps = listOf(
                    learn_step.concept(
                        "cppstudio-app-lifecycle-c",
                        "从「想用」到「能用」",
                        listOf(
                            guide_text("C/C++ 没有 `npm install`，第三方库要靠系统包管理器装头文件和库文件。CppStudio 把这件事做成了**库管理**页：**精选库**给常用库配好了安装包与 CMake 接法，**仓库搜索**则直接查 Alpine 仓库。"),
                            guide_text("装一个库的标准流程：**库管理** → 找到卡片 → **一键安装** → 回项目改 `CMakeLists.txt` → 重新构建。以 fmt 为例："),
                            guide_code(
                                """
                                # CMakeLists.txt
                                find_package(fmt CONFIG REQUIRED)
                                target_link_libraries(你的目标 PRIVATE fmt::fmt)
                                """
                            ),
                            guide_code(
                                """
                                // main.cpp
                                #include <fmt/core.h>

                                int main() {
                                    fmt::print("Hello, {}!\n", "fmt");
                                    return 0;
                                }
                                """
                            ),
                            guide_text("两种「找库」方式要分清：用 `pkg-config` 的库（SDL2、sqlite3 等 C 库）走 `pkg_check_modules`；提供了 CMake 配置文件的库（fmt、spdlog、nlohmann_json 等）走 `find_package(... CONFIG REQUIRED)`。库管理卡片里的「在 CMakeLists.txt 里使用」写的就是要抄的那两行。"),
                            guide_warn("装的是 `-dev` 开发包（头文件 + 库），不是给运行时用的精简包。装完必须让 CMake **重新配置**（重新构建一次），否则它还用缓存里的「找不到包」结论。"),
                            guide_tip("`boost` 约 300MB，只要头文件就装 `boost-dev`；换 Alpine 版本后包名可能变（`catch2-dev` / `catch2-3-dev`），卡片会按候选顺序逐个尝试，实在装不上就去**仓库搜索**用 `apk search` 的包名装。")
                        )
                    )
                )
            ),
            // id 原来是「原生系统 API」，现在讲编译产物与导出。
            learn_lesson(
                id = "cppstudio-app-native",
                title = "编译产物与导出",
                summary = "产物在哪、怎么导出、怎么分享。",
                est_minutes = 5,
                steps = listOf(
                    learn_step.concept(
                        "cppstudio-app-native-c",
                        "build 目录里有什么",
                        listOf(
                            guide_text("构建成功后，可执行文件在项目的 **`build/`** 目录下（这个名字由 CMakeLists.txt / 构建配置决定）。旁边还有几个值得知道的东西："),
                            guide_code(
                                """
                                build/
                                ├── 你的程序名        # 可执行文件（ARM64，musl）
                                ├── CMakeCache.txt    # 上次的配置缓存
                                ├── compile_commands.json  # clangd 用的编译数据库
                                └── CMakeFiles/       # 中间目标文件
                                """
                            ),
                            guide_text("编译成功后 CppStudio 会把可执行文件**导出到公共 Download 目录**，构建输出里会打印实际路径；也可以用**导出项目**（回到项目列表长按项目 → 导出项目）把整个工程打成 ZIP，换设备继续开发。"),
                            guide_text("分享给别人的两种思路：一是**给源码 + CMakeLists.txt**，让他在自己平台编译；二是**给静态链接的可执行文件**，但要求对方也是同架构、同 libc（Android ARM64 + musl 基本只在这类环境里能跑）。"),
                            guide_warn("`build/` 是生成物，不该进 Git。新建项目时 CppStudio 写的 `.gitignore` 已经把 `/build/` 排除了，别手动把它加进去。"),
                            guide_tip("想看产物细节：终端里 `file build/你的程序` 会显示架构与链接方式，`ldd build/你的程序`（musl 下可能没有）或 `ls -l` 看依赖与大小。")
                        )
                    )
                )
            ),
            // id 原来是「对话框与视图控制」，现在讲常见错误排查。
            learn_lesson(
                id = "cppstudio-app-dialog",
                title = "常见错误排查",
                summary = "undefined reference、头文件找不到、cd 到 build。",
                est_minutes = 8,
                steps = listOf(
                    learn_step.concept(
                        "cppstudio-app-dialog-c",
                        "三类错误，三种原因",
                        listOf(
                            guide_text("C/C++ 的报错看着吓人，其实可以按**阶段**分成三类：配置期（CMake）、编译期（g++）、链接期（ld）。先判断阶段，再对症下药。"),
                            guide_text("**① 编译期：找不到头文件**"),
                            guide_code(
                                """
                                fatal error: fmt/core.h: No such file or directory
                                fatal error: SDL2/SDL.h: No such file or directory
                                """
                            ),
                            guide_text("原因是「头文件真不在搜索路径里」。三种情况：库**没装**（去库管理装 `-dev` 包）；装了但 `CMakeLists.txt` **没写 include 路径**（`target_include_directories` / `pkg_check_modules` 的 `${'$'}{XXX_INCLUDE_DIRS}`）；头文件路径写错（SDL2 是 `<SDL2/SDL.h>`，不是 `<SDL.h>`）。"),
                            guide_text("**② 链接期：undefined reference**"),
                            guide_code(
                                """
                                undefined reference to `mathlib::add(int, int)'
                                undefined reference to `main'
                                """
                            ),
                            guide_text("这表示「声明看见了，定义没找到」。常见原因：只写了头文件、对应的 `.cpp` **没加进构建**（不在 `add_executable` 的源文件列表里）；`.cpp` 在但**没链接库**（`target_link_libraries` 缺一行）；库的顺序不对（依赖方要写在被依赖方前面）；C 和 C++ 混编没加 `extern \"C\"`。"),
                            guide_text("**③ 运行期：cd 到 build**"),
                            guide_code(
                                """
                                ./hello: No such file or directory
                                sh: hello: not found
                                Permission denied
                                """
                            ),
                            guide_text("Linux 不会在当前目录找命令，所以必须写 `./hello`。而 `./hello` 找不到文件，多半是**工作目录不对**：产物在 `build/` 里，要么 `cd build && ./hello`，要么在项目根目录直接 `./build/hello`。真在 `build/` 里又 `cd build` 一次，就会得到 `No such file or directory`。"),
                            guide_text("`Permission denied` 则是丢了可执行权限：`chmod +x ./hello`。"),
                            guide_note("排查顺序永远是：**从第一条错误开始修**。第一条 undefined reference 或找不到头文件，往往会让后面几十条错误一起消失。"),
                            guide_tip("实在看不懂就把**第一条错误**整段丢给 AI 助手，问「这条错误是什么意思、怎么改」；把修完的结果再编译一次，比一次性分析 80 条错误有效得多。")
                        )
                    )
                )
            )
        )
    )
)
