---
name: cpp-perf
description: 排查 C/C++ 程序的性能与内存问题：定位热点、查内存错误与泄漏、读懂编译器优化报告
---

# C/C++ 性能与内存排查

先量再改：先用工具证明瓶颈在哪，再动手优化。

## 1. 编译带符号

`Debug` 构建用于定位，`RelWithDebInfo` 用于「跑得快但仍能看栈」：

```bash
cmake -S . -B build -DCMAKE_BUILD_TYPE=RelWithDebInfo
cmake --build build --parallel 8
```

## 2. 内存错误 / 泄漏

`gdb` 随工具链安装；`valgrind` 可在「库管理」里搜索安装：

```bash
valgrind --leak-check=full --show-leak-kinds=all ./build/你的目标
```

看 `definitely lost` 与首次分配的调用栈；`std::vector` 越界、悬垂指针这类问题 memcheck 会直接给出出错行。

## 3. 崩溃现场

```bash
gdb ./build/你的目标
(gdb) run
(gdb) bt full          # 崩溃时的完整调用栈
(gdb) frame 1
(gdb) print 变量名
```

## 4. 微基准

要对比两版实现，用 Google Benchmark（「库管理」→ Google Benchmark）：

```cpp
#include <benchmark/benchmark.h>

static void BM_parse(benchmark::State& state) {
    for (auto _ : state) {
        parse(input);
    }
}
BENCHMARK(BM_parse);
BENCHMARK_MAIN();
```

## 5. 别忽略编译器

```bash
cmake -S . -B build -DCMAKE_CXX_FLAGS="-Wall -Wextra -O2"
```

告警往往就是未定义行为；改完记得重新构建，让 `compile_commands.json` 跟上，否则 clangd 的诊断会用旧参数。
