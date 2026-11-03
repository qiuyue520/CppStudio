package com.jmwl.cppstudio.learn

/**
 * C/C++ 语言基础轨道（10 课）。
 *
 * 所有交互练习都是单文件、可直接 `g++ -std=c++17` 编译的程序，
 * 判题只看 stdout，所以示例都刻意避免了不确定的输出（地址、时间、线程顺序等）。
 */
internal fun cpp_basics_tracks(): List<learn_track> = listOf(
    learn_track(
        id = "cpp-basics",
        title = "C/C++ 基础",
        subtitle = "从 Hello World 到类与多态，写出第一批 C++ 程序",
        accent_color = learn_content.accent_basic,
        category = "C/C++ 语言",
        lessons = listOf(
            learn_lesson(
                id = "go-hello",
                title = "第一个 C++ 程序",
                summary = "从 main 函数和 std::cout 开始。",
                est_minutes = 4,
                steps = listOf(
                    learn_step.concept(
                        "go-hello-c",
                        "main 函数与 #include",
                        listOf(
                            lesson_text("每个 C++ 程序都从 **main 函数**开始执行，`#include` 把标准库的声明引入当前文件："),
                            lesson_code(
                                """
                                #include <iostream>

                                int main() {
                                    std::cout << "Hello, C++!" << std::endl;
                                    return 0;
                                }
                                """
                            ),
                            lesson_text("`#include <iostream>` 引入标准输入输出；`std::cout << ...` 把内容写进输出流；`std::endl` 换行并刷新缓冲区。`int main()` 的返回值就是进程退出码，`0` 表示成功。"),
                            lesson_text("点「运行并检查」时，CppStudio 会先调用 `g++` 把这份源码编译成可执行文件，再运行它——所以**少一个分号或一个引号都会编译失败**，底部的输出就是编译器给你的提示。"),
                            lesson_tip("C++ 语句必须用分号 `;` 结尾，这一点和 Kotlin、Python 都不一样。")
                        )
                    ),
                    learn_step.interactive(
                        "go-hello-i",
                        "打印问候语",
                        listOf(lesson_text("让程序输出：\n\n`Hello, C++!`\n\n改好代码后点「运行并检查」。")),
                        starter_code = """
                            #include <iostream>

                            int main() {
                                // 在下面输出 Hello, C++!
                                return 0;
                            }
                        """,
                        hints = listOf(
                            "用 std::cout 输出，结尾别忘了 std::endl。",
                            "std::cout << \"Hello, C++!\" << std::endl;"
                        ),
                        solution = """
                            #include <iostream>

                            int main() {
                                std::cout << "Hello, C++!" << std::endl;
                                return 0;
                            }
                        """,
                        check = exercise_check(
                            expected_output = "Hello, C++!",
                            require_source = listOf("std::cout")
                        )
                    ),
                    learn_step.quiz(
                        "go-hello-q",
                        "小测",
                        prompt = "一个可执行 C++ 程序的入口是？",
                        options = listOf(
                            "start() 函数",
                            "main 函数",
                            "Main 类",
                            "init.cpp 文件"
                        ),
                        correct_index = 1,
                        explanation = "链接器会去找 main 符号；没有 main 就无法生成可执行文件。"
                    )
                )
            ),
            learn_lesson(
                id = "go-vars",
                title = "变量与类型",
                summary = "int、double、char、bool、auto 与 const。",
                est_minutes = 6,
                steps = listOf(
                    learn_step.concept(
                        "go-vars-c",
                        "声明与初始化",
                        listOf(
                            lesson_text("C++ 是**静态类型**语言：每个变量在编译期就确定了类型。"),
                            lesson_code(
                                """
                                #include <string>

                                int age = 20;          // 整数
                                double price = 9.9;    // 双精度浮点
                                char grade = 'A';      // 单个字符（单引号）
                                bool ok = true;        // 布尔
                                std::string name = "CppStudio";  // 字符串（双引号）

                                auto count = 42;       // auto：编译器推导为 int
                                const int limit = 100; // 常量，之后不能修改
                                """
                            ),
                            lesson_text("常见类型还有 `long`/`long long`/`unsigned`（整数宽度）、`float`（单精度浮点）和 `size_t`（容器长度用的无符号整数）。`auto` 让编译器替你写类型，但它**不会**做运行时推断——类型仍然是编译期确定的。"),
                            lesson_note("局部变量不会自动初始化：`int n;` 里面是随机值。声明时顺手赋值是最省事的习惯。")
                        )
                    ),
                    learn_step.interactive(
                        "go-vars-i",
                        "问好",
                        listOf(lesson_text("声明一个 `std::string` 变量 `name`，值为 `CppStudio`，然后输出：\n\n`Hello, CppStudio!`")),
                        starter_code = """
                            #include <iostream>
                            #include <string>

                            int main() {
                                // 1. 声明 std::string name = "CppStudio"
                                // 2. 输出 Hello, CppStudio!
                                return 0;
                            }
                        """,
                        hints = listOf(
                            "std::string name = \"CppStudio\";",
                            "std::cout << \"Hello, \" << name << \"!\" << std::endl;"
                        ),
                        solution = """
                            #include <iostream>
                            #include <string>

                            int main() {
                                std::string name = "CppStudio";
                                std::cout << "Hello, " << name << "!" << std::endl;
                                return 0;
                            }
                        """,
                        check = exercise_check(
                            expected_output = "Hello, CppStudio!",
                            require_source = listOf("std::string", "std::cout")
                        )
                    ),
                    learn_step.quiz(
                        "go-vars-q",
                        "小测",
                        prompt = "写 `const int n = 5;` 之后又写 `n = 6;`，会发生什么？",
                        options = listOf(
                            "编译错误，const 变量不能赋值",
                            "n 变成 6",
                            "运行时崩溃",
                            "赋值被自动忽略"
                        ),
                        correct_index = 0,
                        explanation = "const 是编译期的承诺，修改它属于编译错误，程序根本生成不出来。"
                    )
                )
            ),
            learn_lesson(
                id = "go-for",
                title = "分支与循环",
                summary = "if/else、for、while 与范围 for。",
                est_minutes = 6,
                steps = listOf(
                    learn_step.concept(
                        "go-for-c",
                        "if 与三种循环",
                        listOf(
                            lesson_text("`if` 用小括号包住条件，花括号包住代码块。**建议始终写花括号**，省掉它以后加第二行代码时最容易埋坑："),
                            lesson_code(
                                """
                                int score = 85;
                                if (score >= 90) {
                                    std::cout << "great" << std::endl;
                                } else if (score >= 60) {
                                    std::cout << "pass" << std::endl;
                                } else {
                                    std::cout << "fail" << std::endl;
                                }
                                """
                            ),
                            lesson_text("经典三段式 `for`、`while`、`do-while` 都有，写法与其它语言一致："),
                            lesson_code(
                                """
                                for (int i = 1; i <= 5; ++i) {
                                    std::cout << i << std::endl;
                                }

                                int n = 3;
                                while (n > 0) {
                                    --n;
                                }
                                """
                            ),
                            lesson_text("遍历容器用**范围 for**（C++11 起），比写下标更不容易越界："),
                            lesson_code(
                                """
                                #include <vector>

                                std::vector<int> nums{1, 2, 3};
                                for (int n : nums) {
                                    std::cout << n << std::endl;
                                }
                                """
                            ),
                            lesson_tip("`++i` 和 `i++` 在这里效果一样；对迭代器/自定义类型，`++i` 少一次拷贝，习惯用它即可。")
                        )
                    ),
                    learn_step.interactive(
                        "go-for-i",
                        "数到五",
                        listOf(lesson_text("用 for 循环输出 `1` 到 `5`，每个数字一行。")),
                        starter_code = """
                            #include <iostream>

                            int main() {
                                // 循环打印 1 到 5
                                return 0;
                            }
                        """,
                        hints = listOf(
                            "for (int i = 1; i <= 5; ++i) { ... }",
                            "循环体里写 std::cout << i << std::endl;"
                        ),
                        solution = """
                            #include <iostream>

                            int main() {
                                for (int i = 1; i <= 5; ++i) {
                                    std::cout << i << std::endl;
                                }
                                return 0;
                            }
                        """,
                        check = exercise_check(
                            expected_output = "1\n2\n3\n4\n5",
                            require_source = listOf("for")
                        )
                    ),
                    learn_step.quiz(
                        "go-for-q",
                        "小测",
                        prompt = "遍历 `std::vector<int> nums` 最简洁的写法是？",
                        options = listOf(
                            "for (int n : nums) { ... }",
                            "for n in nums { ... }",
                            "foreach (n in nums) { ... }",
                            "loop nums { ... }"
                        ),
                        correct_index = 0,
                        explanation = "范围 for（range-based for）是 C++11 引入的语法，只会遍历、不会越界。"
                    )
                )
            ),
            learn_lesson(
                id = "go-funcs",
                title = "函数与重载",
                summary = "定义函数、默认参数与同名重载。",
                est_minutes = 6,
                steps = listOf(
                    learn_step.concept(
                        "go-funcs-c",
                        "返回类型写在最前面",
                        listOf(
                            lesson_text("C++ 函数的**返回类型写在函数名前面**，函数名后面紧跟参数列表。参数默认按值传递，想改调用方的变量就要传指针或引用："),
                            lesson_code(
                                """
                                #include <iostream>

                                // 默认参数：调用 add(1) 等价于 add(1, 10)
                                int add(int a, int b = 10) {
                                    return a + b;
                                }

                                // 重载：同名函数，参数类型不同
                                double add(double a, double b) {
                                    return a + b;
                                }

                                int main() {
                                    std::cout << add(2, 3) << std::endl;      // 5
                                    std::cout << add(2.5, 3.5) << std::endl;  // 6
                                    return 0;
                                }
                                """
                            ),
                            lesson_text("`add(2, 3)` 命中 int 版本，`add(2.5, 3.5)` 命中 double 版本——编译器按实参类型挑最匹配的那个，这叫**重载解析**。"),
                            lesson_note("只有返回类型不同**不能**构成重载，编译器会报「函数重复定义」。默认参数只能在一处声明里给一次。")
                        )
                    ),
                    learn_step.interactive(
                        "go-funcs-i",
                        "写一个 add",
                        listOf(lesson_text("定义函数 `int add(int a, int b)` 返回两数之和，然后打印 `add(2, 3)`（应该是 `5`）。")),
                        starter_code = """
                            #include <iostream>

                            // 在这里定义 add 函数

                            int main() {
                                // 打印 add(2, 3)
                                return 0;
                            }
                        """,
                        hints = listOf(
                            "int add(int a, int b) { return a + b; }",
                            "std::cout << add(2, 3) << std::endl;"
                        ),
                        solution = """
                            #include <iostream>

                            int add(int a, int b) {
                                return a + b;
                            }

                            int main() {
                                std::cout << add(2, 3) << std::endl;
                                return 0;
                            }
                        """,
                        check = exercise_check(
                            expected_output = "5",
                            require_source = listOf("int add", "add(2, 3)")
                        )
                    ),
                    learn_step.quiz(
                        "go-funcs-q",
                        "小测",
                        prompt = "下面哪一组函数构成重载？",
                        options = listOf(
                            "int f(int a) 和 int f(int a, int b)",
                            "int f(int a) 和 int f(int b)",
                            "int f(int a) 和 double f(int a)",
                            "int f(int a) 和 int g(int a)"
                        ),
                        correct_index = 0,
                        explanation = "重载要求**名字相同、参数列表不同**（个数或类型）。只改参数名或只改返回类型都不算。"
                    )
                )
            ),
            learn_lesson(
                id = "cpp-pointers",
                title = "指针与引用",
                summary = "地址、解引用、引用与 const 引用。",
                est_minutes = 8,
                steps = listOf(
                    learn_step.concept(
                        "cpp-pointers-c",
                        "取地址与解引用",
                        listOf(
                            lesson_text("**指针**保存的是变量的地址，`&` 取地址、`*` 解引用（访问它指向的那个值）："),
                            lesson_code(
                                """
                                int n = 42;
                                int* p = &n;        // p 里存的是 n 的地址
                                std::cout << *p;    // 42：解引用
                                *p = 100;           // 通过指针改 n
                                std::cout << n;     // 100
                                """
                            ),
                            lesson_text("**引用**是变量的别名，必须在定义时绑定，之后不能改绑："),
                            lesson_code(
                                """
                                int n = 42;
                                int& r = n;    // r 就是 n，不是拷贝
                                r = 7;
                                std::cout << n;  // 7
                                """
                            ),
                            lesson_text("函数想修改调用方的变量时，C 里传指针、C++ 里常传引用；只读又不想拷贝大对象（比如 `std::string`）就传 `const` 引用："),
                            lesson_code(
                                """
                                void swap(int& a, int& b) {
                                    int t = a;
                                    a = b;
                                    b = t;
                                }

                                void print(const std::string& s) {   // 只读，零拷贝
                                    std::cout << s << std::endl;
                                }
                                """
                            ),
                            lesson_note("指针可能为空（`nullptr`），解引用前要判断；引用必须绑定到真实对象，不存在「空引用」。野指针（未初始化或已释放的指针）是 C++ 最常见的一类崩溃原因。")
                        )
                    ),
                    learn_step.interactive(
                        "cpp-pointers-i",
                        "通过引用交换",
                        listOf(
                            lesson_text(
                                """
                                写一个用**引用参数**交换两个整数的函数 `swap(int& a, int& b)`，
                                然后交换 `a = 3`、`b = 8`，把结果打印成一行：`8 3`。
                                """
                            )
                        ),
                        starter_code = """
                            #include <iostream>

                            // 在这里写 swap(int& a, int& b)

                            int main() {
                                int a = 3;
                                int b = 8;
                                // 调用 swap，然后打印 a 和 b，中间一个空格
                                return 0;
                            }
                        """,
                        hints = listOf(
                            "void swap(int& a, int& b) 里用一个临时变量 t 做三步交换。",
                            "std::cout << a << \" \" << b << std::endl;"
                        ),
                        solution = """
                            #include <iostream>

                            void swap(int& a, int& b) {
                                int t = a;
                                a = b;
                                b = t;
                            }

                            int main() {
                                int a = 3;
                                int b = 8;
                                swap(a, b);
                                std::cout << a << " " << b << std::endl;
                                return 0;
                            }
                        """,
                        check = exercise_check(
                            expected_output = "8 3",
                            require_source = listOf("swap(int&", "swap(a, b)")
                        )
                    ),
                    learn_step.quiz(
                        "cpp-pointers-q",
                        "小测",
                        prompt = "`int* p = nullptr;` 之后直接写 `*p = 1;` 会怎样？",
                        options = listOf(
                            "编译错误，nullptr 不能用",
                            "运行时未定义行为，多半直接崩溃",
                            "自动分配一块内存",
                            "把 0 改成 1"
                        ),
                        correct_index = 1,
                        explanation = "解引用空指针是未定义行为；编译器不会拦你，所以用指针前先判断 `if (p != nullptr)`。"
                    )
                )
            ),
            learn_lesson(
                id = "go-slices",
                title = "数组与 std::vector",
                summary = "C 数组、std::array 与可变长 vector。",
                est_minutes = 7,
                steps = listOf(
                    learn_step.concept(
                        "go-slices-c",
                        "三种序列",
                        listOf(
                            lesson_text("C++ 里存一串同类型元素有三种常见选择："),
                            lesson_code(
                                """
                                #include <array>
                                #include <vector>

                                int raw[3] = {1, 2, 3};            // C 风格数组：长度固定，会退化成指针
                                std::array<int, 3> fixed{1, 2, 3}; // 固定长度，有 size()，可拷贝
                                std::vector<int> nums{1, 2, 3};    // 长度可变

                                nums.push_back(4);             // 追加 → {1, 2, 3, 4}
                                std::cout << nums.size();      // 4
                                std::cout << nums[0];          // 1：下标从 0 开始
                                """
                            ),
                            lesson_text("日常几乎都用 `std::vector`：`.size()` 拿长度、`.push_back()` 追加、`.pop_back()` 删除末尾。`nums[i]` **不做越界检查**，`nums.at(i)` 会检查并抛 `std::out_of_range`，调试阶段可以先用 `at`。"),
                            lesson_code(
                                """
                                for (size_t i = 0; i < nums.size(); ++i) {
                                    std::cout << nums[i] << std::endl;
                                }

                                for (int n : nums) {           // 范围 for，更常用
                                    std::cout << n << std::endl;
                                }
                                """
                            ),
                            lesson_tip("C 风格数组传进函数会「退化」成指针、丢掉长度；接口参数优先用 `std::vector` 或 `std::span`。")
                        )
                    ),
                    learn_step.interactive(
                        "go-slices-i",
                        "求和",
                        listOf(lesson_text("给定 `std::vector<int> nums{1, 2, 3, 4, 5}`，用循环求和并输出 `15`。")),
                        starter_code = """
                            #include <iostream>
                            #include <vector>

                            int main() {
                                std::vector<int> nums{1, 2, 3, 4, 5};
                                // 用循环求和并打印
                                return 0;
                            }
                        """,
                        hints = listOf(
                            "先 int sum = 0;，再遍历累加。",
                            "for (int n : nums) { sum += n; }  然后打印 sum"
                        ),
                        solution = """
                            #include <iostream>
                            #include <vector>

                            int main() {
                                std::vector<int> nums{1, 2, 3, 4, 5};
                                int sum = 0;
                                for (int n : nums) {
                                    sum += n;
                                }
                                std::cout << sum << std::endl;
                                return 0;
                            }
                        """,
                        check = exercise_check(
                            expected_output = "15",
                            require_source = listOf("std::vector", "for")
                        )
                    ),
                    learn_step.quiz(
                        "go-slices-q",
                        "小测",
                        prompt = "给 `std::vector` 追加一个元素用哪个方法？",
                        options = listOf(
                            "append()",
                            "push_back()",
                            "add()",
                            "insert_last()"
                        ),
                        correct_index = 1,
                        explanation = "`append` 是 Python 等语言的写法；C++ 用 `push_back`（尾部插入）或 `emplace_back`（原地构造）。"
                    )
                )
            ),
            learn_lesson(
                id = "go-strings",
                title = "字符串与格式化",
                summary = "std::string、拼接、查找与数字互转。",
                est_minutes = 6,
                steps = listOf(
                    learn_step.concept(
                        "go-strings-c",
                        "std::string 常用操作",
                        listOf(
                            lesson_text("`std::string` 是标准库的字符串类，长度、拼接、查找都是成员函数："),
                            lesson_code(
                                """
                                #include <string>

                                std::string s = "CppStudio";
                                s.size();              // 9
                                s.substr(0, 3);        // "Cpp"
                                s.find("Studio");      // 3；找不到时返回 std::string::npos
                                s += " IDE";           // 拼接
                                s.empty();             // 是否为空
                                """
                            ),
                            lesson_text("数字和字符串互转：`std::to_string` 转字符串，`std::stoi`/`std::stod` 转数字。C 风格字符串是 `const char*`，用 `s.c_str()` 取出来（`printf` 这类 C 接口需要它）："),
                            lesson_code(
                                """
                                std::string msg = "CppStudio 今年 " + std::to_string(5) + " 岁";

                                int age = std::stoi("42");     // 42
                                double pi = std::stod("3.14"); // 3.14

                                std::printf("%s\n", msg.c_str());
                                """
                            ),
                            lesson_text("要控制小数位数、对齐宽度，用 `#include <iomanip>` 里的流操纵器："),
                            lesson_code(
                                """
                                #include <iomanip>

                                std::cout << std::fixed << std::setprecision(2) << 3.14159;  // 3.14
                                """
                            ),
                            lesson_tip("C++20 起可以用 `std::format`，但工具链不一定支持；`std::to_string` + 拼接在任何标准下都能用。")
                        )
                    ),
                    learn_step.interactive(
                        "go-strings-i",
                        "拼一句话",
                        listOf(lesson_text("把 `CppStudio` 和 `5` 拼成一句话并打印：\n\n`CppStudio 今年 5 岁`")),
                        starter_code = """
                            #include <iostream>
                            #include <string>

                            int main() {
                                std::string name = "CppStudio";
                                int age = 5;
                                // 拼出 "CppStudio 今年 5 岁" 并打印
                                return 0;
                            }
                        """,
                        hints = listOf(
                            "std::string msg = name + \" 今年 \" + std::to_string(age) + \" 岁\";",
                            "然后 std::cout << msg << std::endl;"
                        ),
                        solution = """
                            #include <iostream>
                            #include <string>

                            int main() {
                                std::string name = "CppStudio";
                                int age = 5;
                                std::string msg = name + " 今年 " + std::to_string(age) + " 岁";
                                std::cout << msg << std::endl;
                                return 0;
                            }
                        """,
                        check = exercise_check(
                            expected_output = "CppStudio 今年 5 岁",
                            require_source = listOf("std::to_string")
                        )
                    ),
                    learn_step.quiz(
                        "go-strings-q",
                        "小测",
                        prompt = "把整数 `n` 转成 `std::string` 用哪个写法？",
                        options = listOf(
                            "std::to_string(n)",
                            "std::string(n)",
                            "n.to_string()",
                            "str(n)"
                        ),
                        correct_index = 0,
                        explanation = "`std::string(n)` 会把整数当成字符编码，`std::to_string` 才是十进制文本。"
                    )
                )
            ),
            learn_lesson(
                id = "go-structs",
                title = "类与对象",
                summary = "成员变量、构造函数、const 成员函数。",
                est_minutes = 8,
                steps = listOf(
                    learn_step.concept(
                        "go-structs-c",
                        "class 与构造函数",
                        listOf(
                            lesson_text("`struct` 和 `class` 唯一的区别是默认访问权限：`struct` 默认 public，`class` 默认 private。带行为的类型一般用 `class`："),
                            lesson_code(
                                """
                                #include <iostream>

                                class Point {
                                public:
                                    // 构造函数 + 初始化列表
                                    Point(int x, int y) : x_(x), y_(y) {}

                                    // const 成员函数：承诺不修改对象
                                    int sum() const { return x_ + y_; }

                                private:
                                    int x_;
                                    int y_;
                                };

                                int main() {
                                    Point p(2, 3);
                                    std::cout << p.sum() << std::endl;   // 5
                                    return 0;
                                }
                                """
                            ),
                            lesson_text("构造函数名与类名相同、没有返回值，用来保证对象「一创建就是合法的」。初始化列表 `: x_(x)` 直接初始化成员，比在函数体里赋值少一次默认构造。"),
                            lesson_tip("只读接口一律加 `const`：这样 `const Point&` 也能调用，编译器还能帮你发现意外的修改。")
                        )
                    ),
                    learn_step.interactive(
                        "go-structs-i",
                        "给 Point 加成员函数",
                        listOf(lesson_text("定义 `class Point`（成员 `x_`、`y_` 和构造函数），再加一个 `int sum() const` 返回两者之和，最后打印 `Point(2, 3).sum()`（`5`）。")),
                        starter_code = """
                            #include <iostream>

                            // 在这里定义 Point 类和 sum 成员函数

                            int main() {
                                // 创建 Point(2, 3) 并打印 sum()
                                return 0;
                            }
                        """,
                        hints = listOf(
                            "class Point { public: Point(int x, int y) : x_(x), y_(y) {} int sum() const { return x_ + y_; } private: int x_; int y_; };",
                            "Point p(2, 3); 然后 std::cout << p.sum() << std::endl;"
                        ),
                        solution = """
                            #include <iostream>

                            class Point {
                            public:
                                Point(int x, int y) : x_(x), y_(y) {}
                                int sum() const { return x_ + y_; }

                            private:
                                int x_;
                                int y_;
                            };

                            int main() {
                                Point p(2, 3);
                                std::cout << p.sum() << std::endl;
                                return 0;
                            }
                        """,
                        check = exercise_check(
                            expected_output = "5",
                            require_source = listOf("class Point", "sum() const")
                        )
                    ),
                    learn_step.quiz(
                        "go-structs-q",
                        "小测",
                        prompt = "`class` 里成员的默认访问权限是？",
                        options = listOf(
                            "public",
                            "private",
                            "protected",
                            "由编译器自动选择"
                        ),
                        correct_index = 1,
                        explanation = "`class` 默认 private，`struct` 默认 public，这是两者唯一的行为差异。"
                    )
                )
            ),
            learn_lesson(
                id = "go-interface",
                title = "继承与多态",
                summary = "虚函数、override 与抽象类。",
                est_minutes = 9,
                steps = listOf(
                    learn_step.concept(
                        "go-interface-c",
                        "virtual 与 override",
                        listOf(
                            lesson_text("C++ 用**继承 + 虚函数**实现多态：基类声明 `virtual` 函数，派生类用 `override` 覆盖它，调用时按对象的**实际类型**分派："),
                            lesson_code(
                                """
                                #include <iostream>

                                class Shape {
                                public:
                                    virtual ~Shape() = default;        // 有虚函数就要有虚析构
                                    virtual double area() const = 0;   // 纯虚函数 → 抽象类
                                };

                                class Square : public Shape {
                                public:
                                    explicit Square(double side) : side_(side) {}
                                    double area() const override { return side_ * side_; }

                                private:
                                    double side_;
                                };

                                void print_area(const Shape& s) {
                                    std::cout << s.area() << std::endl;
                                }

                                int main() {
                                    Square sq(3);
                                    print_area(sq);   // 9
                                    return 0;
                                }
                                """
                            ),
                            lesson_text("含有纯虚函数（`= 0`）的类是**抽象类**，不能直接创建对象，只能通过指针或引用使用派生类。`print_area` 只依赖 `Shape` 接口，再加一个 `Circle` 也不用改它。"),
                            lesson_note("通过基类指针 `delete` 派生类对象时，基类析构必须是 `virtual`；否则派生类的析构不会执行，成员里申请的资源就泄漏了。")
                        )
                    ),
                    learn_step.interactive(
                        "go-interface-i",
                        "用抽象类求面积",
                        listOf(lesson_text("定义抽象类 `Shape`（纯虚函数 `double area() const`），让 `Square` 继承并实现它，然后打印边长 `3` 的面积：`9`。")),
                        starter_code = """
                            #include <iostream>

                            // 1. 定义抽象类 Shape
                            // 2. 定义 Square，public 继承 Shape 并 override area()

                            int main() {
                                // 3. 创建 Square(3)，通过 const Shape& 打印面积
                                return 0;
                            }
                        """,
                        hints = listOf(
                            "class Shape { public: virtual ~Shape() = default; virtual double area() const = 0; };",
                            "class Square : public Shape { public: explicit Square(double s) : side_(s) {} double area() const override { return side_ * side_; } private: double side_; };",
                            "void show(const Shape& s) { std::cout << s.area() << std::endl; }"
                        ),
                        solution = """
                            #include <iostream>

                            class Shape {
                            public:
                                virtual ~Shape() = default;
                                virtual double area() const = 0;
                            };

                            class Square : public Shape {
                            public:
                                explicit Square(double side) : side_(side) {}
                                double area() const override { return side_ * side_; }

                            private:
                                double side_;
                            };

                            void show(const Shape& s) {
                                std::cout << s.area() << std::endl;
                            }

                            int main() {
                                Square sq(3);
                                show(sq);
                                return 0;
                            }
                        """,
                        check = exercise_check(
                            expected_output = "9",
                            require_source = listOf("virtual", "override", "Shape")
                        )
                    ),
                    learn_step.quiz(
                        "go-interface-q",
                        "小测",
                        prompt = "通过基类指针删除派生类对象，基类必须满足什么条件？",
                        options = listOf(
                            "析构函数是 virtual 的",
                            "是抽象类",
                            "用 public 继承",
                            "有默认构造函数"
                        ),
                        correct_index = 0,
                        explanation = "虚析构保证 `delete base_ptr` 时先执行派生类析构，再执行基类析构。"
                    )
                )
            ),
            learn_lesson(
                id = "go-packages",
                title = "头文件与多文件编译",
                summary = "声明放 .h、定义放 .cpp，理解链接错误。",
                est_minutes = 6,
                steps = listOf(
                    learn_step.concept(
                        "go-packages-c",
                        "声明与定义分离",
                        listOf(
                            lesson_text("C++ 没有「包」，代码按**编译单元**组织：每个 `.cpp` 单独编译成 `.o`，再由链接器把它们合成一个程序。跨文件共享的**声明**放头文件，**定义**放 `.cpp`："),
                            lesson_code(
                                """
                                // include/mathlib.h
                                #pragma once   // 头文件只被包含一次

                                namespace mathlib {
                                int add(int a, int b);          // 只有声明
                                }
                                """
                            ),
                            lesson_code(
                                """
                                // src/mathlib.cpp
                                #include "mathlib.h"

                                namespace mathlib {
                                int add(int a, int b) { return a + b; }   // 这里是定义
                                }
                                """
                            ),
                            lesson_code(
                                """
                                // src/main.cpp
                                #include <iostream>
                                #include "mathlib.h"   // 双引号：先在源文件所在目录找

                                int main() {
                                    std::cout << mathlib::add(2, 3) << std::endl;   // 5
                                    return 0;
                                }
                                """
                            ),
                            lesson_text("`#include <...>` 只找系统目录，`#include \"...\"` 先找项目目录。`namespace` 用来避免不同库里的同名函数打架。"),
                            lesson_note("链接期报 `undefined reference to ...`，九成是**只声明没定义**，或者那个 `.cpp` 没被加进构建：检查 CMakeLists.txt 的源文件列表，别只写了头文件。")
                        )
                    ),
                    learn_step.quiz(
                        "go-packages-q",
                        "小测",
                        prompt = "头文件里通常应该放什么？",
                        options = listOf(
                            "函数的完整定义",
                            "声明（函数原型、类定义、常量）",
                            "main 函数",
                            "所有全局变量的定义"
                        ),
                        correct_index = 1,
                        explanation = "头文件被多个 .cpp 包含，放定义会重复定义（multiple definition）；定义应该放 .cpp。"
                    )
                )
            )
        )
    )
)
