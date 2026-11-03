package com.xiaonai.cppstudio.ui.screens.main

/**
 * 「开发工具」页可触发的安装动作。
 *
 * C/C++ 版本的组件划分：编译器（build-base）、构建系统（cmake）、
 * 语言服务（clangd）、格式化（clang-format）、调试（gdb）、版本控制（git）。
 */
enum class toolchain_action {
    /** 一键安装完整工具链（build-base + cmake + clang 系 + gdb + git） */
    INSTALL_CPP_TOOLCHAIN,

    /** 仅编译器 / 头文件 / make（build-base） */
    INSTALL_BUILD_BASE,

    /** 仅 CMake + pkgconf */
    INSTALL_CMAKE,

    /** 仅 clangd（+ clang） */
    INSTALL_CLANGD,

    /** 仅 clang-format */
    INSTALL_CLANG_FORMAT,

    /** 仅 gdb */
    INSTALL_GDB,

    /** 仅 git */
    INSTALL_GIT
}

data class toolchain_trigger(
    val title: String,
    val action: toolchain_action,
    val source: String = "",
    val version: String = "",
    val sha256: String = ""
)

data class toolchain_custom_install_request(
    val title: String,
    val on_install: (String) -> Unit
)
