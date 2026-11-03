package com.jmwl.cppstudio.libraries

/**
 * 一个可以从 Alpine 仓库一键安装的 C/C++ 库。
 *
 * @param id 稳定标识（列表 key / 收藏记录）
 * @param title 显示名（人类可读，带常用写法）
 * @param packages apk 包名候选（按顺序尝试，第一个能装上的为准；-dev 包才带头文件）
 * @param summary 一句话说明
 * @param category 分类（见 [library_categories]）
 * @param cmake_hint 在 CMakeLists.txt 里的接入方式
 * @param homepage 官网 / 仓库地址
 * @param note 额外提示（体积、替代方案等）
 */
data class library_entry(
    val id: String,
    val title: String,
    val packages: List<String>,
    val summary: String,
    val category: String,
    val cmake_hint: String,
    val homepage: String = "",
    val note: String = ""
) {
    /** 首选包名（显示与安装日志用）。 */
    val primary_package: String
        get() = packages.first()
}

/** 精选库分类（顺序即筛选栏顺序）。 */
object library_categories {
    const val all = "全部"
    const val core = "基础设施"
    const val data = "数据与序列化"
    const val net = "网络与并发"
    const val ui = "图形与界面"
    const val media = "图像与音视频"
    const val test = "测试与调试"
    const val math = "数学与算法"

    val ordered = listOf(all, core, data, net, ui, media, test, math)
}

/**
 * 精选库目录。
 *
 * 这是 CppStudio 相对 GoStudio 的核心增量：Go 有 go get 一条命令，
 * C/C++ 没有统一包管理，新手往往卡在「想用 fmt 却不知道装什么」。
 * 这里把常用库映射到 apk 包名，一键安装 + 自动给出 CMake 接法。
 *
 * 包名以 Alpine v3.24 main/community 仓库为准；不同 Alpine 版本可能改名，
 * 所以每个库都留了候选包名，且「仓库搜索」页可以直接验证。
 */
object library_catalog {

    val entries: List<library_entry> = listOf(
        // ---------------- 基础设施 ----------------
        library_entry(
            id = "fmt",
            title = "fmt",
            packages = listOf("fmt-dev", "fmt"),
            summary = "现代 C++ 格式化库，std::format 的先行实现",
            category = library_categories.core,
            cmake_hint = "find_package(fmt CONFIG REQUIRED)\ntarget_link_libraries(你的目标 PRIVATE fmt::fmt)",
            homepage = "https://github.com/fmtlib/fmt"
        ),
        library_entry(
            id = "spdlog",
            title = "spdlog",
            packages = listOf("spdlog-dev", "spdlog"),
            summary = "高性能日志库，支持异步与多种 sink",
            category = library_categories.core,
            cmake_hint = "find_package(spdlog CONFIG REQUIRED)\ntarget_link_libraries(你的目标 PRIVATE spdlog::spdlog)",
            homepage = "https://github.com/gabime/spdlog"
        ),
        library_entry(
            id = "boost",
            title = "Boost",
            packages = listOf("boost-dev", "boost1.84-dev"),
            summary = "准标准库全家桶（headers + 常用编译库），体积较大",
            category = library_categories.core,
            cmake_hint = "find_package(Boost REQUIRED)\ntarget_link_libraries(你的目标 PRIVATE Boost::boost)",
            homepage = "https://www.boost.org/",
            note = "体积约 300MB；只需 headers 时可只装 boost-dev"
        ),
        library_entry(
            id = "gtest",
            title = "GoogleTest",
            packages = listOf("gtest-dev", "gtest"),
            summary = "Google C++ 单元测试框架",
            category = library_categories.test,
            cmake_hint = "find_package(GTest REQUIRED)\ntarget_link_libraries(你的测试 PRIVATE GTest::gtest_main)",
            homepage = "https://github.com/google/googletest"
        ),
        library_entry(
            id = "catch2",
            title = "Catch2",
            packages = listOf("catch2-dev", "catch2-3-dev", "catch2"),
            summary = "单头风格 C++ 测试框架，断言写起来像自然语言",
            category = library_categories.test,
            cmake_hint = "find_package(Catch2 3 REQUIRED)\ntarget_link_libraries(你的测试 PRIVATE Catch2::Catch2WithMain)",
            homepage = "https://github.com/catchorg/Catch2"
        ),
        library_entry(
            id = "benchmark",
            title = "Google Benchmark",
            packages = listOf("benchmark-dev", "benchmark"),
            summary = "微基准测试框架，测函数耗时",
            category = library_categories.test,
            cmake_hint = "find_package(benchmark REQUIRED)\ntarget_link_libraries(你的目标 PRIVATE benchmark::benchmark)",
            homepage = "https://github.com/google/benchmark"
        ),
        library_entry(
            id = "gdb",
            title = "GDB",
            packages = listOf("gdb"),
            summary = "GNU 调试器（断点 / 单步 / backtrace）",
            category = library_categories.test,
            cmake_hint = "在终端里运行：gdb ./build/你的目标",
            homepage = "https://sourceware.org/gdb/"
        ),
        library_entry(
            id = "valgrind",
            title = "Valgrind",
            packages = listOf("valgrind"),
            summary = "内存错误与泄漏检测（memcheck）",
            category = library_categories.test,
            cmake_hint = "在终端里运行：valgrind --leak-check=full ./build/你的目标",
            homepage = "https://valgrind.org/"
        ),
        library_entry(
            id = "clang-tools",
            title = "clangd / clang-format",
            packages = listOf("clang-extra-tools", "clang"),
            summary = "语言服务（补全/诊断/跳转）与代码格式化",
            category = library_categories.test,
            cmake_hint = "编辑器自动使用；终端里可用 clang-format -i 文件",
            homepage = "https://clangd.llvm.org/"
        ),

        // ---------------- 数据与序列化 ----------------
        library_entry(
            id = "nlohmann-json",
            title = "nlohmann/json",
            packages = listOf("nlohmann-json-dev", "nlohmann-json"),
            summary = "最顺手的单头 JSON 库，现代 C++ 语法",
            category = library_categories.data,
            cmake_hint = "find_package(nlohmann_json CONFIG REQUIRED)\ntarget_link_libraries(你的目标 PRIVATE nlohmann_json::nlohmann_json)",
            homepage = "https://github.com/nlohmann/json"
        ),
        library_entry(
            id = "jsoncpp",
            title = "JsonCpp",
            packages = listOf("jsoncpp-dev", "jsoncpp"),
            summary = "老牌 JSON 库，API 稳定",
            category = library_categories.data,
            cmake_hint = "find_package(jsoncpp CONFIG REQUIRED)\ntarget_link_libraries(你的目标 PRIVATE JsonCpp::JsonCpp)",
            homepage = "https://github.com/open-source-parsers/jsoncpp"
        ),
        library_entry(
            id = "rapidjson",
            title = "RapidJSON",
            packages = listOf("rapidjson-dev", "rapidjson"),
            summary = "腾讯开源的高性能 JSON 解析（header-only）",
            category = library_categories.data,
            cmake_hint = "target_include_directories(你的目标 PRIVATE /usr/include)",
            homepage = "https://github.com/Tencent/rapidjson"
        ),
        library_entry(
            id = "yaml-cpp",
            title = "yaml-cpp",
            packages = listOf("yaml-cpp-dev", "yaml-cpp"),
            summary = "YAML 解析与生成",
            category = library_categories.data,
            cmake_hint = "find_package(yaml-cpp REQUIRED)\ntarget_link_libraries(你的目标 PRIVATE yaml-cpp)",
            homepage = "https://github.com/jbeder/yaml-cpp"
        ),
        library_entry(
            id = "sqlite",
            title = "SQLite3",
            packages = listOf("sqlite-dev", "sqlite"),
            summary = "嵌入式 SQL 数据库（单文件、零配置）",
            category = library_categories.data,
            cmake_hint = "pkg_check_modules(SQLITE REQUIRED sqlite3)\ntarget_link_libraries(你的目标 PRIVATE \${SQLITE_LIBRARIES})",
            homepage = "https://sqlite.org/"
        ),
        library_entry(
            id = "protobuf",
            title = "Protocol Buffers",
            packages = listOf("protobuf-dev", "protobuf"),
            summary = "Google 二进制序列化 + protoc 编译器",
            category = library_categories.data,
            cmake_hint = "find_package(Protobuf REQUIRED)\ntarget_link_libraries(你的目标 PRIVATE \${Protobuf_LIBRARIES})",
            homepage = "https://protobuf.dev/"
        ),
        library_entry(
            id = "tinyxml2",
            title = "TinyXML2",
            packages = listOf("tinyxml2-dev", "tinyxml2"),
            summary = "轻量 XML 解析",
            category = library_categories.data,
            cmake_hint = "find_package(tinyxml2 CONFIG REQUIRED)\ntarget_link_libraries(你的目标 PRIVATE tinyxml2::tinyxml2)",
            homepage = "https://github.com/leethomason/tinyxml2"
        ),
        library_entry(
            id = "pugixml",
            title = "pugixml",
            packages = listOf("pugixml-dev", "pugixml"),
            summary = "XPath 支持的 XML 库，速度很快",
            category = library_categories.data,
            cmake_hint = "target_link_libraries(你的目标 PRIVATE pugixml)",
            homepage = "https://pugixml.org/"
        ),
        library_entry(
            id = "sqlite-orm",
            title = "SQLiteCpp",
            packages = listOf("sqlite-dev", "sqlite"),
            summary = "SQLite 的现代 C++ 封装（需自行 git clone）",
            category = library_categories.data,
            cmake_hint = "git clone https://github.com/SRombauts/SQLiteCpp third_party/SQLiteCpp\nadd_subdirectory(third_party/SQLiteCpp)",
            homepage = "https://github.com/SRombauts/SQLiteCpp",
            note = "仓库无打包版本；安装 sqlite-dev 后可按提示从源码引入"
        ),

        // ---------------- 网络与并发 ----------------
        library_entry(
            id = "curl",
            title = "libcurl",
            packages = listOf("curl-dev", "libcurl"),
            summary = "HTTP/HTTPS/FTP 客户端（几乎所有联网程序的基础）",
            category = library_categories.net,
            cmake_hint = "find_package(CURL REQUIRED)\ntarget_link_libraries(你的目标 PRIVATE CURL::libcurl)",
            homepage = "https://curl.se/libcurl/"
        ),
        library_entry(
            id = "openssl",
            title = "OpenSSL",
            packages = listOf("openssl-dev", "openssl"),
            summary = "TLS / 加密 / 摘要算法",
            category = library_categories.net,
            cmake_hint = "find_package(OpenSSL REQUIRED)\ntarget_link_libraries(你的目标 PRIVATE OpenSSL::SSL OpenSSL::Crypto)",
            homepage = "https://www.openssl.org/"
        ),
        library_entry(
            id = "libuv",
            title = "libuv",
            packages = listOf("libuv-dev", "libuv"),
            summary = "跨平台异步 IO（Node.js 的底层）",
            category = library_categories.net,
            cmake_hint = "pkg_check_modules(UV REQUIRED libuv)\ntarget_link_libraries(你的目标 PRIVATE \${UV_LIBRARIES})",
            homepage = "https://libuv.org/"
        ),
        library_entry(
            id = "asio",
            title = "Asio",
            packages = listOf("asio-dev", "asio"),
            summary = "header-only 异步网络库（可配合 Boost）",
            category = library_categories.net,
            cmake_hint = "find_package(asio CONFIG REQUIRED)\ntarget_link_libraries(你的目标 PRIVATE asio::asio)",
            homepage = "https://think-async.com/Asio/"
        ),
        library_entry(
            id = "libevent",
            title = "libevent",
            packages = listOf("libevent-dev", "libevent"),
            summary = "事件驱动网络库（经典、稳定）",
            category = library_categories.net,
            cmake_hint = "pkg_check_modules(EVENT REQUIRED libevent)\ntarget_link_libraries(你的目标 PRIVATE \${EVENT_LIBRARIES})",
            homepage = "https://libevent.org/"
        ),
        library_entry(
            id = "libgit2",
            title = "libgit2",
            packages = listOf("libgit2-dev", "libgit2"),
            summary = "在程序里操作 Git 仓库",
            category = library_categories.net,
            cmake_hint = "pkg_check_modules(GIT2 REQUIRED libgit2)\ntarget_link_libraries(你的目标 PRIVATE \${GIT2_LIBRARIES})",
            homepage = "https://libgit2.org/"
        ),
        library_entry(
            id = "poco",
            title = "POCO",
            packages = listOf("poco-dev", "poco"),
            summary = "网络 / 线程 / 日志 / 配置的综合库",
            category = library_categories.net,
            cmake_hint = "find_package(Poco REQUIRED COMPONENTS Net Foundation)\ntarget_link_libraries(你的目标 PRIVATE Poco::Net Poco::Foundation)",
            homepage = "https://pocoproject.org/"
        ),
        library_entry(
            id = "zmq",
            title = "ZeroMQ",
            packages = listOf("zeromq-dev", "zeromq"),
            summary = "高性能消息队列（进程内 / 跨机器）",
            category = library_categories.net,
            cmake_hint = "find_package(ZeroMQ REQUIRED)\ntarget_link_libraries(你的目标 PRIVATE libzmq)",
            homepage = "https://zeromq.org/"
        ),

        // ---------------- 图形与界面 ----------------
        library_entry(
            id = "sdl2",
            title = "SDL2",
            packages = listOf("sdl2-dev", "sdl2"),
            summary = "跨平台窗口 / 输入 / 音频（游戏与图形程序首选）",
            category = library_categories.ui,
            cmake_hint = "find_package(SDL2 REQUIRED)\ntarget_link_libraries(你的目标 PRIVATE SDL2::SDL2)",
            homepage = "https://www.libsdl.org/"
        ),
        library_entry(
            id = "sdl3",
            title = "SDL3",
            packages = listOf("sdl3-dev", "sdl3"),
            summary = "SDL 第三代（新 API，部分仓库尚未收录）",
            category = library_categories.ui,
            cmake_hint = "find_package(SDL3 REQUIRED)\ntarget_link_libraries(你的目标 PRIVATE SDL3::SDL3)",
            homepage = "https://github.com/libsdl-org/SDL"
        ),
        library_entry(
            id = "raylib",
            title = "raylib",
            packages = listOf("raylib-dev", "raylib"),
            summary = "极简游戏框架，几十行就是一个完整游戏",
            category = library_categories.ui,
            cmake_hint = "find_package(raylib REQUIRED)\ntarget_link_libraries(你的目标 PRIVATE raylib)",
            homepage = "https://www.raylib.com/"
        ),
        library_entry(
            id = "sfml",
            title = "SFML",
            packages = listOf("sfml-dev", "sfml"),
            summary = "面向对象的窗口 / 图形 / 音频库",
            category = library_categories.ui,
            cmake_hint = "find_package(SFML 2.5 COMPONENTS graphics window system REQUIRED)\ntarget_link_libraries(你的目标 PRIVATE sfml-graphics sfml-window sfml-system)",
            homepage = "https://www.sfml-dev.org/"
        ),
        library_entry(
            id = "imgui",
            title = "Dear ImGui",
            packages = listOf("glfw-dev"),
            summary = "即时模式 GUI，做工具/调试面板极快（Alpine 未收录本体，从源码引入）",
            category = library_categories.ui,
            cmake_hint = "git clone --depth 1 https://github.com/ocornut/imgui third_party/imgui\nadd_subdirectory(third_party/imgui/backends)  # 或用 GLFW+OpenGL3 后端手动加源文件",
            homepage = "https://github.com/ocornut/imgui",
            note = "这里安装它依赖的 GLFW；imgui 本体需 git clone 后 add_subdirectory"
        ),
        library_entry(
            id = "qt6",
            title = "Qt6 (Base)",
            packages = listOf("qt6-qtbase-dev", "qt5-qtbase-dev"),
            summary = "成熟桌面 GUI 框架（体积很大）",
            category = library_categories.ui,
            cmake_hint = "find_package(Qt6 REQUIRED COMPONENTS Widgets)\ntarget_link_libraries(你的目标 PRIVATE Qt6::Widgets)",
            homepage = "https://www.qt.io/",
            note = "约 200MB+，手机上编译较慢"
        ),
        library_entry(
            id = "gtk",
            title = "GTK+3",
            packages = listOf("gtk+3.0-dev", "gtk+3.0"),
            summary = "Linux 桌面主流 GUI 工具包",
            category = library_categories.ui,
            cmake_hint = "pkg_check_modules(GTK3 REQUIRED gtk+-3.0)\ntarget_link_libraries(你的目标 PRIVATE \${GTK3_LIBRARIES})",
            homepage = "https://www.gtk.org/"
        ),
        library_entry(
            id = "glfw",
            title = "GLFW",
            packages = listOf("glfw-dev", "glfw"),
            summary = "OpenGL 窗口/输入库（配 GLAD 用）",
            category = library_categories.ui,
            cmake_hint = "find_package(glfw3 REQUIRED)\ntarget_link_libraries(你的目标 PRIVATE glfw)",
            homepage = "https://www.glfw.org/"
        ),
        library_entry(
            id = "glm",
            title = "GLM",
            packages = listOf("glm-dev", "glm"),
            summary = "OpenGL 风格的数学库（header-only）",
            category = library_categories.math,
            cmake_hint = "find_package(glm CONFIG REQUIRED)\ntarget_link_libraries(你的目标 PRIVATE glm::glm)",
            homepage = "https://github.com/g-truc/glm"
        ),
        library_entry(
            id = "eigen",
            title = "Eigen",
            packages = listOf("eigen-dev", "eigen3-dev", "eigen"),
            summary = "线性代数（矩阵/向量/求解器，header-only）",
            category = library_categories.math,
            cmake_hint = "find_package(Eigen3 REQUIRED)\ntarget_link_libraries(你的目标 PRIVATE Eigen3::Eigen)",
            homepage = "https://eigen.tuxfamily.org/"
        ),

        // ---------------- 图像与音视频 ----------------
        library_entry(
            id = "libpng",
            title = "libpng",
            packages = listOf("libpng-dev", "libpng"),
            summary = "PNG 读写",
            category = library_categories.media,
            cmake_hint = "find_package(PNG REQUIRED)\ntarget_link_libraries(你的目标 PRIVATE PNG::PNG)",
            homepage = "http://www.libpng.org/"
        ),
        library_entry(
            id = "jpeg",
            title = "libjpeg-turbo",
            packages = listOf("libjpeg-turbo-dev", "libjpeg-turbo"),
            summary = "JPEG 读写（SIMD 加速）",
            category = library_categories.media,
            cmake_hint = "find_package(JPEG REQUIRED)\ntarget_link_libraries(你的目标 PRIVATE JPEG::JPEG)",
            homepage = "https://libjpeg-turbo.org/"
        ),
        library_entry(
            id = "freetype",
            title = "FreeType",
            packages = listOf("freetype-dev", "freetype"),
            summary = "字体渲染（配合 SDL_ttf / OpenGL 用）",
            category = library_categories.media,
            cmake_hint = "pkg_check_modules(FREETYPE REQUIRED freetype2)\ntarget_link_libraries(你的目标 PRIVATE \${FREETYPE_LIBRARIES})",
            homepage = "https://freetype.org/"
        ),
        library_entry(
            id = "sdl2-ttf",
            title = "SDL2_ttf",
            packages = listOf("sdl2_ttf-dev", "sdl2_ttf"),
            summary = "SDL2 的 TrueType 文字渲染扩展",
            category = library_categories.media,
            cmake_hint = "pkg_check_modules(SDL2_TTF REQUIRED SDL2_ttf)\ntarget_link_libraries(你的目标 PRIVATE \${SDL2_TTF_LIBRARIES})",
            homepage = "https://github.com/libsdl-org/SDL_ttf"
        ),
        library_entry(
            id = "sdl2-image",
            title = "SDL2_image",
            packages = listOf("sdl2_image-dev", "sdl2_image"),
            summary = "SDL2 图片加载扩展（png/jpg/webp）",
            category = library_categories.media,
            cmake_hint = "pkg_check_modules(SDL2_IMAGE REQUIRED SDL2_image)\ntarget_link_libraries(你的目标 PRIVATE \${SDL2_IMAGE_LIBRARIES})",
            homepage = "https://github.com/libsdl-org/SDL_image"
        ),
        library_entry(
            id = "openal",
            title = "OpenAL",
            packages = listOf("openal-soft-dev", "openal-soft"),
            summary = "3D 音频 API 实现",
            category = library_categories.media,
            cmake_hint = "find_package(OpenAL REQUIRED)\ntarget_link_libraries(你的目标 PRIVATE OpenAL::OpenAL)",
            homepage = "https://openal-soft.org/"
        ),
        library_entry(
            id = "ffmpeg",
            title = "FFmpeg (libav*)",
            packages = listOf("ffmpeg-dev", "ffmpeg-libs"),
            summary = "音视频编解码（体积大，手机端慎用）",
            category = library_categories.media,
            cmake_hint = "pkg_check_modules(AVCODEC REQUIRED libavcodec)\ntarget_link_libraries(你的目标 PRIVATE \${AVCODEC_LIBRARIES})",
            homepage = "https://ffmpeg.org/"
        ),

        // ---------------- 压缩 / 其它 ----------------
        library_entry(
            id = "zlib",
            title = "zlib",
            packages = listOf("zlib-dev", "zlib"),
            summary = "DEFLATE 压缩（gzip/zip 的基础）",
            category = library_categories.core,
            cmake_hint = "find_package(ZLIB REQUIRED)\ntarget_link_libraries(你的目标 PRIVATE ZLIB::ZLIB)",
            homepage = "https://zlib.net/"
        ),
        library_entry(
            id = "libarchive",
            title = "libarchive",
            packages = listOf("libarchive-dev", "libarchive"),
            summary = "统一的压缩包读写（tar/zip/7z…）",
            category = library_categories.core,
            cmake_hint = "pkg_check_modules(ARCHIVE REQUIRED libarchive)\ntarget_link_libraries(你的目标 PRIVATE \${ARCHIVE_LIBRARIES})",
            homepage = "https://libarchive.org/"
        ),
        library_entry(
            id = "pcre2",
            title = "PCRE2",
            packages = listOf("pcre2-dev", "pcre2"),
            summary = "Perl 兼容正则表达式",
            category = library_categories.core,
            cmake_hint = "pkg_check_modules(PCRE2 REQUIRED libpcre2-8)\ntarget_link_libraries(你的目标 PRIVATE \${PCRE2_LIBRARIES})",
            homepage = "https://www.pcre.org/"
        ),
        library_entry(
            id = "ncurses",
            title = "ncurses",
            packages = listOf("ncurses-dev", "ncurses"),
            summary = "终端界面（TUI）库",
            category = library_categories.ui,
            cmake_hint = "find_package(Curses REQUIRED)\ntarget_link_libraries(你的目标 PRIVATE \${CURSES_LIBRARIES})",
            homepage = "https://invisible-island.net/ncurses/"
        ),
        library_entry(
            id = "readline",
            title = "GNU Readline",
            packages = listOf("readline-dev", "readline"),
            summary = "交互式命令行行编辑（历史、补全）",
            category = library_categories.core,
            cmake_hint = "find_package(Readline REQUIRED)\ntarget_link_libraries(你的目标 PRIVATE Readline::Readline)",
            homepage = "https://tiswww.case.edu/php/chet/readline/rltop.html"
        ),
        library_entry(
            id = "mimalloc",
            title = "mimalloc",
            packages = listOf("mimalloc-dev", "mimalloc"),
            summary = "微软开源的高性能内存分配器",
            category = library_categories.core,
            cmake_hint = "find_package(mimalloc CONFIG REQUIRED)\ntarget_link_libraries(你的目标 PRIVATE mimalloc)",
            homepage = "https://github.com/microsoft/mimalloc"
        ),
        library_entry(
            id = "abseil",
            title = "Abseil",
            packages = listOf("abseil-cpp-dev", "abseil-cpp"),
            summary = "Google 的基础库（string/容器/时间）",
            category = library_categories.core,
            cmake_hint = "find_package(absl CONFIG REQUIRED)\ntarget_link_libraries(你的目标 PRIVATE absl::base absl::strings)",
            homepage = "https://abseil.io/"
        )
    )

    /** 按分类筛选。 */
    fun by_category(category: String): List<library_entry> {
        if (category == library_categories.all) return entries
        return entries.filter { it.category == category }
    }

    /** 关键字过滤（名称 / 说明 / 包名 / 分类）。 */
    fun search(keyword: String): List<library_entry> {
        val q = keyword.trim().lowercase()
        if (q.isEmpty()) return entries
        return entries.filter { entry ->
            entry.title.lowercase().contains(q) ||
                entry.summary.lowercase().contains(q) ||
                entry.id.contains(q) ||
                entry.category.lowercase().contains(q) ||
                entry.packages.any { it.contains(q) }
        }
    }

    fun find(id: String): library_entry? = entries.firstOrNull { it.id == id }

    /** 分类 → 该分类下库数量（用于筛选栏角标）。 */
    fun count_by_category(): Map<String, Int> {
        val counts = LinkedHashMap<String, Int>()
        library_categories.ordered.forEach { category ->
            counts[category] = by_category(category).size
        }
        return counts
    }
}
