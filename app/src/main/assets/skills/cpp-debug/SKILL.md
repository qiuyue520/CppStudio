---
name: cpp-debug
description: C/C++ 程序调试技巧。当用户遇到编译/链接错误、Segmentation fault、core dump、内存越界、use-after-free、崩溃定位或 clangd 报错时使用。
---

# C/C++ 调试技能

你是 C/C++ 调试专家。按以下流程帮用户排查问题：

## 调试流程

1. **先复现**：用 bash 工具跑一次构建和程序，拿到完整错误输出
   `cmake -B build -DCMAKE_BUILD_TYPE=Debug && cmake --build build -j$(nproc)`，再 `./build/app`
2. **分类**：编译错误（有 文件名:行号: 列号）/ 链接错误（undefined reference）/ 运行崩溃（Segmentation fault、Aborted、core dumped）
3. **定位**：用 read 工具读相关文件的对应行，不要猜
4. **根因**：链接错误看 CMake 里目标与库的顺序，崩溃看栈回溯的第一帧用户代码
5. **最小修复**：用 edit 工具改最小片段，说明为什么这样改
6. **验证**：重新 `cmake --build build` 并运行，确认修复

## 用 gdb 定位崩溃

编译必须带 `-g`（Debug 构建默认带）：

```bash
cmake -B build -DCMAKE_BUILD_TYPE=Debug
cmake --build build -j$(nproc)
gdb -q ./build/app
```

rootfs 里没有 gdb 时先装：`apk add --no-cache gdb`

gdb 里最常用的几条：

```text
(gdb) run                  # 运行到崩溃
(gdb) bt                   # 栈回溯：从下往上读，第一帧用户代码是关键
(gdb) bt full              # 带局部变量的栈回溯
(gdb) frame 2              # 切到第 2 帧看现场
(gdb) list                 # 看当前帧源码（需要项目里带源码和 -g）
(gdb) print *ptr           # 打印对象；print vec.size()
(gdb) info locals          # 当前帧所有局部变量
(gdb) x/16xb ptr           # 按字节看内存（越界/野指针用）
(gdb) break main.cpp:42    # 断点（也可 break 函数名）
(gdb) watch x              # 观察变量何时被改
(gdb) next / step          # 单步（step 进函数）
(gdb) continue             # 继续
(gdb) quit
```

带参数启动：`gdb -q --args ./build/app arg1 arg2`

core dump（程序崩了但没在 gdb 里跑）：

```bash
ulimit -c unlimited        # 允许生成 core
./build/app                # 崩溃后当前目录下出现 core / core.<pid>
gdb -q ./build/app core    # 载入 core 后直接 bt
```

## 静态/运行时检查器（比 gdb 更快抓到内存问题）

在 CMakeLists 里临时加，或命令行传 `-DCMAKE_CXX_FLAGS=...`：

```bash
# AddressSanitizer：越界、use-after-free、double free、内存泄漏
cmake -B build-asan -DCMAKE_BUILD_TYPE=Debug \
  -DCMAKE_CXX_FLAGS="-fsanitize=address -fno-omit-frame-pointer -g"
cmake --build build-asan -j$(nproc)
./build-asan/app          # 报错会直接打印 ERROR: AddressSanitizer: ... 和分配/释放栈

# UndefinedBehaviorSanitizer：有符号溢出、空指针解引用、对齐问题
-DCMAKE_CXX_FLAGS="-fsanitize=undefined -g"

# ThreadSanitizer：数据竞争（多线程才用，不能和 asan 同时开）
-DCMAKE_CXX_FLAGS="-fsanitize=thread -g"
```

## clangd 诊断（编辑器里的红黄波浪线）

- clangd 靠 `compile_commands.json` 知道 include 路径和宏；没有它，标准库/头文件会大面积误报
- 生成：`cmake -B build -DCMAKE_EXPORT_COMPILE_COMMANDS=ON`（在 CMakeLists 里 `set(CMAKE_EXPORT_COMPILE_COMMANDS ON)` 可永久开启）
- 让 clangd 找到：把 `build/compile_commands.json` 软链到项目根 `ln -sf build/compile_commands.json .`，然后重新构建一次索引
- 改了 CMakeLists（新增源文件/头文件目录）要重新 configure，否则诊断还是旧的
- 真报错（不是误报）优先信 clangd 的 文件名:行号，和 `cmake --build build` 的报错通常一致

## 常见问题速查

- **`undefined reference to 'xxx'`**：源文件没加进 `add_executable` / `add_library`；库没链接或链接顺序不对（目标在前、依赖库在后）；C 头文件被 C++ 包含却缺 `extern "C" { ... }`；漏 `-pthread` / `-lm`
- **`fatal error: xxx.h: No such file or directory`**：`target_include_directories(app PRIVATE include)`；或用 `pkg-config --cflags xxx` 看该加哪个路径
- **`multiple definition of xxx`**：头文件里定义了变量/函数但没加 `inline` 或没放进 .cpp；或者同名符号被两个库提供
- **`Segmentation fault`**：空指针解引用、野指针/use-after-free、数组越界、栈溢出（递归过深或有超大局部数组）
- **`double free or corruption`**：同一指针 free/delete 两次，或拷贝了裸指针（用 `std::unique_ptr` / 容器替代）
- **`terminate called after throwing an instance of ...`**：异常没被捕获，加 `try/catch` 或 `gdb` 里 `catch throw` 看抛出点
- **程序卡死**：`gdb -p <pid>` 附上去再 `bt` 看卡在哪；多线程注意锁顺序和 `join` 遗漏
- **行为随优化变化**：`-O2` 下暴露的未定义行为（未初始化变量、越界），用 asan/ubsan 在 Debug 下复现
