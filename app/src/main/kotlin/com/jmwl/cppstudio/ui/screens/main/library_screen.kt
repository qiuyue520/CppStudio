package com.jmwl.cppstudio.ui.screens.main

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jmwl.cppstudio.libraries.library_catalog
import com.jmwl.cppstudio.libraries.library_categories
import com.jmwl.cppstudio.libraries.library_entry
import com.jmwl.cppstudio.libraries.library_manager
import com.jmwl.cppstudio.libraries.repository_package
import com.jmwl.cppstudio.ui.components.sub_page_top_bar
import com.jmwl.cppstudio.ui.dialogs.common.install_progress_dialog
import com.jmwl.cppstudio.ui.theme.app_theme_provider
import kotlinx.coroutines.launch

/**
 * 库管理页（CppStudio 相对 GoStudio 的核心增量）。
 *
 * 三个能力：
 * 1. 精选库一键安装（[library_catalog]，按分类筛选 + 关键字搜索）
 * 2. 已安装状态与版本实时显示（解析 apk 包数据库，不起进程）
 * 3. 仓库搜索：直接 `apk search` 查 Alpine 仓库，装任意包（不受精选目录限制）
 */
@Composable
fun library_screen(on_back: () -> Unit) {
    val colors = app_theme_provider.colors
    val scope = rememberCoroutineScope()

    var installed_map by remember { mutableStateOf(library_manager.installed()) }
    var category by remember { mutableStateOf(library_categories.all) }
    var keyword by remember { mutableStateOf("") }
    var installed_only by remember { mutableStateOf(false) }
    var repository_mode by remember { mutableStateOf(false) }

    // 仓库搜索状态
    var repo_query by remember { mutableStateOf("") }
    var repo_results by remember { mutableStateOf(listOf<repository_package>()) }
    var repo_searched by remember { mutableStateOf(false) }
    var repo_logs by remember { mutableStateOf(listOf<String>()) }
    var repo_busy by remember { mutableStateOf(false) }

    // 详情 / 安装对话框状态
    var detail_entry by remember { mutableStateOf<library_entry?>(null) }
    var pending_install by remember { mutableStateOf<library_entry?>(null) }
    var pending_uninstall by remember { mutableStateOf<library_entry?>(null) }
    var pending_package_install by remember { mutableStateOf<repository_package?>(null) }

    fun refresh_state() {
        installed_map = library_manager.installed()
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = Color.Transparent,
        topBar = { sub_page_top_bar("库管理", on_back) }
    ) { padding_values ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding_values)
        ) {
            // 模式切换
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                library_mode_tab("精选库", !repository_mode) { repository_mode = false }
                library_mode_tab("仓库搜索", repository_mode) { repository_mode = true }
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (!repository_mode) {
                Text(
                    text = "已安装 ${library_manager.installed_count(installed_map)} / ${library_catalog.entries.size} 个精选库 · 点开卡片看 CMake 接法",
                    fontSize = 11.sp,
                    color = colors.subtitle,
                    modifier = Modifier.padding(horizontal = 18.dp)
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = keyword,
                    onValueChange = { keyword = it },
                    singleLine = true,
                    placeholder = { Text("搜索库名 / 用途，例如 json、http、gui", fontSize = 13.sp) },
                    leadingIcon = { Icon(Icons.Default.Search, null, tint = colors.card_text_subtitle, modifier = Modifier.size(18.dp)) },
                    trailingIcon = {
                        if (keyword.isNotEmpty()) {
                            IconButton(onClick = { keyword = "" }) {
                                Icon(Icons.Default.Close, "清除", tint = colors.card_text_subtitle, modifier = Modifier.size(16.dp))
                            }
                        }
                    },
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = colors.dialog_clone_bg,
                        unfocusedBorderColor = colors.dialog_hint.copy(alpha = 0.4f),
                        focusedTextColor = colors.input_text,
                        unfocusedTextColor = colors.input_text,
                        cursorColor = colors.dialog_clone_bg,
                        focusedContainerColor = colors.card_bg,
                        unfocusedContainerColor = colors.card_bg
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp)
                )

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 18.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = installed_only,
                        onClick = { installed_only = !installed_only },
                        label = { Text("只看已安装", fontSize = 11.sp) },
                        leadingIcon = {
                            if (installed_only) {
                                Icon(Icons.Default.Check, null, modifier = Modifier.size(13.dp))
                            }
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = colors.card_bg,
                            labelColor = colors.card_text_subtitle,
                            selectedContainerColor = colors.success.copy(alpha = 0.2f),
                            selectedLabelColor = colors.success
                        )
                    )
                    library_categories.ordered.forEach { candidate ->
                        FilterChip(
                            selected = category == candidate,
                            onClick = { category = candidate },
                            label = { Text(candidate, fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = colors.card_bg,
                                labelColor = colors.card_text_subtitle,
                                selectedContainerColor = colors.dialog_clone_bg,
                                selectedLabelColor = colors.dialog_clone_text
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                var visible = if (keyword.isBlank()) {
                    library_catalog.by_category(category)
                } else {
                    library_catalog.search(keyword)
                }
                if (installed_only) {
                    visible = visible.filter { library_manager.is_installed(it, installed_map) }
                }

                if (visible.isEmpty()) {
                    library_empty_hint("没有匹配的库，去「仓库搜索」里按包名找找")
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 18.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(bottom = 24.dp)
                    ) {
                        items(visible, key = { it.id }) { entry ->
                            library_card(
                                entry = entry,
                                installed_version = library_manager.installed_version(entry, installed_map),
                                on_open_detail = { detail_entry = entry },
                                on_install = { pending_install = entry },
                                on_uninstall = { pending_uninstall = entry }
                            )
                        }
                    }
                }
            } else {
                Text(
                    text = "直接在 Alpine 仓库里搜索：apk search 的结果，未收录进精选目录也能装",
                    fontSize = 11.sp,
                    color = colors.subtitle,
                    modifier = Modifier.padding(horizontal = 18.dp)
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = repo_query,
                    onValueChange = { repo_query = it },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = {
                        if (!repo_busy && repo_query.isNotBlank()) {
                            repo_busy = true
                            repo_logs = emptyList()
                            repo_searched = true
                            scope.launch {
                                val results = library_manager.search_repository(repo_query) { line ->
                                    repo_logs = repo_logs + line
                                }
                                repo_results = results
                                repo_busy = false
                            }
                        }
                    }),
                    placeholder = { Text("包名关键字，例如 fmt、sdl、boost", fontSize = 13.sp) },
                    leadingIcon = { Icon(Icons.Default.Search, null, tint = colors.card_text_subtitle, modifier = Modifier.size(18.dp)) },
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = colors.dialog_clone_bg,
                        unfocusedBorderColor = colors.dialog_hint.copy(alpha = 0.4f),
                        focusedTextColor = colors.input_text,
                        unfocusedTextColor = colors.input_text,
                        cursorColor = colors.dialog_clone_bg,
                        focusedContainerColor = colors.card_bg,
                        unfocusedContainerColor = colors.card_bg
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp)
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            if (!repo_busy && repo_query.isNotBlank()) {
                                repo_busy = true
                                repo_logs = emptyList()
                                repo_searched = true
                                scope.launch {
                                    val results = library_manager.search_repository(repo_query) { line ->
                                        repo_logs = repo_logs + line
                                    }
                                    repo_results = results
                                    repo_busy = false
                                }
                            }
                        },
                        enabled = !repo_busy && repo_query.isNotBlank(),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.dialog_clone_bg,
                            contentColor = colors.dialog_clone_text
                        ),
                        modifier = Modifier.height(34.dp)
                    ) {
                        Icon(Icons.Default.Search, null, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (repo_busy) "搜索中..." else "搜索仓库", fontSize = 12.sp)
                    }

                    OutlinedButton(
                        onClick = {
                            if (!repo_busy) {
                                repo_busy = true
                                repo_logs = emptyList()
                                scope.launch {
                                    library_manager.update_index { line -> repo_logs = repo_logs + line }
                                    repo_busy = false
                                }
                            }
                        },
                        enabled = !repo_busy,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.height(34.dp)
                    ) {
                        Icon(Icons.Default.Refresh, null, tint = colors.card_text_title, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("同步索引", fontSize = 12.sp, color = colors.card_text_title)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                if (repo_busy && repo_results.isEmpty()) {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 18.dp)
                            .height(3.dp)
                            .clip(RoundedCornerShape(2.dp)),
                        color = colors.dialog_clone_bg,
                        trackColor = colors.dialog_hint.copy(alpha = 0.25f)
                    )
                }

                if (repo_results.isEmpty()) {
                    if (repo_searched && !repo_busy) {
                        library_empty_hint(
                            if (repo_logs.any { it.contains("ERROR") || it.contains("失败") || it.contains("无法") }) {
                                "搜索失败：请先点「同步索引」下载仓库索引"
                            } else {
                                "没有匹配的包，换个关键字试试"
                            }
                        )
                    } else if (!repo_searched) {
                        library_empty_hint("输入关键字后搜索，例如 boost、qt、opencv")
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 18.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(bottom = 24.dp)
                    ) {
                        items(repo_results, key = { it.name }) { pkg ->
                            repository_package_row(
                                pkg = pkg,
                                installed_version = installed_map[pkg.name],
                                on_install = { pending_package_install = pkg }
                            )
                        }
                    }
                }
            }
        }
    }

    // ---------------- 详情弹层 ----------------
    detail_entry?.let { entry ->
        library_detail_sheet(
            entry = entry,
            installed_version = library_manager.installed_version(entry, installed_map),
            on_dismiss = { detail_entry = null },
            on_install = {
                detail_entry = null
                pending_install = entry
            },
            on_uninstall = {
                detail_entry = null
                pending_uninstall = entry
            }
        )
    }

    // ---------------- 安装 / 卸载进度 ----------------
    pending_install?.let { entry ->
        install_progress_dialog(
            title = "安装 ${entry.title}",
            task = { on_log, on_progress -> library_manager.install(entry, on_log, on_progress) },
            on_dismiss = { pending_install = null },
            on_success = {
                pending_install = null
                refresh_state()
            },
            auto_close_delay = 900
        )
    }

    pending_uninstall?.let { entry ->
        install_progress_dialog(
            title = "卸载 ${entry.title}",
            task = { on_log, on_progress -> library_manager.uninstall(entry, on_log, on_progress) },
            on_dismiss = { pending_uninstall = null },
            on_success = {
                pending_uninstall = null
                refresh_state()
            },
            auto_close_delay = 900
        )
    }

    pending_package_install?.let { pkg ->
        install_progress_dialog(
            title = "安装 ${pkg.name}",
            task = { on_log, on_progress -> library_manager.install_package(pkg.name, on_log, on_progress) },
            on_dismiss = { pending_package_install = null },
            on_success = {
                pending_package_install = null
                refresh_state()
            },
            auto_close_delay = 900
        )
    }
}

@Composable
private fun library_mode_tab(label: String, selected: Boolean, on_click: () -> Unit) {
    val colors = app_theme_provider.colors
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) colors.dialog_clone_bg else colors.card_bg)
            .clickable { on_click() }
            .padding(horizontal = 14.dp, vertical = 7.dp)
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) colors.dialog_clone_text else colors.card_text_subtitle
        )
    }
}

@Composable
private fun library_card(
    entry: library_entry,
    installed_version: String?,
    on_open_detail: () -> Unit,
    on_install: () -> Unit,
    on_uninstall: () -> Unit
) {
    val colors = app_theme_provider.colors
    val installed = installed_version != null

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { on_open_detail() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = colors.card_bg),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(colors.card_icon_bg.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Extension,
                        null,
                        tint = colors.card_icon_bg,
                        modifier = Modifier.size(17.dp)
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = entry.title,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.card_text_title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "apk: " + entry.packages.joinToString(" / "),
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace,
                        color = colors.card_text_subtitle,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (installed) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = colors.success.copy(alpha = 0.16f)
                    ) {
                        Text(
                            text = installed_version.ifBlank { "已安装" },
                            fontSize = 9.sp,
                            color = colors.success,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            Text(
                text = entry.summary,
                fontSize = 11.sp,
                lineHeight = 15.sp,
                color = colors.card_text_subtitle
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = entry.category,
                    fontSize = 9.sp,
                    color = colors.card_text_subtitle.copy(alpha = 0.8f)
                )

                if (installed) {
                    TextButton(
                        onClick = on_uninstall,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.DeleteOutline, null, tint = colors.danger, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("卸载", fontSize = 12.sp, color = colors.danger)
                    }
                } else {
                    Button(
                        onClick = on_install,
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.dialog_clone_bg,
                            contentColor = colors.dialog_clone_text
                        ),
                        contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
                        modifier = Modifier.height(32.dp)
                    ) {
                        Icon(Icons.Default.Download, null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(5.dp))
                        Text("安装", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
    }
}

@Composable
private fun repository_package_row(
    pkg: repository_package,
    installed_version: String?,
    on_install: () -> Unit
) {
    val colors = app_theme_provider.colors
    val installed = installed_version != null

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(colors.card_bg)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = pkg.name,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Medium,
                color = colors.card_text_title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (pkg.description.isNotBlank()) {
                Text(
                    text = pkg.description,
                    fontSize = 10.sp,
                    color = colors.card_text_subtitle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            } else {
                Text(
                    text = pkg.version,
                    fontSize = 10.sp,
                    color = colors.card_text_subtitle,
                    maxLines = 1
                )
            }
        }

        if (installed) {
            Text(
                text = "已安装",
                fontSize = 10.sp,
                color = colors.success,
                fontWeight = FontWeight.Medium
            )
        } else {
            IconButton(onClick = on_install, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Default.Download, "安装 ${pkg.name}", tint = colors.dialog_clone_bg, modifier = Modifier.size(17.dp))
            }
        }
    }
}

@Composable
private fun library_empty_hint(text: String) {
    val colors = app_theme_provider.colors
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        Text(
            text = text,
            fontSize = 12.sp,
            color = colors.card_text_subtitle,
            modifier = Modifier.padding(top = 24.dp)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun library_detail_sheet(
    entry: library_entry,
    installed_version: String?,
    on_dismiss: () -> Unit,
    on_install: () -> Unit,
    on_uninstall: () -> Unit
) {
    val colors = app_theme_provider.colors
    val installed = installed_version != null
    val sheet_state = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = on_dismiss,
        sheetState = sheet_state,
        containerColor = colors.dialog_bg,
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = entry.title,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = colors.dialog_text
            )
            Text(
                text = entry.summary,
                fontSize = 12.sp,
                lineHeight = 17.sp,
                color = colors.dialog_hint
            )

            library_detail_label("安装包（候选）")
            Text(
                text = entry.packages.joinToString("  ·  "),
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                color = colors.dialog_text
            )

            if (installed) {
                library_detail_label("已安装版本")
                Text(
                    text = installed_version.ifBlank { "已安装" },
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    color = colors.success
                )
            } else {
                library_detail_label("状态")
                Text("未安装", fontSize = 11.sp, color = colors.dialog_hint)
            }

            library_detail_label("在 CMakeLists.txt 里使用")
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(colors.dialog_card_bg)
                    .padding(10.dp)
            ) {
                Text(
                    text = entry.cmake_hint,
                    fontSize = 10.sp,
                    lineHeight = 15.sp,
                    fontFamily = FontFamily.Monospace,
                    color = colors.dialog_text
                )
            }

            if (entry.note.isNotBlank()) {
                library_detail_label("提示")
                Text(
                    text = entry.note,
                    fontSize = 11.sp,
                    color = colors.warning
                )
            }

            if (entry.homepage.isNotBlank()) {
                library_detail_label("主页")
                Text(
                    text = entry.homepage,
                    fontSize = 11.sp,
                    color = colors.dialog_clone_bg,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            if (installed) {
                Button(
                    onClick = on_uninstall,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.danger.copy(alpha = 0.16f),
                        contentColor = colors.danger
                    )
                ) {
                    Text("卸载 ${entry.title}", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                }
            } else {
                Button(
                    onClick = on_install,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.dialog_clone_bg,
                        contentColor = colors.dialog_clone_text
                    )
                ) {
                    Text("一键安装 ${entry.title}", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

@Composable
private fun library_detail_label(text: String) {
    val colors = app_theme_provider.colors
    Text(
        text = text,
        fontSize = 10.sp,
        fontWeight = FontWeight.SemiBold,
        color = colors.dialog_hint.copy(alpha = 0.9f)
    )
}
