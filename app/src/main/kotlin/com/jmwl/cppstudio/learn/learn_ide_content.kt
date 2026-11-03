package com.jmwl.cppstudio.learn

/**
 * IDE 使用轨道：教新用户把 CppStudio 这个 C/C++ IDE 用起来
 * （建项目 → CMakeLists → 编辑器 → clangd → 格式化 → 构建运行 → 终端 → Git → 库管理 → AI）。
 *
 * 只做概念说明，不配置交互练习：这些操作都需要真实项目环境。
 */

private fun ide_text(md: String) = learn_block.text(md.trimIndent())
private fun ide_code(src: String) = learn_block.code(normalize_lesson_code(src))
private fun ide_tip(text: String) = learn_block.callout("tip", text)
private fun ide_note(text: String) = learn_block.callout("note", text)
private fun ide_warn(text: String) = learn_block.callout("warn", text)

internal fun ide_learn_tracks(): List<learn_track> = listOf(
    learn_track(
        id = "cppstudio-ide",
        title = "IDE 使用",
        subtitle = "从新建项目到构建运行，把编辑器本身用熟",
        accent_color = 0xFF9C6ADEL,
        category = "IDE 使用",
        lessons = listOf(
            learn_lesson(
                id = "cppstudio-ide-projects",
                title = "新建与打开项目",
                summary = "选模板建第一个项目，或把已有工程导进来。",
                est_minutes = 5,
                steps = listOf(
                    learn_step.concept(
                        "cppstudio-ide-projects-c",
                        "三种起点",
                        listOf(
                            ide_text("首页有三个入口：**新建项目**、**导入项目**、**克隆 Git 项目**。"),
                            ide_text(
                                """
                                新建项目只需三步：填**项目名**（只能字母、数字、下划线，且不能以数字开头）→ 选模板 → 点「创建项目」。
                                模板会生成 `CMakeLists.txt`、`main.cpp`（或 `main.c`）以及配套的构建配置，常见的有：

                                - **Hello World**：最简 `main.cpp`，编译并打印一句话；
                                - **C 语言**：`main.c` + C 标准，用 `printf`；
                                - **CLI 工具**：读取 `argc/argv` 的命令行程序；
                                - **静态库**：`include/` + `src/` 分层，演示库与可执行文件分离；
                                - **多线程**：`std::thread` + `std::mutex`；
                                - **CMake 单测**：`add_library` + `add_test`，配好 CTest；
                                - **SQLite / SDL2**：需要额外装库，`CMakeLists.txt` 里已经写好 `pkg_check_modules`。
                                """.trimIndent()
                            ),
                            ide_text("已有工程？用**导入项目**：可以选项目目录，也可以选整个 ZIP 压缩包（CppStudio 会解压并自动识别根目录）。工程里有 `CMakeLists.txt` 或 `Makefile` 就能被识别。"),
                            ide_tip("不确定选哪个就选 Hello World：先跑通一次，再回来看 `CMakeLists.txt` 里到底写了什么。"),
                            ide_note("项目统一存放在 App 内部存储（proot 里对应 `/home/cppstudio/projects/<项目名>`），这样 cmake 和 clangd 才能稳定访问；不要指望它出现在手机公共目录里。")
                        )
                    )
                )
            ),
            learn_lesson(
                id = "cppstudio-ide-cmake",
                title = "CMakeLists.txt 是什么",
                summary = "读懂模板生成的那几行构建脚本。",
                est_minutes = 7,
                steps = listOf(
                    learn_step.concept(
                        "cppstudio-ide-cmake-c",
                        "构建脚本，不是代码",
                        listOf(
                            ide_text("`CMakeLists.txt` 是**给 CMake 看的项目说明书**：它告诉 CMake 用哪个 C++ 标准、要编译哪些源文件、生成叫什么名字的可执行文件或库。CppStudio 新建项目时生成的大致是这样："),
                            ide_code(
                                """
                                cmake_minimum_required(VERSION 3.20)
                                project(hello LANGUAGES CXX)

                                set(CMAKE_CXX_STANDARD 17)             # 用 C++17
                                set(CMAKE_CXX_STANDARD_REQUIRED ON)    # 编译器不支持就报错，不降级
                                set(CMAKE_EXPORT_COMPILE_COMMANDS ON)  # 生成 compile_commands.json，clangd 靠它

                                file(GLOB SOURCES ${'$'}{CMAKE_CURRENT_SOURCE_DIR}/src/*.cpp)
                                add_executable(hello ${'$'}{SOURCES})
                                """
                            ),
                            ide_text("逐行看：`project(hello)` 定项目名；`CMAKE_CXX_STANDARD` 定标准；`file(GLOB ...)` 收集 `src/` 下所有 `.cpp`；`add_executable(hello ...)` 说「生成一个叫 hello 的可执行文件」。所以往 `src/` 里加新文件**不用改脚本**。"),
                            ide_text("用到第三方库时再加两行链接。比如 fmt："),
                            ide_code(
                                """
                                find_package(fmt CONFIG REQUIRED)
                                target_link_libraries(hello PRIVATE fmt::fmt)
                                """
                            ),
                            ide_text("分成库和程序时用 `add_library` + `target_include_directories`，单测再用 `enable_testing()` + `add_test()`。"),
                            ide_warn("`CMAKE_EXPORT_COMPILE_COMMANDS ON` 这行别删：clangd 的补全、跳转、诊断全靠生成的 `compile_commands.json`。"),
                            ide_tip("改了 CMakeLists.txt 之后要**重新配置**（Acknowledge/构建一次），否则新加的文件不会参与编译。`${'$'}{CMAKE_CURRENT_SOURCE_DIR}` 这类写法是 CMake 变量，`${'$'}` 后面跟花括号才有效。")
                        )
                    )
                )
            ),
            learn_lesson(
                id = "cppstudio-ide-editor",
                title = "编辑器与符号栏",
                summary = "侧边栏、顶部工具栏和「结构」面板。",
                est_minutes = 5,
                steps = listOf(
                    learn_step.concept(
                        "cppstudio-ide-editor-c",
                        "界面上的几块区域",
                        listOf(
                            ide_text("打开项目后就进了编辑器。左侧竖排是**侧边栏**，从上到下：**文件**（文件树）、**搜索**（当前文件 / 整个项目）、**结构**（当前文件的符号列表）、**日志**、**终端**。"),
                            ide_text("顶部工具栏是常用动作：**撤销**、**重做**、**搜索**、**保存**、**格式化**、**展开/折叠参数**。底部面板有三个页签：**构建输出**、**IDE日志**、**问题**。"),
                            ide_text("**「结构」面板就是符号栏**：它列出当前文件里的类、函数、宏，输入框可以按名字过滤（`过滤符号...`），点一下直接跳到对应位置。文件很长时，这比滚轮快得多。"),
                            ide_text("**没有物理键盘也能高效操作**：选中一段代码，会浮出**注释 / 问 AI / 详情 / 跳转定义**四个动作；想改名字或看函数签名，先选中再点「详情」，比翻源码快。"),
                            ide_note("CppStudio 的编辑器没有 VS Code 那种组合键，所有动作都在工具栏、侧边栏和「选中文本」的浮出按钮上；长按文件树里的文件还能看到重命名、删除等操作。"),
                            ide_tip("编辑器和终端左右/上下分屏的位置可以在设置里调整；小屏手机建议把底部面板收起再写代码。")
                        )
                    )
                )
            ),
            learn_lesson(
                id = "cppstudio-ide-clangd",
                title = "补全、诊断与跳转（clangd）",
                summary = "让编辑器真正读懂 C++。",
                est_minutes = 7,
                steps = listOf(
                    learn_step.concept(
                        "cppstudio-ide-clangd-c",
                        "clangd 在背后做什么",
                        listOf(
                            ide_text("C++ 的补全不能靠关键字猜，必须知道每个头文件里有什么、`std::vector` 的成员有哪些、这个变量是什么类型。CppStudio 用 **clangd** 做这件事：它读 `compile_commands.json`（由 CMake 生成），因此能给出**语义级**的补全和诊断。"),
                            ide_text("用起来就三点："),
                            ide_text(
                                """
                                - **补全**：输入 `vec.` 会列出 `size`、`push_back` 等真实成员；输入类名后 `::` 会列出静态成员；
                                - **诊断**：写错的类型、漏掉的分号、找不到的头文件会立刻标红，底部「问题」页签列出全部条目，点条目跳到出错行；
                                - **跳转**：点函数/类型可以跳到定义处，也能查「谁调用了它」。
                                """.trimIndent()
                            ),
                            ide_text("前置条件是装好 clangd：主界面**开发工具**页点「clangd」卡片（装的是 `clang` 与 `clang-extra-tools`，clangd 和 clang-format 都在里面）。"),
                            ide_warn("如果补全一直是空的，先确认两点：项目里存在 `build/compile_commands.json`（CMakeLists.txt 里要有 `CMAKE_EXPORT_COMPILE_COMMANDS ON`），以及你构建过一次项目。两者缺一，clangd 就退化成纯文本编辑器。"),
                            ide_tip("改完 CMakeLists.txt 或换了 C++ 标准，重新构建一次，让 `compile_commands.json` 跟上，否则诊断会用旧参数。")
                        )
                    )
                )
            ),
            learn_lesson(
                id = "cppstudio-ide-format",
                title = "格式化",
                summary = "clang-format 一键整理缩进与换行。",
                est_minutes = 4,
                steps = listOf(
                    learn_step.concept(
                        "cppstudio-ide-format-c",
                        "别手动对齐",
                        listOf(
                            ide_text("工具栏上的**格式化**按钮调用 `clang-format`，按项目根目录的 `.clang-format` 规则整理当前文件：缩进、空格、换行、`#include` 排序都交给它。"),
                            ide_text("新建项目时 CppStudio 会自动写一份 `.clang-format`（基于 Google 风格、4 空格缩进、行宽 100），可以按团队习惯修改："),
                            ide_code(
                                """
                                BasedOnStyle: Google
                                IndentWidth: 4
                                ColumnLimit: 100
                                AccessModifierOffset: -4
                                AllowShortFunctionsOnASingleLine: Inline
                                SortIncludes: true
                                """
                            ),
                            ide_text("需要 `clang-format` 可执行文件：**开发工具**页安装 `clang-extra-tools`。没装时按钮会提示安装。"),
                            ide_tip("提交代码前格式化一次，diff 里就不会混进「只改了空格」的噪音，代码评审只看真实逻辑。")
                        )
                    )
                )
            ),
            learn_lesson(
                id = "cppstudio-ide-build",
                title = "构建与运行",
                summary = "配置 → 编译 → 看输出 → 跑起来。",
                est_minutes = 7,
                steps = listOf(
                    learn_step.concept(
                        "cppstudio-ide-build-c",
                        "一次完整的构建",
                        listOf(
                            ide_text("构建分两步：CMake **配置**（生成构建文件）和编译（`cmake --build`）。CppStudio 在项目配置页把这两步合到**编译**按钮里，日志实时写进底部**构建输出**面板。"),
                            ide_text("项目配置页可以调的关键项：**构建类型**（Debug / Release / RelWithDebInfo / MinSizeRel）、**编译器**（gcc / clang）、**C++ 标准**（c++11 到 c++23）、**C 标准**、**CMake 参数**、**make 参数**、**并行任务数**、**运行入口**与**运行参数**。"),
                            ide_text("点了编译之后："),
                            ide_text(
                                """
                                - 成功：`构建输出` 里能看到 `[100%] Built target ...`，可执行文件在项目的 `build/` 目录下；
                                - 失败：错误会同时进**问题**页签（带行号，点一下跳过去），先修第一个错误——后面的往往是被它带出来的；
                                - 产物：编译成功后 CppStudio 会把可执行文件导出到公共 Download 目录，日志里会打印实际路径。
                                """.trimIndent()
                            ),
                            ide_note("手机上的可执行文件是 **Android/ARM 架构、链接的是 Alpine musl**，拷到电脑上跑不了；要跨平台分发得在对应平台重新编译。"),
                            ide_tip("「运行」需要可执行文件能自己在设备上跑通；如果需要终端参数，在项目配置的**运行参数**里填，别写死在代码里。")
                        )
                    )
                )
            ),
            learn_lesson(
                id = "cppstudio-ide-terminal",
                title = "终端",
                summary = "在 proot 里手动 cmake、跑程序和看文件。",
                est_minutes = 6,
                steps = listOf(
                    learn_step.concept(
                        "cppstudio-ide-terminal-c",
                        "一个真的 Alpine shell",
                        listOf(
                            ide_text("侧边栏点**终端**进入一个真正的 shell（proot 里的 Alpine Linux），工作目录默认就是当前项目目录。可以点「新建标签」开多个会话，互不干扰。"),
                            ide_text("最常用的几条命令："),
                            ide_code(
                                """
                                pwd                      # 我在哪（项目目录）
                                ls                       # 看文件
                                cd build && ls           # 看构建产物
                                cmake -B build -DCMAKE_BUILD_TYPE=Debug
                                cmake --build build -j4
                                ./build/hello            # 直接跑刚编出来的程序
                                """
                            ),
                            ide_text("编译报错、找不到头文件、链接失败时，**到终端里手敲一遍命令**是最快的定位方式：界面上的参数都对应一条你能看见的命令。"),
                            ide_warn("`./hello` 提示 `not found` 不一定是文件不存在——路径不对、或者没有可执行权限时会这样。先 `ls -l` 确认，再 `./hello` 带上 `./`（Linux 不会去当前目录找命令）。"),
                            ide_tip("终端和编辑器共享同一个文件系统：终端里 `touch` 出来的文件，回到**文件**面板刷新一下就能看到。")
                        )
                    )
                )
            ),
            learn_lesson(
                id = "cppstudio-ide-git",
                title = "Git 面板",
                summary = "初始化、提交、分支、历史与贮藏。",
                est_minutes = 7,
                steps = listOf(
                    learn_step.concept(
                        "cppstudio-ide-git-c",
                        "常用操作都在面板里",
                        listOf(
                            ide_text("打开带 `.git` 的项目，侧边栏/编辑器的 Git 面板会显示工作区状态。新建项目还没进版本库时，面板会提示「当前项目不是 git 仓库」，点**初始化仓库**即可。"),
                            ide_text("日常流程就是四步：看**更改**列表 → 选中要提交的文件（移到**已暂存**）→ 填**提交消息** → 提交。**未跟踪**是新建的文件，先加入暂存区才会进提交。"),
                            ide_text("面板上还能做：**新建分支**、切换分支、查看**提交历史**与逐行 diff、**贮藏**（stash）临时收起未提交的修改。"),
                            ide_text("要克隆远端仓库，回首页点**克隆 Git 项目**填仓库地址；需要账号的仓库在设置里配好凭据，认证信息由 CppStudio 保存。"),
                            ide_tip("提交前先**构建一次**：能编译通过的提交，比「先提交再修」省下无数麻烦。"),
                            ide_note("Git 操作依赖 proot 里的 `git`，没装的话在**开发工具**页安装。")
                        )
                    )
                )
            ),
            learn_lesson(
                id = "cppstudio-ide-lib",
                title = "库管理",
                summary = "fmt、spdlog、boost 一键装，CMake 怎么接。",
                est_minutes = 7,
                steps = listOf(
                    learn_step.concept(
                        "cppstudio-ide-lib-c",
                        "精选库与仓库搜索",
                        listOf(
                            ide_text("C/C++ 没有统一的包管理器，新手最常卡在「我想用 fmt，到底要装什么」。CppStudio 的**库管理**页把这件事包了一层：**精选库**里每个卡片就是常用库 + 它对应的 apk 包 + CMake 接法。"),
                            ide_text("比如 `fmt` 卡片：安装包是 `fmt-dev`（不同 Alpine 版本可能叫 `fmt`），装好后在 `CMakeLists.txt` 里加："),
                            ide_code(
                                """
                                find_package(fmt CONFIG REQUIRED)
                                target_link_libraries(你的目标 PRIVATE fmt::fmt)
                                """
                            ),
                            ide_text("精选库里常见的：**fmt**（格式化）、**spdlog**（日志）、**boost**、**nlohmann/json**、**gtest** / **catch2**（测试）、**sqlite**、**sdl2**、**eigen**、**opencv**……每张卡片展开都有安装包候选、已装版本和 CMake 接法。"),
                            ide_text("精选库里没有的，去**仓库搜索**按包名搜（其实就是在 Alpine 仓库里执行 `apk search`）；搜不到就先点**同步索引**更新一次仓库列表。"),
                            ide_warn("装的是 `-dev` 开发包（头文件 + 库），不是运行时包；装完记得回去**重新构建**，否则 CMake 缓存里还是「找不到包」。"),
                            ide_tip("`boost` 体积约 300MB，只要头文件的话装 `boost-dev` 就够了。")
                        )
                    )
                )
            ),
            learn_lesson(
                id = "cppstudio-ide-ai",
                title = "AI 助手",
                summary = "解释报错、生成代码、边问边学。",
                est_minutes = 5,
                steps = listOf(
                    learn_step.concept(
                        "cppstudio-ide-ai-c",
                        "把 AI 当同学",
                        listOf(
                            ide_text("编辑器里的 **AI 助手**能结合你当前打开的文件回答问题，常用的三种问法："),
                            ide_text(
                                """
                                - **解释**：「这段代码在做什么」「`undefined reference to main` 是什么意思」；
                                - **生成**：「写一个函数判断回文」「把这段改成 RAII 写法」；
                                - **学习**：「指针和引用有什么区别」「整理一下 CMakeLists.txt 的写法」。
                                """.trimIndent()
                            ),
                            ide_text("报错时最省事的用法：把**构建输出里的第一条错误**整段贴进去，问「怎么改」。AI 认识 `g++`/clangd 的报错格式，通常能直接指出缺哪个头文件、少哪个定义。"),
                            ide_warn("AI 会写错，也会编出不存在的 API。**编译器的结果永远比回答可信**：拿到代码先构建，红了就把报错再贴回去。"),
                            ide_note("使用 AI 助手需要先在设置里配置可用的服务；对话不会自动改动你的项目文件，所有修改都要你确认。")
                        )
                    )
                )
            )
        )
    )
)
