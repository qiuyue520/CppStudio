---
name: cpp-test
description: C/C++ 单元测试编写与运行。当用户需要写测试、用 ctest / Catch2 / GoogleTest、拆分测试目标或看失败输出时使用。
---

# C/C++ 测试技能

你是 C/C++ 测试专家。帮用户写出可维护、能一键跑的测试。

## 运行方式（ctest）

```bash
cmake -B build -DCMAKE_BUILD_TYPE=Debug
cmake --build build -j$(nproc)
ctest --test-dir build --output-on-failure     # cmake >= 3.20
cd build && ctest --output-on-failure          # 老版本 cmake 的写法
ctest --test-dir build -R util -V              # 只跑名字匹配 util 的用例，打印详情
ctest --test-dir build -j$(nproc)              # 并行跑
```

## 组织原则

1. **测试是独立可执行目标**，不要塞进主程序：`add_executable(app_tests tests/test_util.cpp)`
2. **被测代码抽成库**，主程序和测试都链接它，避免同一份 .cpp 编译两遍（否则会出现重复符号/行为不一致）
3. 每个测试可执行文件用 `add_test(NAME xxx COMMAND app_tests)` 注册，ctest 才能发现
4. 失败信息要能定位：断言里带上输入值，不要只写 `EXPECT_TRUE(ok)`

## 最小 ctest 工程（不用第三方框架）

```cmake
# CMakeLists.txt
cmake_minimum_required(VERSION 3.16)
project(myapp LANGUAGES CXX)
set(CMAKE_CXX_STANDARD 17)
enable_testing()

add_library(core STATIC src/util.cpp)
target_include_directories(core PUBLIC include)

add_executable(app src/main.cpp)
target_link_libraries(app PRIVATE core)

add_executable(app_tests tests/test_util.cpp)
target_link_libraries(app_tests PRIVATE core)
add_test(NAME util COMMAND app_tests)
```

```cpp
// tests/test_util.cpp
#include "util.h"
#include <cstdio>
#include <cstdlib>

static int failures = 0;

static void check(bool ok, const char* what) {
    if (!ok) { std::fprintf(stderr, "FAIL: %s\n", what); ++failures; }
}

int main() {
    check(util::add(1, 2) == 3, "add(1,2) == 3");
    check(util::upper("ab") == "AB", "upper(ab) == AB");
    if (failures) { std::fprintf(stderr, "%d 个用例失败\n", failures); return 1; }
    std::puts("全部通过");
    return 0;   // 返回非 0 时 ctest 判为失败
}
```

## Catch2

v3（库形式，推荐）：

```cmake
find_package(Catch2 3 REQUIRED)
add_executable(app_tests tests/test_util.cpp)
target_link_libraries(app_tests PRIVATE core Catch2::Catch2WithMain)
include(Catch)
catch_discover_tests(app_tests)     # 每个 TEST_CASE 注册成独立 ctest 用例
```

v2（单头文件、零依赖，Alpine 里最省事）：把 `catch2/catch.hpp` 放进 `third_party/`，测试文件顶部

```cpp
#define CATCH_CONFIG_MAIN
#include "catch.hpp"
```

```cpp
#include <catch2/catch_test_macros.hpp>   // v2 用 #include "catch.hpp"

TEST_CASE("util::add 加法", "[util]") {
    REQUIRE(util::add(1, 2) == 3);

    SECTION("边界") {
        REQUIRE(util::add(0, 0) == 0);
        REQUIRE(util::add(-1, 1) == 0);
    }
}
```

- `REQUIRE` 失败即中止当前用例，`CHECK` 失败继续跑（收集多个失败）
- `[util]` 是 tag，可用 `./build/app_tests "[util]"` 或 `ctest -R` 过滤
- 直接跑可执行文件也能看结果：`./build/app_tests -s`（打印成功断言）

## GoogleTest

```cmake
find_package(GTest REQUIRED)     # 库管理里装 googletest 后可用
add_executable(app_tests tests/test_util.cpp)
target_link_libraries(app_tests PRIVATE core GTest::gtest_main)
include(GoogleTest)
gtest_discover_tests(app_tests)
```

```cpp
#include <gtest/gtest.h>

TEST(UtilTest, Add) {
    EXPECT_EQ(util::add(1, 2), 3);
}

TEST(UtilTest, AddBoundary) {
    EXPECT_EQ(util::add(0, 0), 0);
    ASSERT_EQ(util::add(-1, 1), 0);   // ASSERT 失败后不再继续本用例
}
```

- `EXPECT_*` 软断言 / `ASSERT_*` 硬断言
- 跑单个用例：`./build/app_tests --gtest_filter=UtilTest.Add`

## 工作流

1. 先用 read 看被测函数签名和行为，确认预期（不确定时先问用户或按名字/注释推断）
2. 列边界：空输入、0、负数、最大值、`nullptr`、空容器、越界前一位
3. 写/改测试文件，同目录放 `test_<模块>.cpp`，在 CMakeLists 里加目标和 `add_test`
4. 用 bash 工具 `cmake --build build -j$(nproc) && ctest --test-dir build --output-on-failure` 验证
5. 测试失败先判断是实现错还是预期写错——不要为了让测试通过而放宽断言

## 常见坑

1. `enable_testing()` 写在子目录里 → 顶层 ctest 看不到用例，必须根 CMakeLists 里调用
2. 测试目标和主程序各自编译同一份 .cpp → 符号重复或行为分叉，抽成库
3. 用 `assert` 做测试断言 → Release 下 `NDEBUG` 会整体失效，改用框架断言或自己返回非 0
4. 跑 ctest 但没 `--output-on-failure`，失败时看不到原因
5. 忘了重新 configure，新加的 `add_test` 不生效
