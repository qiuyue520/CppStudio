package com.xiaonai.cppstudio.learn

/**
 * C/C++ 进阶轨道（6 课）+ 并发轨道（3 课）。
 *
 * 进阶部分覆盖 RAII / 智能指针、模板、STL、异常、文件 IO 与现代 C++；
 * 并发部分用 std::thread / mutex / future 重写。
 * 并发练习都先用 join 收敛，保证判题输出可复现。
 */
internal fun cpp_advanced_tracks(): List<learn_track> = listOf(
    learn_track(
        id = "cpp-advanced",
        title = "C/C++ 进阶",
        subtitle = "RAII 与智能指针、模板、STL、异常、文件 IO 与现代 C++",
        accent_color = learn_content.accent_advanced,
        category = "C/C++ 语言",
        lessons = listOf(
            learn_lesson(
                id = "go-defer",
                title = "RAII 与智能指针",
                summary = "析构函数保证资源一定被释放。",
                est_minutes = 8,
                steps = listOf(
                    learn_step.concept(
                        "go-defer-c",
                        "析构函数：离开作用域必执行",
                        listOf(
                            lesson_text("局部对象离开作用域时会**自动调用析构函数**——提前 `return`、抛异常也一样。把「申请资源」写进构造函数、把「释放资源」写进析构函数，这套做法叫 **RAII**："),
                            lesson_code(
                                """
                                #include <iostream>

                                class File {
                                public:
                                    explicit File(const char* path) {
                                        std::cout << "打开 " << path << std::endl;
                                    }
                                    ~File() {                       // 析构函数：名字是 ~类名
                                        std::cout << "关闭文件" << std::endl;
                                    }
                                };

                                int main() {
                                    File f("note.txt");
                                    std::cout << "读写中" << std::endl;
                                    return 0;   // 离开作用域 → 自动打印「关闭文件」
                                }
                                """
                            ),
                            lesson_text("内存也一样：裸 `new` 出来的对象要自己 `delete`，中间任何一步抛异常就泄漏了。标准库的**智能指针**把这件事交给析构函数："),
                            lesson_code(
                                """
                                #include <memory>

                                auto p = std::make_unique<int>(42);   // 独占所有权，离开作用域自动 delete
                                std::cout << *p << std::endl;         // 42

                                auto q = std::make_shared<int>(7);    // 引用计数共享
                                auto r = q;                           // 计数变 2，最后一个持有者销毁时才释放
                                """
                            ),
                            lesson_tip("默认用 `std::unique_ptr`，确实需要共享再用 `std::shared_ptr`。`std::make_unique` / `std::make_shared` 比直接 `new` 更安全，也更高效。")
                        )
                    ),
                    learn_step.interactive(
                        "go-defer-i",
                        "补上析构函数",
                        listOf(
                            lesson_text(
                                """
                                给 `Guard` 加一个析构函数，打印 `离开 ` 加名字（如 `离开 a`）。
                                运行后观察对象**逆序销毁**，输出应为五行：

                                `进入 a`
                                `进入 b`
                                `使用中`
                                `离开 b`
                                `离开 a`
                                """
                            )
                        ),
                        starter_code = """
                            #include <iostream>

                            class Guard {
                            public:
                                explicit Guard(const char* name) : name_(name) {
                                    std::cout << "进入 " << name_ << std::endl;
                                }

                                // 在这里补上析构函数，打印 "离开 " 和 name_

                            private:
                                const char* name_;
                            };

                            int main() {
                                Guard a("a");
                                Guard b("b");
                                std::cout << "使用中" << std::endl;
                                return 0;
                            }
                        """,
                        hints = listOf(
                            "析构函数写成 ~Guard() { ... }，不需要返回类型和参数。",
                            "~Guard() { std::cout << \"离开 \" << name_ << std::endl; }"
                        ),
                        solution = """
                            #include <iostream>

                            class Guard {
                            public:
                                explicit Guard(const char* name) : name_(name) {
                                    std::cout << "进入 " << name_ << std::endl;
                                }

                                ~Guard() {
                                    std::cout << "离开 " << name_ << std::endl;
                                }

                            private:
                                const char* name_;
                            };

                            int main() {
                                Guard a("a");
                                Guard b("b");
                                std::cout << "使用中" << std::endl;
                                return 0;
                            }
                        """,
                        check = exercise_check(
                            expected_output = "进入 a\n进入 b\n使用中\n离开 b\n离开 a",
                            require_source = listOf("~Guard")
                        )
                    ),
                    learn_step.quiz(
                        "go-defer-q",
                        "小测",
                        prompt = "下面哪种资源管理方式是 C++ 推荐的？",
                        options = listOf(
                            "手动 new / delete 一一配对",
                            "把资源交给 unique_ptr 等 RAII 对象",
                            "依赖垃圾回收自动清理",
                            "不释放，进程退出时系统会收走"
                        ),
                        correct_index = 1,
                        explanation = "RAII 把释放动作交给析构函数，提前 return 和异常路径都不会漏；长期运行的程序也不可能靠「进程退出」回收。"
                    )
                )
            ),
            learn_lesson(
                id = "cpp-templates",
                title = "模板入门",
                summary = "函数模板、类模板与实例化。",
                est_minutes = 7,
                steps = listOf(
                    learn_step.concept(
                        "cpp-templates-c",
                        "把类型当参数",
                        listOf(
                            lesson_text("模板让同一段代码适配多种类型，`T` 由调用处的实参推导："),
                            lesson_code(
                                """
                                #include <iostream>

                                template <typename T>
                                T max_of(T a, T b) {
                                    return a > b ? a : b;
                                }

                                int main() {
                                    std::cout << max_of(3, 7) << std::endl;       // 7，T = int
                                    std::cout << max_of(2.5, 1.5) << std::endl;   // 2.5，T = double
                                    std::cout << max_of<int>(3, 7) << std::endl;  // 显式指定 T
                                    return 0;
                                }
                                """
                            ),
                            lesson_text("类模板同理，`std::vector<int>`、`std::map<K, V>` 都是模板实例化的结果。编译器为每种实际用到的类型各生成一份代码，所以模板**没有运行时开销**："),
                            lesson_code(
                                """
                                #include <string>

                                template <typename T>
                                class Box {
                                public:
                                    explicit Box(T value) : value_(value) {}
                                    T get() const { return value_; }

                                private:
                                    T value_;
                                };

                                Box<int> a(1);
                                Box<std::string> b("hi");
                                """
                            ),
                            lesson_tip("模板的声明和实现通常都放头文件：编译每个 .cpp 时要能看到完整定义才能实例化，否则会在链接期报 `undefined reference`。")
                        )
                    ),
                    learn_step.interactive(
                        "cpp-templates-i",
                        "写一个模板函数",
                        listOf(lesson_text("补完模板函数 `max_of`，让它返回较大的那个参数，然后打印 `max_of(3, 7)`（`7`）。")),
                        starter_code = """
                            #include <iostream>

                            template <typename T>
                            T max_of(T a, T b) {
                                // 返回较大的那个
                            }

                            int main() {
                                std::cout << max_of(3, 7) << std::endl;
                                return 0;
                            }
                        """,
                        hints = listOf(
                            "用三目运算符：return a > b ? a : b;",
                            "注意返回类型是 T，不要返回局部变量的引用。"
                        ),
                        solution = """
                            #include <iostream>

                            template <typename T>
                            T max_of(T a, T b) {
                                return a > b ? a : b;
                            }

                            int main() {
                                std::cout << max_of(3, 7) << std::endl;
                                return 0;
                            }
                        """,
                        check = exercise_check(
                            expected_output = "7",
                            require_source = listOf("template <typename T>", "max_of(3, 7)")
                        )
                    ),
                    learn_step.quiz(
                        "cpp-templates-q",
                        "小测",
                        prompt = "模板函数的类型参数通常怎么确定？",
                        options = listOf(
                            "由调用处的实参推导（或显式指定）",
                            "运行时从输入读取",
                            "必须用宏预先定义",
                            "不能推导，必须手写每种类型"
                        ),
                        correct_index = 0,
                        explanation = "模板在编译期实例化：`max_of(3, 7)` 推导出 T = int，`max_of<int>(3, 7)` 则是手动指定。"
                    )
                )
            ),
            learn_lesson(
                id = "go-maps",
                title = "STL 容器与算法",
                summary = "vector、map、unordered_map、set 与 algorithm。",
                est_minutes = 9,
                steps = listOf(
                    learn_step.concept(
                        "go-maps-c",
                        "容器一览",
                        listOf(
                            lesson_text("标准库容器按用途挑：需要顺序就用 `vector`，需要按键查找就用 `map` / `unordered_map`，需要去重排序就用 `set`。"),
                            lesson_code(
                                """
                                #include <map>
                                #include <set>
                                #include <string>
                                #include <unordered_map>
                                #include <vector>

                                std::vector<int> v{3, 1, 2};

                                std::map<std::string, int> ages{{"CppStudio", 5}};  // 按 key 有序
                                ages["C++"] = 40;          // 不存在就插入
                                ages.count("C++");         // 1：存在
                                ages.find("Rust") == ages.end();  // true：不存在

                                std::unordered_map<std::string, int> hash;  // 哈希表，平均 O(1)
                                std::set<int> unique{1, 1, 2};               // {1, 2}：去重且有序
                                """
                            ),
                            lesson_text("`#include <algorithm>` 把常用操作写成泛型算法，配合迭代器和 lambda："),
                            lesson_code(
                                """
                                #include <algorithm>
                                #include <numeric>

                                std::sort(v.begin(), v.end());                     // 升序
                                std::sort(v.begin(), v.end(),
                                          [](int a, int b) { return a > b; });     // 降序（lambda）
                                std::reverse(v.begin(), v.end());

                                auto it = std::find(v.begin(), v.end(), 2);        // 迭代器
                                int total = std::accumulate(v.begin(), v.end(), 0); // 求和
                                """
                            ),
                            lesson_note("`v.size()` 返回 `size_t`（无符号），拿它和 `int` 比较会有「有符号/无符号」警告，也可能在负数上出错；循环变量直接用 `size_t` 或范围 for。")
                        )
                    ),
                    learn_step.interactive(
                        "go-maps-i",
                        "统计单词",
                        listOf(lesson_text("统计 `words` 里 `cpp` 出现的次数，输出 `3`。")),
                        starter_code = """
                            #include <iostream>
                            #include <map>
                            #include <string>
                            #include <vector>

                            int main() {
                                std::vector<std::string> words{"cpp", "rust", "cpp", "kotlin", "cpp"};
                                // 用 map 统计 "cpp" 出现的次数并打印
                                return 0;
                            }
                        """,
                        hints = listOf(
                            "std::map<std::string, int> counts;",
                            "for (const auto& w : words) { counts[w]++; }  然后打印 counts[\"cpp\"]"
                        ),
                        solution = """
                            #include <iostream>
                            #include <map>
                            #include <string>
                            #include <vector>

                            int main() {
                                std::vector<std::string> words{"cpp", "rust", "cpp", "kotlin", "cpp"};
                                std::map<std::string, int> counts;
                                for (const auto& w : words) {
                                    counts[w]++;
                                }
                                std::cout << counts["cpp"] << std::endl;
                                return 0;
                            }
                        """,
                        check = exercise_check(
                            expected_output = "3",
                            require_source = listOf("std::map", "counts")
                        )
                    ),
                    learn_step.quiz(
                        "go-maps-q",
                        "小测",
                        prompt = "`std::map` 与 `std::unordered_map` 的主要区别是？",
                        options = listOf(
                            "map 按 key 有序，unordered_map 用哈希、不保证顺序",
                            "map 查找更快",
                            "unordered_map 不能存字符串 key",
                            "两者完全一样，只是名字不同"
                        ),
                        correct_index = 0,
                        explanation = "map 基于红黑树（O(log n)、有序），unordered_map 基于哈希（平均 O(1)、无序）；要按顺序遍历就用 map。"
                    )
                )
            ),
            learn_lesson(
                id = "go-errors",
                title = "异常处理",
                summary = "throw、try/catch 与 std::exception。",
                est_minutes = 7,
                steps = listOf(
                    learn_step.concept(
                        "go-errors-c",
                        "抛出与捕获",
                        listOf(
                            lesson_text("C++ 用 `throw` 抛出异常、`try`/`catch` 捕获。捕获时**按 const 引用**接，避免拷贝和对象切片："),
                            lesson_code(
                                """
                                #include <iostream>
                                #include <stdexcept>

                                int divide(int a, int b) {
                                    if (b == 0) {
                                        throw std::runtime_error("divide by zero");   // 抛出
                                    }
                                    return a / b;
                                }

                                int main() {
                                    try {
                                        std::cout << divide(10, 2) << std::endl;   // 5
                                        std::cout << divide(1, 0) << std::endl;    // 抛异常，跳到 catch
                                    } catch (const std::exception& e) {
                                        std::cout << "出错: " << e.what() << std::endl;
                                    }
                                    return 0;
                                }
                                """
                            ),
                            lesson_text("标准库的异常都继承自 `std::exception`，`what()` 返回错误描述；想自定义类型就继承它并 override `what()`。构造函数里抛异常时，已经构造完成的成员会被正确析构；析构函数里抛异常则非常危险，默认应标记 `noexcept`。"),
                            lesson_note("并不是所有失败都该用异常：可预期的失败（查不到、解析失败）更适合返回 `std::optional` 或 `bool` + 输出参数；异常适合「不该发生但必须处理」的情况。")
                        )
                    ),
                    learn_step.interactive(
                        "go-errors-i",
                        "安全除法",
                        listOf(
                            lesson_text(
                                """
                                补完 `divide(a, b)`：`b == 0` 时抛 `std::runtime_error("divide by zero")`，否则返回 `a / b`；
                                然后在 main 里打印 `divide(10, 2)` 的结果，再用 try/catch 调用 `divide(1, 0)` 并打印错误。
                                期望输出：

                                `5`
                                `出错: divide by zero`
                                """
                            )
                        ),
                        starter_code = """
                            #include <iostream>
                            #include <stdexcept>

                            int divide(int a, int b) {
                                // b == 0 时抛出 std::runtime_error("divide by zero")
                                return 0;
                            }

                            int main() {
                                // 1. 打印 divide(10, 2)
                                // 2. try 里调用 divide(1, 0)，catch (const std::exception& e) 里打印 出错: e.what()
                                return 0;
                            }
                        """,
                        hints = listOf(
                            "if (b == 0) { throw std::runtime_error(\"divide by zero\"); }",
                            "catch (const std::exception& e) { std::cout << \"出错: \" << e.what() << std::endl; }"
                        ),
                        solution = """
                            #include <iostream>
                            #include <stdexcept>

                            int divide(int a, int b) {
                                if (b == 0) {
                                    throw std::runtime_error("divide by zero");
                                }
                                return a / b;
                            }

                            int main() {
                                std::cout << divide(10, 2) << std::endl;
                                try {
                                    std::cout << divide(1, 0) << std::endl;
                                } catch (const std::exception& e) {
                                    std::cout << "出错: " << e.what() << std::endl;
                                }
                                return 0;
                            }
                        """,
                        check = exercise_check(
                            expected_output = "5\n出错: divide by zero",
                            require_source = listOf("throw", "catch")
                        )
                    ),
                    learn_step.quiz(
                        "go-errors-q",
                        "小测",
                        prompt = "捕获标准异常时推荐的写法是？",
                        options = listOf(
                            "catch (std::exception e)",
                            "catch (const std::exception& e)",
                            "catch (...)",
                            "catch (exception* e)"
                        ),
                        correct_index = 1,
                        explanation = "按值捕获会发生拷贝和对象切片；`catch (...)` 什么都接但拿不到信息，只适合最外层兜底。"
                    )
                )
            ),
            learn_lesson(
                id = "go-files",
                title = "文件读写",
                summary = "ofstream、ifstream 与逐行读取。",
                est_minutes = 7,
                steps = listOf(
                    learn_step.concept(
                        "go-files-c",
                        "fstream 三件套",
                        listOf(
                            lesson_text("`std::ofstream` 写、`std::ifstream` 读、`std::fstream` 读写。流对象本身是 RAII 的：离开作用域自动关闭文件。"),
                            lesson_code(
                                """
                                #include <fstream>
                                #include <iostream>
                                #include <string>

                                int main() {
                                    std::ofstream out("note.txt");     // 默认截断写入
                                    if (!out) {                        // 打开失败不抛异常，必须自己查
                                        std::cerr << "打开失败" << std::endl;
                                        return 1;
                                    }
                                    out << "你好" << std::endl;
                                    out.close();                       // 也可以不写，析构时自动关

                                    std::ifstream in("note.txt");
                                    std::string line;
                                    while (std::getline(in, line)) {   // 每次读一行
                                        std::cout << line << std::endl;
                                    }
                                    return 0;
                                }
                                """
                            ),
                            lesson_text("追加写入用 `std::ios::app`，二进制读写加 `std::ios::binary` 并配合 `read`/`write` 处理字节块："),
                            lesson_code(
                                """
                                std::ofstream log("run.log", std::ios::app);   // 追加，不覆盖

                                std::ifstream bin("data.bin", std::ios::binary);
                                char buf[256];
                                bin.read(buf, sizeof(buf));
                                """
                            ),
                            lesson_tip("相对路径相对**程序运行目录**。在 CppStudio 里运行时工作目录是项目根目录，所以 `note.txt` 会出现在文件树里，而不是 `build/` 里。")
                        )
                    ),
                    learn_step.interactive(
                        "go-files-i",
                        "写完再读",
                        listOf(lesson_text("把字符串 `hello cppstudio` 写入 `hello.txt`，再读回来原样打印。")),
                        starter_code = """
                            #include <fstream>
                            #include <iostream>
                            #include <string>

                            int main() {
                                // 1. ofstream 写入 hello.txt
                                // 2. ifstream 读回来
                                // 3. 打印读到的内容
                                return 0;
                            }
                        """,
                        hints = listOf(
                            "std::ofstream out(\"hello.txt\"); out << \"hello cppstudio\";",
                            "std::ifstream in(\"hello.txt\"); 用 std::getline 或 std::string 一次读完再打印。"
                        ),
                        solution = """
                            #include <fstream>
                            #include <iostream>
                            #include <string>

                            int main() {
                                std::ofstream out("hello.txt");
                                if (!out) {
                                    std::cout << "写入失败" << std::endl;
                                    return 1;
                                }
                                out << "hello cppstudio";
                                out.close();

                                std::ifstream in("hello.txt");
                                std::string content;
                                std::getline(in, content);
                                std::cout << content << std::endl;
                                return 0;
                            }
                        """,
                        check = exercise_check(
                            expected_output = "hello cppstudio",
                            require_source = listOf("std::ofstream", "std::ifstream")
                        )
                    ),
                    learn_step.quiz(
                        "go-files-q",
                        "小测",
                        prompt = "`std::ofstream out(\"a.txt\");` 打开失败时会发生什么？",
                        options = listOf(
                            "抛异常",
                            "流对象进入失败状态，需要自己判断 if (!out)",
                            "自动创建缺失的目录",
                            "打印错误并退出进程"
                        ),
                        correct_index = 1,
                        explanation = "iostream 默认不抛异常，靠状态位报告失败；想让它抛异常要显式设置 `out.exceptions(...)`。"
                    )
                )
            ),
            learn_lesson(
                id = "cpp-modern",
                title = "现代 C++：C++17/20",
                summary = "结构化绑定、optional、string_view、filesystem。",
                est_minutes = 8,
                steps = listOf(
                    learn_step.concept(
                        "cpp-modern-c",
                        "C++17 的日常好用特性",
                        listOf(
                            lesson_text("C++17 提供了好几个「用过就回不去」的特性。结构化绑定一次拆开多个返回值："),
                            lesson_code(
                                """
                                #include <iostream>
                                #include <optional>
                                #include <string>
                                #include <utility>

                                std::pair<int, std::string> user() {
                                    return {1, "CppStudio"};
                                }

                                int main() {
                                    auto [id, name] = user();          // 结构化绑定
                                    std::cout << id << " " << name << std::endl;

                                    std::optional<int> maybe;          // 可能没有值
                                    maybe = 42;
                                    if (maybe.has_value()) {
                                        std::cout << *maybe << std::endl;
                                    }
                                    return 0;
                                }
                                """
                            ),
                            lesson_text("`std::optional<T>` 表达「可能没有值」，比返回 `-1` 或空指针更明确；`std::string_view` 是「指针 + 长度」的只读视图，传参时零拷贝；`std::filesystem` 处理路径、目录和遍历："),
                            lesson_code(
                                """
                                #include <filesystem>
                                #include <iostream>
                                #include <string_view>

                                std::string_view sv = "hello";               // 不拷贝字符串
                                std::filesystem::path p = "src/main.cpp";
                                p.filename();                                // "main.cpp"
                                std::filesystem::exists("build");            // 目录/文件是否存在

                                for (const auto& entry : std::filesystem::directory_iterator("src")) {
                                    std::cout << entry.path() << std::endl;
                                }
                                """
                            ),
                            lesson_text("C++20 又往前一步：`std::format` 类型安全地格式化、ranges 让算法能直接管道串联、concepts 给模板参数加约束。工具链支持就可以用，不支持的场景保持 C++17 写法即可。"),
                            lesson_tip("CppStudio 项目默认用 `-std=c++17`；想用 C++20 特性，在项目配置里把 C++ 标准改成 `c++20` 再重新构建。")
                        )
                    ),
                    learn_step.interactive(
                        "cpp-modern-i",
                        "结构化绑定",
                        listOf(lesson_text("用结构化绑定取出 `user()` 返回的 `id` 和 `name`，输出 `1 CppStudio`。")),
                        starter_code = """
                            #include <iostream>
                            #include <string>
                            #include <utility>

                            std::pair<int, std::string> user() {
                                return {1, "CppStudio"};
                            }

                            int main() {
                                // 用 auto [id, name] 接住返回值，然后打印 1 CppStudio
                                return 0;
                            }
                        """,
                        hints = listOf(
                            "auto [id, name] = user();",
                            "std::cout << id << \" \" << name << std::endl;"
                        ),
                        solution = """
                            #include <iostream>
                            #include <string>
                            #include <utility>

                            std::pair<int, std::string> user() {
                                return {1, "CppStudio"};
                            }

                            int main() {
                                auto [id, name] = user();
                                std::cout << id << " " << name << std::endl;
                                return 0;
                            }
                        """,
                        check = exercise_check(
                            expected_output = "1 CppStudio",
                            require_source = listOf("auto [", "std::pair")
                        )
                    ),
                    learn_step.quiz(
                        "cpp-modern-q",
                        "小测",
                        prompt = "`std::optional<int>` 表示什么？",
                        options = listOf(
                            "指向 int 的指针",
                            "一个「可能有值、也可能没有值」的 int",
                            "可变的 int",
                            "int 的数组"
                        ),
                        correct_index = 1,
                        explanation = "optional 内部存一个标志位 + 值，用 `has_value()` 判断、`*o` 取值；比 magic number 或裸指针更能表达意图。"
                    )
                )
            )
        )
    )
)

internal fun cpp_concurrency_tracks(): List<learn_track> = listOf(
    learn_track(
        id = "cpp-conc",
        title = "C++ 并发",
        subtitle = "std::thread、mutex 与 async/future",
        accent_color = learn_content.accent_conc,
        category = "C/C++ 语言",
        lessons = listOf(
            learn_lesson(
                id = "go-goroutine",
                title = "std::thread",
                summary = "启动线程，并用 join 等它结束。",
                est_minutes = 7,
                steps = listOf(
                    learn_step.concept(
                        "go-goroutine-c",
                        "创建与 join",
                        listOf(
                            lesson_text("`std::thread` 启动一个系统线程，函数和参数跟在构造函数里："),
                            lesson_code(
                                """
                                #include <iostream>
                                #include <thread>

                                void say(const char* s) {
                                    std::cout << s << std::endl;
                                }

                                int main() {
                                    std::thread t(say, "hello");   // 参数按值拷贝给新线程
                                    say("world");                  // 主线程自己执行
                                    t.join();                      // 等 t 结束再继续
                                    return 0;
                                }
                                """
                            ),
                            lesson_text("`t.join()` 阻塞到线程结束；`t.detach()` 让它在后台自生自灭。线程对象销毁时如果既没 join 也没 detach，会直接 `std::terminate`——这是新手最常见的一崩。"),
                            lesson_note("两个线程打印的先后由调度决定，不加同步就是不确定的。要可复现的输出，就用 `join` 控制顺序，或者用锁保护输出。")
                        )
                    ),
                    learn_step.interactive(
                        "go-goroutine-i",
                        "先 hello 后 world",
                        listOf(lesson_text("启动一个线程打印 `hello`，`join` 之后再打印 `world`。输出应为两行：\n\n`hello`\n`world`")),
                        starter_code = """
                            #include <iostream>
                            #include <thread>

                            int main() {
                                // 1. 启动线程打印 hello
                                // 2. join 之后再打印 world
                                return 0;
                            }
                        """,
                        hints = listOf(
                            "std::thread t([]{ std::cout << \"hello\" << std::endl; });",
                            "t.join(); 之后写 std::cout << \"world\" << std::endl;"
                        ),
                        solution = """
                            #include <iostream>
                            #include <thread>

                            int main() {
                                std::thread t([] {
                                    std::cout << "hello" << std::endl;
                                });
                                t.join();
                                std::cout << "world" << std::endl;
                                return 0;
                            }
                        """,
                        check = exercise_check(
                            expected_output = "hello\nworld",
                            require_source = listOf("std::thread", "join")
                        )
                    ),
                    learn_step.quiz(
                        "go-goroutine-q",
                        "小测",
                        prompt = "`std::thread` 对象销毁前必须做什么？",
                        options = listOf(
                            "join() 或 detach()",
                            "delete 掉它",
                            "sleep 一下",
                            "什么都不用做"
                        ),
                        correct_index = 0,
                        explanation = "线程对象析构时若仍关联着可 join 的线程，标准库会调用 `std::terminate` 直接终止进程。"
                    )
                )
            ),
            learn_lesson(
                id = "go-mutex",
                title = "互斥锁与数据竞争",
                summary = "用 std::mutex 保护临界区。",
                est_minutes = 8,
                steps = listOf(
                    learn_step.concept(
                        "go-mutex-c",
                        "mutex 与 lock_guard",
                        listOf(
                            lesson_text("多个线程同时「读-改-写」同一个变量会产生**数据竞争**，结果不可预测（甚至可能被优化成永远算错）。用 `std::mutex` 保护临界区："),
                            lesson_code(
                                """
                                #include <iostream>
                                #include <mutex>
                                #include <thread>
                                #include <vector>

                                int main() {
                                    std::mutex mtx;
                                    long long total = 0;
                                    std::vector<std::thread> pool;

                                    for (int i = 0; i < 4; ++i) {
                                        pool.emplace_back([&] {
                                            for (int n = 0; n < 2500; ++n) {
                                                std::lock_guard<std::mutex> lock(mtx);   // 构造加锁
                                                total += 1;
                                            }                                        // 析构自动解锁
                                        });
                                    }
                                    for (auto& t : pool) t.join();
                                    std::cout << total << std::endl;   // 10000
                                    return 0;
                                }
                                """
                            ),
                            lesson_text("`std::lock_guard` 是 RAII 的锁：异常、提前 return 都会正确解锁，比手写 `lock()`/`unlock()` 安全。C++17 还有 `std::scoped_lock`，可以一次锁多个 mutex 并避免死锁。"),
                            lesson_note("加锁范围要尽量小（只在真正访问共享数据时锁），并且多个锁的获取顺序必须全局一致，否则两个线程互等就会死锁。")
                        )
                    ),
                    learn_step.interactive(
                        "go-mutex-i",
                        "安全数到一万",
                        listOf(lesson_text("4 个线程各把 `total` 加 2500 次，用 `std::lock_guard` 保护累加，全部 join 后打印 `10000`。")),
                        starter_code = """
                            #include <iostream>
                            #include <mutex>
                            #include <thread>
                            #include <vector>

                            int main() {
                                std::mutex mtx;
                                long long total = 0;
                                std::vector<std::thread> pool;

                                for (int i = 0; i < 4; ++i) {
                                    pool.emplace_back([&] {
                                        for (int n = 0; n < 2500; ++n) {
                                            // 加锁后 total += 1
                                        }
                                    });
                                }
                                for (auto& t : pool) t.join();
                                std::cout << total << std::endl;
                                return 0;
                            }
                        """,
                        hints = listOf(
                            "std::lock_guard<std::mutex> lock(mtx);",
                            "把它写在 total += 1; 前面，作用域结束会自动解锁。"
                        ),
                        solution = """
                            #include <iostream>
                            #include <mutex>
                            #include <thread>
                            #include <vector>

                            int main() {
                                std::mutex mtx;
                                long long total = 0;
                                std::vector<std::thread> pool;

                                for (int i = 0; i < 4; ++i) {
                                    pool.emplace_back([&] {
                                        for (int n = 0; n < 2500; ++n) {
                                            std::lock_guard<std::mutex> lock(mtx);
                                            total += 1;
                                        }
                                    });
                                }
                                for (auto& t : pool) t.join();
                                std::cout << total << std::endl;
                                return 0;
                            }
                        """,
                        check = exercise_check(
                            expected_output = "10000",
                            require_source = listOf("lock_guard", "mtx")
                        )
                    ),
                    learn_step.quiz(
                        "go-mutex-q",
                        "小测",
                        prompt = "用 `std::lock_guard` 而不是手动 lock/unlock，主要好处是？",
                        options = listOf(
                            "RAII 保证离开作用域自动解锁，异常路径也不会漏",
                            "比 mutex 更快",
                            "允许同一线程重复加锁",
                            "不需要 mutex 对象"
                        ),
                        correct_index = 0,
                        explanation = "提前 return 或抛异常时手动 unlock 很容易漏，lock_guard 靠析构函数兜住这些路径。"
                    )
                )
            ),
            learn_lesson(
                id = "go-waitgroup",
                title = "async 与 future",
                summary = "把任务丢到后台，再用 future 取结果。",
                est_minutes = 7,
                steps = listOf(
                    learn_step.concept(
                        "go-waitgroup-c",
                        "std::async 与 std::future",
                        listOf(
                            lesson_text("`std::async` 把函数交给后台执行，返回一个 `std::future<T>`；`get()` 取结果，取之前会自动等它算完："),
                            lesson_code(
                                """
                                #include <future>
                                #include <iostream>

                                int compute(int a, int b) {
                                    return a + b;
                                }

                                int main() {
                                    std::future<int> f = std::async(std::launch::async, compute, 2, 3);
                                    // 这里可以同时做别的事情
                                    std::cout << f.get() << std::endl;   // 5
                                    return 0;
                                }
                                """
                            ),
                            lesson_text("比 `std::thread` + 共享变量省事的地方：返回值自动传回，任务里抛的异常也会在 `get()` 处重新抛出。`get()` 只能调用一次，想多次读取结果要先存下来。"),
                            lesson_tip("`std::async` 不写启动策略时可能被延迟执行（等你调用 get 才真正开跑）；需要真并发就显式写 `std::launch::async`。")
                        )
                    ),
                    learn_step.interactive(
                        "go-waitgroup-i",
                        "用 async 算 2+3",
                        listOf(lesson_text("用 `std::async` 在后台计算 `2 + 3`，再用 `get()` 取回结果并打印 `5`。")),
                        starter_code = """
                            #include <future>
                            #include <iostream>

                            int main() {
                                // 1. std::async 计算 2 + 3
                                // 2. 用 future 的 get() 取结果并打印
                                return 0;
                            }
                        """,
                        hints = listOf(
                            "auto f = std::async(std::launch::async, [] { return 2 + 3; });",
                            "std::cout << f.get() << std::endl;"
                        ),
                        solution = """
                            #include <future>
                            #include <iostream>

                            int main() {
                                auto f = std::async(std::launch::async, [] {
                                    return 2 + 3;
                                });
                                std::cout << f.get() << std::endl;
                                return 0;
                            }
                        """,
                        check = exercise_check(
                            expected_output = "5",
                            require_source = listOf("std::async", "get()")
                        )
                    ),
                    learn_step.quiz(
                        "go-waitgroup-q",
                        "小测",
                        prompt = "同一个 `std::future` 的 `get()` 能调用几次？",
                        options = listOf(
                            "一次",
                            "任意多次",
                            "两次",
                            "取决于线程数"
                        ),
                        correct_index = 0,
                        explanation = "`get()` 会移走共享状态，第二次调用是未定义行为；要多次使用请先存成普通变量。"
                    )
                )
            )
        )
    )
)
