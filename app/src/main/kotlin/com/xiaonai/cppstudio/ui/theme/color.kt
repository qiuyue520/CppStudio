package com.xiaonai.cppstudio.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * CppStudio 配色（浅紫 / lavender）。
 *
 * 与 GoStudio 的青色系区别：强调色是一支柔和的浅紫（#A78BFA 深色模式 / #7C5CE0 浅色模式），
 * 中性灰统一向紫色偏（hue ~285°），因此背景不是纯黑/纯灰，而是带一点紫的冷灰。
 * 三阶表面（paper / surface / raised），1px 分隔线，无渐变无阴影，圆角 8–16。
 *
 * 对比度：浅紫比青色亮，所以「强调底上的文字」在深色模式改用深墨色（#17141F）而不是白色，
 * 保证 4.5:1 以上；浅色模式仍用白色文字。
 */
data class app_colors(
    // 渐变 & 背景
    val gradient_start: Color,
    val gradient_middle: Color,
    val gradient_end: Color,

    // 文字
    val title_large: Color,
    val title_highlight: Color,
    val subtitle: Color,
    val section_title: Color,

    // 卡片
    val card_bg: Color,
    val card_pressed: Color,
    val card_text_title: Color,
    val card_text_subtitle: Color,
    val card_icon_bg: Color,
    val card_chevron: Color,

    // 顶部栏 & Logo
    val logo_tint: Color,
    val top_button_bg: Color,
    val top_button_icon: Color,

    // 搜索 & 输入框
    val search_button_active: Color,
    val search_button_bg_active: Color,
    val input_hint: Color,
    val input_text: Color,
    val input_border: Color,

    // 弹窗
    val dialog_bg: Color,
    val dialog_text: Color,
    val dialog_hint: Color,
    val dialog_icon: Color,
    val dialog_cancel: Color,
    val dialog_clone_bg: Color,
    val dialog_clone_text: Color,
    val dialog_card_bg: Color,
    val dialog_input_bg: Color,
    val dialog_input_text: Color,
    val dialog_input_hint: Color,
    val dialog_input_border: Color,
    val dialog_input_icon: Color,
    val dialog_input_icon_hint: Color,

    // 状态色
    val danger: Color,
    val danger_bg: Color,
    val success: Color,
    val success_bg: Color,
    val warning: Color,
    val warning_bg: Color,
    val info: Color,
    val info_bg: Color,

    // 终端
    val terminal_cursor: Int,
    val terminal_foreground: Int,
    val terminal_background: Int,
    val key_button_pressed_bg: Color,
    val key_button_pressed_text: Color,
    val key_button_normal_text: Color,
    val key_button_active_text: Color,
    val terminal_tab_add_icon: Color,
    val terminal_tab_separator: Color,
    val terminal_tab_selected_bg: Color,
    val terminal_tab_unselected_bg: Color,
    val terminal_tab_selected_icon: Color,
    val terminal_tab_selected_text: Color,
    val terminal_tab_unselected_content: Color,

    // 编辑器
    val editor_bg: Color,
    val editor_text: Color,
    val editor_hint: Color,
    val editor_icon: Color,
    val editor_toolbar_icon: Color,
    val editor_panel_overlay: Color,
    val editor_button_bg: Color,
    val editor_tab_add_icon: Color,
    val editor_tab_separator: Color,
    val editor_tab_selected_bg: Color,
    val editor_tab_unselected_bg: Color,
    val editor_tab_selected_icon: Color,
    val editor_tab_selected_text: Color,
    val editor_tab_unselected_content: Color,
    val editor_sidebar_selected_bg: Color,
    val editor_divider: Color,
    val editor_line_divider: Color
)

// ===== 深色模式（Lavender Dark） =====
// paper #16141B · surface #1F1C28 · raised #262231 · editor #1A1721
// ink #EDEAF4 · ink-2 #ADA6BC · muted #7E778E · accent #A78BFA · 分隔线 白 8%/14%/7%
val dark_app_colors = app_colors(
    // ===== 背景（纯色，无渐变） =====
    gradient_start = Color(0xFF16141B),
    gradient_middle = Color(0xFF16141B),
    gradient_end = Color(0xFF16141B),

    // ===== 文字 =====
    title_large = Color(0xFFEDEAF4),
    title_highlight = Color(0xFFA78BFA),
    subtitle = Color(0xFFADA6BC),
    section_title = Color(0xFFADA6BC),

    // ===== 卡片 =====
    card_bg = Color(0xFF1F1C28),
    card_pressed = Color(0xFF2C2739),
    card_text_title = Color(0xFFEDEAF4),
    card_text_subtitle = Color(0xFFADA6BC),
    card_icon_bg = Color(0xFFA78BFA),
    card_chevron = Color(0xFF7E778E),

    // ===== 顶部栏 & Logo =====
    logo_tint = Color(0xFFEDEAF4),
    top_button_bg = Color(0xFF262231),
    top_button_icon = Color(0xFFADA6BC),

    // ===== 搜索 & 输入框 =====
    search_button_active = Color(0xFFA78BFA),
    search_button_bg_active = Color(0x29A78BFA),
    input_hint = Color(0xFFADA6BC),
    input_text = Color(0xFFEDEAF4),
    input_border = Color(0xFFA78BFA),

    // ===== 弹窗 =====
    dialog_bg = Color(0xFF262231),
    dialog_text = Color(0xFFEDEAF4),
    dialog_hint = Color(0xFFADA6BC),
    dialog_icon = Color(0xFFA78BFA),
    dialog_cancel = Color(0xFFA78BFA),
    dialog_clone_bg = Color(0xFFA78BFA),
    // 浅紫底上用深墨色文字（白字对比度不足）
    dialog_clone_text = Color(0xFF17141F),
    dialog_card_bg = Color(0xFF16141B),
    dialog_input_bg = Color(0xFF16141B),
    dialog_input_text = Color(0xFFEDEAF4),
    dialog_input_hint = Color(0xFFADA6BC),
    dialog_input_border = Color(0xFFA78BFA),
    dialog_input_icon = Color(0xFFA78BFA),
    dialog_input_icon_hint = Color(0xFFADA6BC),

    // ===== 状态色 =====
    danger = Color(0xFFFF6B81),
    danger_bg = Color(0x29FF6B81),
    success = Color(0xFF3ED598),
    success_bg = Color(0x293ED598),
    warning = Color(0xFFFFC15E),
    warning_bg = Color(0x29FFC15E),
    info = Color(0xFF8FB6FF),
    info_bg = Color(0x298FB6FF),

    // ===== 终端 =====
    terminal_cursor = 0xFFA78BFA.toInt(),
    terminal_foreground = 0xFFEDEAF4.toInt(),
    terminal_background = 0xFF16141B.toInt(),
    key_button_pressed_bg = Color(0xFF2C2739),
    key_button_pressed_text = Color(0xFFFFFFFF),
    key_button_normal_text = Color(0xFFADA6BC),
    key_button_active_text = Color(0xFFA78BFA),
    terminal_tab_add_icon = Color(0xFFEDEAF4),
    terminal_tab_separator = Color(0x17FFFFFF),
    terminal_tab_selected_bg = Color(0x00000000),
    terminal_tab_unselected_bg = Color(0xFF1F1C28),
    terminal_tab_selected_icon = Color(0xFFA78BFA),
    terminal_tab_selected_text = Color(0xFFEDEAF4),
    terminal_tab_unselected_content = Color(0xFFADA6BC),

    // ===== 编辑器 =====
    editor_bg = Color(0xFF1A1721),
    editor_text = Color(0xFFEDEAF4),
    editor_hint = Color(0xFFADA6BC),
    editor_icon = Color(0xFFA78BFA),
    editor_toolbar_icon = Color(0xFFADA6BC),
    editor_panel_overlay = Color(0xD116141B),
    editor_button_bg = Color(0xFF262231),
    editor_tab_add_icon = Color(0xFFEDEAF4),
    editor_tab_separator = Color(0x17FFFFFF),
    editor_tab_selected_bg = Color(0x00000000),
    editor_tab_unselected_bg = Color(0xFF1F1C28),
    editor_tab_selected_icon = Color(0xFFA78BFA),
    editor_tab_selected_text = Color(0xFFEDEAF4),
    editor_tab_unselected_content = Color(0xFFADA6BC),
    editor_sidebar_selected_bg = Color(0x29A78BFA),
    editor_divider = Color(0x1FFFFFFF),
    editor_line_divider = Color(0x12FFFFFF)
)

// ===== 浅色模式（Lavender Light） =====
// paper #F7F5FB · surface #FFFFFF · raised #F0ECF9 · editor #FBFAFE
// ink #1C1A24 · ink-2 #4C4759 · muted #7C7688 · accent #7C5CE0 · 分隔线 黑 8%/14%/6%
val light_app_colors = app_colors(
    // ===== 背景（纯色，无渐变） =====
    gradient_start = Color(0xFFF7F5FB),
    gradient_middle = Color(0xFFF7F5FB),
    gradient_end = Color(0xFFF7F5FB),

    // ===== 文字 =====
    title_large = Color(0xFF1C1A24),
    title_highlight = Color(0xFF7C5CE0),
    subtitle = Color(0xFF4C4759),
    section_title = Color(0xFF4C4759),

    // ===== 卡片 =====
    card_bg = Color(0xFFFFFFFF),
    card_pressed = Color(0xFFF0ECF9),
    card_text_title = Color(0xFF1C1A24),
    card_text_subtitle = Color(0xFF4C4759),
    card_icon_bg = Color(0xFF7C5CE0),
    card_chevron = Color(0xFF7C7688),

    // ===== 顶部栏 & Logo =====
    logo_tint = Color(0xFF1C1A24),
    top_button_bg = Color(0xFFF0ECF9),
    top_button_icon = Color(0xFF4C4759),

    // ===== 搜索 & 输入框 =====
    search_button_active = Color(0xFF7C5CE0),
    search_button_bg_active = Color(0x267C5CE0),
    input_hint = Color(0xFF4C4759),
    input_text = Color(0xFF1C1A24),
    input_border = Color(0xFF7C5CE0),

    // ===== 弹窗 =====
    dialog_bg = Color(0xFFFDFCFF),
    dialog_text = Color(0xFF1C1A24),
    dialog_hint = Color(0xFF4C4759),
    dialog_icon = Color(0xFF7C5CE0),
    dialog_cancel = Color(0xFF7C5CE0),
    dialog_clone_bg = Color(0xFF7C5CE0),
    dialog_clone_text = Color(0xFFFFFFFF),
    dialog_card_bg = Color(0xFFF7F5FB),
    dialog_input_bg = Color(0xFFFFFFFF),
    dialog_input_text = Color(0xFF1C1A24),
    dialog_input_hint = Color(0xFF4C4759),
    dialog_input_border = Color(0xFF7C5CE0),
    dialog_input_icon = Color(0xFF7C5CE0),
    dialog_input_icon_hint = Color(0xFF4C4759),

    // ===== 状态色 =====
    danger = Color(0xFFD63A5A),
    danger_bg = Color(0x1FD63A5A),
    success = Color(0xFF14875C),
    success_bg = Color(0x1F14875C),
    warning = Color(0xFFB87503),
    warning_bg = Color(0x1FB87503),
    info = Color(0xFF3D6BD6),
    info_bg = Color(0x1F3D6BD6),

    // ===== 终端 =====
    terminal_cursor = 0xFF7C5CE0.toInt(),
    terminal_foreground = 0xFF1C1A24.toInt(),
    terminal_background = 0xFFFBFAFE.toInt(),
    key_button_pressed_bg = Color(0xFFF0ECF9),
    key_button_pressed_text = Color(0xFF1C1A24),
    key_button_normal_text = Color(0xFF4C4759),
    key_button_active_text = Color(0xFF7C5CE0),
    terminal_tab_add_icon = Color(0xFF1C1A24),
    terminal_tab_separator = Color(0x17000000),
    terminal_tab_selected_bg = Color(0x00000000),
    terminal_tab_unselected_bg = Color(0xFFF0ECF9),
    terminal_tab_selected_icon = Color(0xFF7C5CE0),
    terminal_tab_selected_text = Color(0xFF1C1A24),
    terminal_tab_unselected_content = Color(0xFF4C4759),

    // ===== 编辑器 =====
    editor_bg = Color(0xFFFBFAFE),
    editor_text = Color(0xFF1C1A24),
    editor_hint = Color(0xFF4C4759),
    editor_icon = Color(0xFF7C5CE0),
    editor_toolbar_icon = Color(0xFF4C4759),
    editor_panel_overlay = Color(0xD1F7F5FB),
    editor_button_bg = Color(0xFFF0ECF9),
    editor_tab_add_icon = Color(0xFF1C1A24),
    editor_tab_separator = Color(0x17000000),
    editor_tab_selected_bg = Color(0x00000000),
    editor_tab_unselected_bg = Color(0xFFF0ECF9),
    editor_tab_selected_icon = Color(0xFF7C5CE0),
    editor_tab_selected_text = Color(0xFF1C1A24),
    editor_tab_unselected_content = Color(0xFF4C4759),
    editor_sidebar_selected_bg = Color(0x267C5CE0),
    editor_divider = Color(0x1F000000),
    editor_line_divider = Color(0x0F000000)
)
