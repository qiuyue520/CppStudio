package com.xiaonai.cppstudio.learn

/**
 * 实战轨道：5 个能真正跑起来的 C/C++ 小项目。
 *
 * 只做概念说明（不配交互练习）：这些项目需要多文件、第三方库或图形环境，
 * 练习编辑器（单文件判题）装不下，直接在项目里跟着敲更合适。
 */

private fun practical_text(md: String) = learn_block.text(md.trimIndent())
private fun practical_code(src: String) = learn_block.code(normalize_lesson_code(src))
private fun practical_note(text: String) = learn_block.callout("note", text)
private fun practical_tip(text: String) = learn_block.callout("tip", text)
private fun practical_warn(text: String) = learn_block.callout("warn", text)

internal fun practical_learn_tracks(): List<learn_track> = listOf(
    learn_track(
        id = "cpp-practical",
        title = "C/C++ 实战",
        subtitle = "命令行工具、词频统计、静态库与单测、SDL2 动画、sqlite3 记账",
        accent_color = 0xFF7C5CFFL,
        category = "实战开发",
        lessons = listOf(
            learn_lesson(
                id = "go-cli",
                title = "命令行工具",
                summary = "argc/argv、选项解析与退出码。",
                est_minutes = 8,
                steps = listOf(
                    learn_step.concept(
                        "go-cli-concept",
                        "从 argv 开始",
                        listOf(
                            practical_text("`main` 的两个参数就是命令行：`argc` 是参数个数（含程序名），`argv` 是字符串数组。**`argv[0]` 是程序名**，用户参数从 `argv[1]` 开始："),
                            practical_code(
                                """
                                #include <iostream>
                                #include <string>
                                #include <vector>

                                int main(int argc, char** argv) {
                                    std::vector<std::string> args(argv + 1, argv + argc);
                                    if (args.empty()) {
                                        std::cerr << "用法: app <名称> [--verbose]" << std::endl;
                                        return 1;   // 非零退出码表示失败
                                    }

                                    bool verbose = false;
                                    for (const auto& arg : args) {
                                        if (arg == "--verbose" || arg == "-v") verbose = true;
                                    }

                                    std::cout << "Hello, " << args.front() << "!" << std::endl;
                                    if (verbose) {
                                        std::cout << "参数个数: " << args.size() << std::endl;
                                    }
                                    return 0;
                                }
                                """
                            ),
                            practical_text("**退出码是接口的一部分**：`0` 成功、非零失败。脚本里用 `${'$'}?` 读上一条命令的退出码，`app && echo ok` 也依赖它。工具越自动化，退出码越重要。"),
                            practical_text("选项一多，手写 `if` 会变成一团乱麻。常见做法有两种：自己写一张选项表，或者引入单头文件的解析库（如 CLI11、cxxopts）——它们只有一个 `.hpp`，直接放进 `src/` 就能用，不需要装系统包："),
                            practical_code(
                                """
                                #include <iostream>
                                #include <string>
                                #include <vector>

                                struct options {
                                    std::string name;
                                    int count = 1;
                                    bool verbose = false;
                                };

                                // 手写解析：只认 --name=xx / -n xx / --verbose 这几种形式
                                bool parse(const std::vector<std::string>& args, options& out) {
                                    for (size_t i = 0; i < args.size(); ++i) {
                                        const std::string& a = args[i];
                                        if (a.rfind("--name=", 0) == 0) {
                                            out.name = a.substr(7);
                                        } else if ((a == "-n" || a == "--name") && i + 1 < args.size()) {
                                            out.name = args[++i];
                                        } else if (a == "--verbose") {
                                            out.verbose = true;
                                        } else {
                                            std::cerr << "未知参数: " << a << std::endl;
                                            return false;
                                        }
                                    }
                                    return true;
                                }
                                """
                            ),
                            practical_note("程序名、子命令按 `argv[1]` 分发（`app add ...` / `app list`）；每个子命令再单独解析它自己的参数，和 `git`、`cmake` 的组织方式一样。"),
                            practical_tip("跑起来看效果：在项目配置的**运行参数**里填 `CppStudio --verbose`，或者到终端里直接 `./build/你的程序 CppStudio --verbose`。")
                        )
                    )
                )
            ),
            learn_lesson(
                id = "cpp-wordcount",
                title = "读写文件统计词频",
                summary = "ifstream 逐词读取 + map 计数 + 排序输出。",
                est_minutes = 10,
                steps = listOf(
                    learn_step.concept(
                        "cpp-wordcount-concept",
                        "先造一份输入",
                        listOf(
                            practical_text("先从文件读数据。`std::ifstream` 的 `operator>>` 会**按空白切词**，`std::getline` 按行读——统计词频用前者最省事："),
                            practical_code(
                                """
                                #include <fstream>
                                #include <iostream>
                                #include <map>
                                #include <string>

                                int main(int argc, char** argv) {
                                    if (argc < 2) {
                                        std::cerr << "用法: wordcount <文件>" << std::endl;
                                        return 1;
                                    }
                                    std::ifstream in(argv[1]);
                                    if (!in) {
                                        std::cerr << "打不开 " << argv[1] << std::endl;
                                        return 1;
                                    }

                                    std::map<std::string, int> counts;
                                    std::string word;
                                    while (in >> word) {   // 每次读一个「词」
                                        ++counts[word];
                                    }
                                    std::cout << "不同单词: " << counts.size() << std::endl;
                                    return 0;
                                }
                                """
                            ),
                            practical_text("计数用 `std::map`（有序、稳定）。`counts[word]++` 是 C++ 里最顺手的写法：key 不存在时自动插入 0 再自增。"),
                            practical_text("要按**出现次数**排序，就得把 map 的键值对搬进 vector（map 自己只能按 key 排序），再用 lambda 自定义比较规则。次数相同按字典序，输出才稳定："),
                            practical_code(
                                """
                                #include <algorithm>
                                #include <utility>
                                #include <vector>

                                std::vector<std::pair<std::string, int>> items(counts.begin(), counts.end());
                                std::sort(items.begin(), items.end(),
                                          [](const auto& a, const auto& b) {
                                              if (a.second != b.second) return a.second > b.second;
                                              return a.first < b.first;
                                          });

                                for (size_t i = 0; i < items.size() && i < 10; ++i) {
                                    std::cout << items[i].second << "\t" << items[i].first << std::endl;
                                }
                                """
                            ),
                            practical_text("真实文本还得清洗：把标点换成空格、统一小写、跳过超短词。注意 `std::tolower` 的参数必须是 `unsigned char`，直接传可能带负号的 `char` 是未定义行为："),
                            practical_code(
                                """
                                #include <cctype>

                                std::string clean(const std::string& raw) {
                                    std::string out;
                                    for (char ch : raw) {
                                        unsigned char c = static_cast<unsigned char>(ch);
                                        if (std::isalnum(c)) {
                                            out.push_back(static_cast<char>(std::tolower(c)));
                                        }
                                    }
                                    return out;
                                }
                                """
                            ),
                            practical_tip("先用终端造测试文件：`printf 'a b a\\nc a\\n' > sample.txt`，再 `./build/wordcount sample.txt`，马上能看到 `a 3`。")
                        )
                    )
                )
            ),
            learn_lesson(
                id = "cpp-static-lib",
                title = "CMake 静态库 + 单元测试",
                summary = "把代码拆成库，用 CTest 自动验证。",
                est_minutes = 12,
                steps = listOf(
                    learn_step.concept(
                        "cpp-static-lib-concept",
                        "目录结构先立起来",
                        listOf(
                            practical_text("把可复用的逻辑放进库、`main` 只负责调用，是 C++ 项目最常见的分层。新建项目时选**静态库**或 **CMake 单测**模板就能拿到这个骨架："),
                            practical_code(
                                """
                                include/mathlib.h      # 声明（public 接口）
                                src/mathlib.cpp        # 定义（实现）
                                main.cpp               # 可执行程序：调用库
                                tests/test_mathlib.cpp # 测试：也链接这个库
                                CMakeLists.txt
                                """
                            ),
                            practical_text("头文件只放声明，加 `#pragma once` 防止重复包含；实现在 `.cpp` 里。**公共头文件所在目录**通过 `target_include_directories(... PUBLIC ...)` 暴露出去，链接这个库的目标就自动有了 include 路径："),
                            practical_code(
                                """
                                // include/mathlib.h
                                #pragma once

                                namespace mathlib {
                                long long sum_to(int n);
                                long long factorial(int n);
                                }
                                """
                            ),
                            practical_code(
                                """
                                // src/mathlib.cpp
                                #include "mathlib.h"

                                namespace mathlib {

                                long long sum_to(int n) {
                                    long long total = 0;
                                    for (int i = 1; i <= n; ++i) total += i;
                                    return total;
                                }

                                long long factorial(int n) {
                                    long long result = 1;
                                    for (int i = 2; i <= n; ++i) result *= i;
                                    return result;
                                }

                                } // namespace mathlib
                                """
                            ),
                            practical_text("CMake 里用 `add_library(... STATIC ...)` 生成静态库（`.a`），再用 `target_link_libraries` 把库接给程序与测试；`enable_testing()` + `add_test()` 注册测试用例："),
                            practical_code(
                                """
                                cmake_minimum_required(VERSION 3.20)
                                project(mathdemo LANGUAGES CXX)

                                set(CMAKE_CXX_STANDARD 17)
                                set(CMAKE_CXX_STANDARD_REQUIRED ON)
                                set(CMAKE_EXPORT_COMPILE_COMMANDS ON)

                                add_library(mathlib STATIC src/mathlib.cpp)
                                target_include_directories(mathlib PUBLIC ${'$'}{CMAKE_CURRENT_SOURCE_DIR}/include)

                                add_executable(demo main.cpp)
                                target_link_libraries(demo PRIVATE mathlib)

                                enable_testing()
                                add_executable(test_mathlib tests/test_mathlib.cpp)
                                target_link_libraries(test_mathlib PRIVATE mathlib)
                                add_test(NAME mathlib_tests COMMAND test_mathlib)
                                """
                            ),
                            practical_text("测试代码就是一个返回 0 的程序：断言失败会 abort，CTest 记为失败。"),
                            practical_code(
                                """
                                #include <cassert>
                                #include "mathlib.h"

                                int main() {
                                    assert(mathlib::sum_to(100) == 5050);
                                    assert(mathlib::factorial(5) == 120);
                                    return 0;
                                }
                                """
                            ),
                            practical_text("构建并跑测试："),
                            practical_code(
                                """
                                cmake -B build -DCMAKE_BUILD_TYPE=Debug
                                cmake --build build -j4
                                ctest --test-dir build --output-on-failure
                                """
                            ),
                            practical_warn("`assert` 在 Release 构建里会被 `NDEBUG` 关掉，正式项目请换 GoogleTest 或 Catch2（库管理页一键安装），它们能给出失败的表达式和文件行号。"),
                            practical_note("静态库会被复制进最终的可执行文件，分发时不用带 `.a`；需要多个程序共享一份实现时才考虑 `SHARED` 动态库。")
                        )
                    )
                )
            ),
            learn_lesson(
                id = "cpp-sdl2-box",
                title = "SDL2 画一个移动方块",
                summary = "窗口、渲染循环、事件与逐帧更新。",
                est_minutes = 12,
                steps = listOf(
                    learn_step.concept(
                        "cpp-sdl2-box-concept",
                        "游戏循环的基本形状",
                        listOf(
                            practical_text("所有实时图形程序都是一个循环：**收事件 → 更新状态 → 清屏 → 画一帧 → 等下一帧**。SDL2 只是把这几步换成函数调用。先装库：**库管理** → `sdl2`。"),
                            practical_code(
                                """
                                #include <SDL2/SDL.h>

                                int main(int argc, char** argv) {
                                    if (SDL_Init(SDL_INIT_VIDEO) != 0) {
                                        SDL_Log("SDL_Init 失败: %s", SDL_GetError());
                                        return 1;
                                    }

                                    SDL_Window* window = SDL_CreateWindow(
                                        "移动方块",
                                        SDL_WINDOWPOS_CENTERED, SDL_WINDOWPOS_CENTERED,
                                        640, 480, SDL_WINDOW_SHOWN);
                                    if (!window) {
                                        SDL_Log("创建窗口失败: %s", SDL_GetError());
                                        SDL_Quit();
                                        return 1;
                                    }

                                    SDL_Renderer* renderer = SDL_CreateRenderer(
                                        window, -1, SDL_RENDERER_ACCELERATED | SDL_RENDERER_PRESENTVSYNC);
                                    if (!renderer) {
                                        SDL_Log("创建渲染器失败: %s", SDL_GetError());
                                        SDL_DestroyWindow(window);
                                        SDL_Quit();
                                        return 1;
                                    }

                                    SDL_Rect box{0, 200, 48, 48};   // x, y, w, h
                                    int vx = 4;                     // 每帧水平位移
                                    bool running = true;

                                    while (running) {
                                        SDL_Event event;
                                        while (SDL_PollEvent(&event)) {
                                            if (event.type == SDL_QUIT) running = false;
                                        }

                                        box.x += vx;                    // 更新
                                        if (box.x < 0 || box.x + box.w > 640) vx = -vx;   // 撞墙反弹

                                        SDL_SetRenderDrawColor(renderer, 24, 26, 32, 255);
                                        SDL_RenderClear(renderer);      // 清屏
                                        SDL_SetRenderDrawColor(renderer, 88, 166, 255, 255);
                                        SDL_RenderFillRect(renderer, &box);   // 画方块
                                        SDL_RenderPresent(renderer);    // 显示这一帧

                                        SDL_Delay(16);                  // 约 60 FPS
                                    }

                                    SDL_DestroyRenderer(renderer);
                                    SDL_DestroyWindow(window);
                                    SDL_Quit();
                                    return 0;
                                }
                                """
                            ),
                            practical_text("CMake 侧用 `pkg-config` 找 SDL2（库管理装好 `sdl2-dev` 之后它就存在了）："),
                            practical_code(
                                """
                                find_package(PkgConfig REQUIRED)
                                pkg_check_modules(SDL2 REQUIRED sdl2)

                                add_executable(game main.cpp)
                                target_include_directories(game PRIVATE ${'$'}{SDL2_INCLUDE_DIRS})
                                target_link_libraries(game PRIVATE ${'$'}{SDL2_LIBRARIES})
                                """
                            ),
                            practical_text("想让它动得更「真」，就把「每帧移动多少像素」改成「每秒移动多少像素 × 上一帧到现在过了多久」（`SDL_GetTicks64()` 计算 delta time）——这样不同帧率下速度一致。"),
                            practical_warn("如果 `SDL_Init` 报 `No available video device`，说明当前运行环境没有图形显示服务（proot 里没有 X11/Wayland），代码和 CMake 接法都是对的，换到有图形环境的设备上就能看到窗口。手机场景里这类程序更适合作为「渲染循环模板」来参考。"),
                            practical_tip("加键盘控制：在事件循环里判断 `event.type == SDL_KEYDOWN`，看 `event.key.keysym.sym` 是不是 `SDLK_LEFT`/`SDLK_RIGHT`，直接改 `box.x`。")
                        )
                    )
                )
            ),
            learn_lesson(
                id = "go-database",
                title = "用 sqlite3 做小型记账",
                summary = "sqlite3 C API：建表、绑定参数、查询与事务。",
                est_minutes = 12,
                steps = listOf(
                    learn_step.concept(
                        "go-database-concept",
                        "库里装、CMake 里接",
                        listOf(
                            practical_text("**库管理** → `sqlite` 安装 `sqlite-dev`，然后 CMake 里用 `pkg-config` 找它："),
                            practical_code(
                                """
                                find_package(PkgConfig REQUIRED)
                                pkg_check_modules(SQLITE REQUIRED sqlite3)

                                add_executable(ledger main.cpp)
                                target_include_directories(ledger PRIVATE ${'$'}{SQLITE_INCLUDE_DIRS})
                                target_link_libraries(ledger PRIVATE ${'$'}{SQLITE_LIBRARIES})
                                """
                            ),
                            practical_text("sqlite3 是 C 库，API 不长：`sqlite3_open` 打开、`sqlite3_exec` 跑不带参数的语句、`sqlite3_prepare_v2` + `sqlite3_bind_*` + `sqlite3_step` 跑带参数的语句、`sqlite3_finalize` 收尾。先建表："),
                            practical_code(
                                """
                                #include <cstdio>
                                #include <sqlite3.h>

                                int main() {
                                    sqlite3* db = nullptr;
                                    if (sqlite3_open("ledger.db", &db) != SQLITE_OK) {
                                        std::fprintf(stderr, "打开失败: %s\n", sqlite3_errmsg(db));
                                        return 1;
                                    }

                                    const char* ddl =
                                        "CREATE TABLE IF NOT EXISTS entries ("
                                        "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                                        "day TEXT NOT NULL,"
                                        "amount REAL NOT NULL,"
                                        "note TEXT);";

                                    char* error = nullptr;
                                    if (sqlite3_exec(db, ddl, nullptr, nullptr, &error) != SQLITE_OK) {
                                        std::fprintf(stderr, "建表失败: %s\n", error);
                                        sqlite3_free(error);
                                        sqlite3_close(db);
                                        return 1;
                                    }
                                    std::puts("建表成功");
                                    sqlite3_close(db);
                                    return 0;
                                }
                                """
                            ),
                            practical_text("插入用**参数绑定**，永远不要把金额、备注拼进 SQL 字符串（拼接既会转义出错，也会被注入）："),
                            practical_code(
                                """
                                const char* sql = "INSERT INTO entries (day, amount, note) VALUES (?, ?, ?)";
                                sqlite3_stmt* stmt = nullptr;
                                if (sqlite3_prepare_v2(db, sql, -1, &stmt, nullptr) != SQLITE_OK) {
                                    std::fprintf(stderr, "准备失败: %s\n", sqlite3_errmsg(db));
                                    return 1;
                                }

                                sqlite3_bind_text(stmt, 1, "2026-01-01", -1, SQLITE_TRANSIENT);
                                sqlite3_bind_double(stmt, 2, 35.5);
                                sqlite3_bind_text(stmt, 3, "午饭", -1, SQLITE_TRANSIENT);

                                if (sqlite3_step(stmt) != SQLITE_DONE) {
                                    std::fprintf(stderr, "插入失败: %s\n", sqlite3_errmsg(db));
                                }
                                sqlite3_finalize(stmt);   // 每条语句用完都要 finalize
                                """
                            ),
                            practical_text("查询：`sqlite3_step` 返回 `SQLITE_ROW` 表示还有一行，用 `sqlite3_column_*` 按列号取值；返回 `SQLITE_DONE` 表示读完了："),
                            practical_code(
                                """
                                sqlite3_stmt* query = nullptr;
                                sqlite3_prepare_v2(db, "SELECT day, amount, note FROM entries ORDER BY day;",
                                                   -1, &query, nullptr);

                                double total = 0.0;
                                while (sqlite3_step(query) == SQLITE_ROW) {
                                    const char* day = (const char*)sqlite3_column_text(query, 0);
                                    double amount  = sqlite3_column_double(query, 1);
                                    const char* note = (const char*)sqlite3_column_text(query, 2);
                                    std::printf("%s  %8.2f  %s\n", day, amount, note ? note : "");
                                    total += amount;
                                }
                                sqlite3_finalize(query);
                                std::printf("合计: %.2f\n", total);
                                """
                            ),
                            practical_text("一次要写多条记录（比如批量导入）时用事务包起来，否则每条 INSERT 都会各自落盘，慢上几十倍："),
                            practical_code(
                                """
                                sqlite3_exec(db, "BEGIN;", nullptr, nullptr, nullptr);
                                // ... 多条 INSERT ...
                                if (sqlite3_exec(db, "COMMIT;", nullptr, nullptr, nullptr) != SQLITE_OK) {
                                    sqlite3_exec(db, "ROLLBACK;", nullptr, nullptr, nullptr);
                                }
                                """
                            ),
                            practical_note("`sqlite3_open` 失败时也要 `sqlite3_close`（哪怕 db 是空句柄），不然会漏一点内存；数据库文件 `ledger.db` 会出现在程序运行目录（CppStudio 里就是项目根目录）。"),
                            practical_tip("把 `entries` 换成 SQLite 官方命令行也能查：终端里 `apk add sqlite`，然后 `sqlite3 ledger.db 'SELECT SUM(amount) FROM entries;'`，两边看同一份数据。")
                        )
                    )
                )
            )
        )
    )
)
