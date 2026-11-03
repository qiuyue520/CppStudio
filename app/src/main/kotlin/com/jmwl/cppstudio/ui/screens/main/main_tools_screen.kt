package com.jmwl.cppstudio.ui.screens.main

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jmwl.cppstudio.ui.components.sub_page_top_bar
import com.jmwl.cppstudio.ui.theme.app_colors
import com.jmwl.cppstudio.ui.theme.app_theme_provider

/**
 * 开发工具安装状态（由 main_activity.refresh_toolchain_status 填充）。
 *
 * C/C++ 工具链全部通过 apk 装进 proot Alpine rootfs：
 * - 编译器：build-base（gcc / g++ / make / musl-dev / binutils）
 * - 构建：cmake（+ pkgconf）
 * - 智能编辑：clangd（补全 / 诊断 / 跳转）
 * - 格式化：clang-format
 * - 调试：gdb
 * - 版本控制：git
 */
data class main_tools_install_status(
    val compiler_installed: Boolean = false,
    val compiler_version: String = "",
    val cmake_installed: Boolean = false,
    val cmake_version: String = "",
    val clangd_installed: Boolean = false,
    val clang_format_installed: Boolean = false,
    val gdb_installed: Boolean = false,
    val git_installed: Boolean = false,
    val installed_libraries: Int = 0
) {
    /** 一键安装状态：编译器 + CMake 都有才算就绪。 */
    val toolchain_ready: Boolean
        get() = compiler_installed && cmake_installed
}

@Composable
fun main_tools_screen(
    on_back: () -> Unit,
    on_install_cpp_toolchain: () -> Unit = {},
    on_install_build_base: () -> Unit = {},
    on_install_cmake: () -> Unit = {},
    on_install_clangd: () -> Unit = {},
    on_install_clang_format: () -> Unit = {},
    on_install_gdb: () -> Unit = {},
    on_install_git: () -> Unit = {},
    on_open_libraries: () -> Unit = {},
    install_status: main_tools_install_status = main_tools_install_status()
) {
    val colors = app_theme_provider.colors

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = Color.Transparent,
        topBar = { sub_page_top_bar("开发工具", on_back) }
    ) { padding_values ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding_values)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = "管理 C/C++ 编译器、构建系统、语言服务器与调试工具",
                fontSize = 14.sp,
                fontWeight = FontWeight.Light,
                color = colors.subtitle,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp)
            )

            Spacer(modifier = Modifier.height(18.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp)
                    .padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                main_tools_group_title("核心组件")

                main_tools_tool_card(
                    icon = Icons.Default.Terminal,
                    title = "C/C++ 工具链",
                    description = "build-base + cmake + clangd + gdb + git（一键装好开箱即用）",
                    status_text = when {
                        install_status.toolchain_ready && install_status.compiler_version.isNotBlank() ->
                            "已就绪 · gcc ${install_status.compiler_version}"
                        install_status.toolchain_ready -> "已就绪"
                        else -> "未安装"
                    },
                    installed = install_status.toolchain_ready,
                    install_text = if (install_status.toolchain_ready) "重装工具链" else "一键安装",
                    on_install = on_install_cpp_toolchain
                )

                main_tools_tool_card(
                    icon = Icons.Default.Build,
                    title = "编译器（build-base）",
                    description = "gcc / g++ / make / musl 头文件 + binutils",
                    status_text = if (install_status.compiler_installed) {
                        if (install_status.compiler_version.isNotBlank()) "已安装 · ${install_status.compiler_version}"
                        else "已安装"
                    } else "未安装",
                    installed = install_status.compiler_installed,
                    install_text = if (install_status.compiler_installed) "重装 build-base" else "安装 build-base",
                    on_install = on_install_build_base
                )

                main_tools_tool_card(
                    icon = Icons.Default.AccountTree,
                    title = "CMake",
                    description = "CMake + pkgconf：配置、生成编译数据库、构建",
                    status_text = if (install_status.cmake_installed) {
                        if (install_status.cmake_version.isNotBlank()) "已安装 · ${install_status.cmake_version}"
                        else "已安装"
                    } else "未安装",
                    installed = install_status.cmake_installed,
                    install_text = if (install_status.cmake_installed) "重装 CMake" else "安装 CMake",
                    on_install = on_install_cmake
                )

                main_tools_group_title("语言服务与调试")

                main_tools_tool_card(
                    icon = Icons.Default.AutoAwesome,
                    title = "clangd",
                    description = "C/C++ 语言服务器：补全、诊断、跳转、悬浮提示",
                    status_text = if (install_status.clangd_installed) "已安装" else "未安装",
                    installed = install_status.clangd_installed,
                    install_text = if (install_status.clangd_installed) "重装 clangd" else "安装 clangd",
                    on_install = on_install_clangd
                )

                main_tools_tool_card(
                    icon = Icons.Default.FormatAlignLeft,
                    title = "clang-format",
                    description = "按项目 .clang-format 一键格式化代码",
                    status_text = if (install_status.clang_format_installed) "已安装" else "未安装",
                    installed = install_status.clang_format_installed,
                    install_text = if (install_status.clang_format_installed) "重装 clang-format" else "安装 clang-format",
                    on_install = on_install_clang_format
                )

                main_tools_tool_card(
                    icon = Icons.Default.PestControl,
                    title = "gdb",
                    description = "命令行调试器：断点、单步、backtrace（在终端里用）",
                    status_text = if (install_status.gdb_installed) "已安装" else "未安装",
                    installed = install_status.gdb_installed,
                    install_text = if (install_status.gdb_installed) "重装 gdb" else "安装 gdb",
                    on_install = on_install_gdb
                )

                main_tools_group_title("开发工具")

                main_tools_tool_card(
                    icon = Icons.Default.CallSplit,
                    title = "git",
                    description = "版本控制：克隆仓库、提交、分支管理",
                    status_text = if (install_status.git_installed) "已安装" else "未安装",
                    installed = install_status.git_installed,
                    install_text = if (install_status.git_installed) "重装 git" else "安装 git",
                    on_install = on_install_git
                )

                // 库管理入口（CppStudio 的差异化能力）
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { on_open_libraries() },
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = colors.card_bg),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        main_tools_icon_box(Icons.Default.Extension, "库管理", colors)
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text = "库管理",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = colors.card_text_title
                            )
                            Text(
                                text = "fmt / spdlog / boost / sdl2 / opengl 等常用库一键安装，自动给出 CMake 接法",
                                fontSize = 10.sp,
                                lineHeight = 12.sp,
                                color = colors.card_text_subtitle
                            )
                        }
                        Text(
                            text = "${install_status.installed_libraries} 已装",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            color = colors.dialog_clone_bg
                        )
                        Icon(
                            Icons.Default.ChevronRight,
                            null,
                            tint = colors.card_chevron,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                main_tools_info_card(
                    icon = Icons.Default.Info,
                    title = "安装说明",
                    lines = listOf(
                        "所有工具通过 apk 安装到 Alpine rootfs（/usr/bin）",
                        "首次安装会测速国内镜像源并自动配置 apk 源",
                        "第三方库在「库管理」里装，装完 CMake 即可 find_package"
                    )
                )
            }
        }
    }
}

/** 单个工具卡片：图标 + 标题 + 描述 + 状态 + 安装按钮。 */
@Composable
private fun main_tools_tool_card(
    icon: ImageVector,
    title: String,
    description: String,
    status_text: String,
    installed: Boolean,
    install_text: String,
    on_install: () -> Unit
) {
    val colors = app_theme_provider.colors

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = colors.card_bg),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                main_tools_icon_box(icon = icon, title = title, colors = colors)

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = title,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.card_text_title
                        )
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = if (installed) colors.dialog_clone_bg.copy(alpha = 0.18f) else colors.card_text_subtitle.copy(alpha = 0.12f)
                        ) {
                            Text(
                                text = status_text,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Medium,
                                color = if (installed) colors.dialog_clone_bg else colors.card_text_subtitle,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Text(
                        text = description,
                        fontSize = 10.sp,
                        lineHeight = 12.sp,
                        fontWeight = FontWeight.Light,
                        color = colors.card_text_subtitle
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = on_install,
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.dialog_clone_bg,
                        contentColor = colors.dialog_clone_text
                    ),
                    contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
                    modifier = Modifier.height(34.dp)
                ) {
                    Icon(
                        Icons.Default.Download,
                        contentDescription = null,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = install_text,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun main_tools_info_card(
    icon: ImageVector,
    title: String,
    lines: List<String>
) {
    val colors = app_theme_provider.colors

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = colors.card_bg),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            main_tools_icon_box(icon = icon, title = title, colors = colors)

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    text = title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.card_text_title
                )
                lines.forEach { line ->
                    Text(
                        text = line,
                        fontSize = 10.sp,
                        lineHeight = 12.sp,
                        fontWeight = FontWeight.Light,
                        color = colors.card_text_subtitle,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun main_tools_icon_box(
    icon: ImageVector,
    title: String,
    colors: app_colors
) {
    Box(
        modifier = Modifier
            .size(28.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(colors.card_icon_bg.copy(alpha = 0.15f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            icon,
            contentDescription = title,
            tint = colors.card_icon_bg,
            modifier = Modifier.size(16.dp)
        )
    }
}

@Composable
private fun main_tools_group_title(title: String) {
    val colors = app_theme_provider.colors
    Text(
        text = title,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        color = colors.title_highlight,
        modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 2.dp)
    )
}
