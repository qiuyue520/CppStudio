package com.xiaonai.cppstudio.activity

import com.xiaonai.cppstudio.editor.session.editor_activity_state
import com.xiaonai.cppstudio.editor.session.editor_open_tab
import com.xiaonai.cppstudio.editor.session.editor_pending_action
import com.xiaonai.cppstudio.editor.tabs.find_dirty_closable_tab_index
import com.xiaonai.cppstudio.editor.tabs.find_dirty_tab_index
import com.xiaonai.cppstudio.editor.tabs.ordered_pinned_first_tabs
import com.xiaonai.cppstudio.editor.tabs.pinned_tab_paths
import com.xiaonai.cppstudio.editor.tabs.pinned_tabs
import com.xiaonai.cppstudio.editor.tabs.remaining_tabs_after_close_others
import com.xiaonai.cppstudio.editor.settings.*
import com.xiaonai.cppstudio.editor.settings.editor_session_tab
import com.xiaonai.cppstudio.editor.settings.load_editor_session
import com.xiaonai.cppstudio.editor.settings.save_editor_session
import com.xiaonai.cppstudio.editor.model.*
import com.xiaonai.cppstudio.editor.core.*

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import com.xiaonai.cppstudio.ui.dialogs.editor.editor_create_file_template
import com.xiaonai.cppstudio.ui.toast.app_toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import com.xiaonai.cppstudio.core.logging.logger_manager
import com.xiaonai.cppstudio.toolchain.proot_manager
import com.xiaonai.cppstudio.toolchain.toolchain_manager
import com.xiaonai.cppstudio.toolchain.toolchain_runtime_provider
import com.xiaonai.cppstudio.project.detected_project
import com.xiaonai.cppstudio.project.project_build_config
import com.xiaonai.cppstudio.project.project_detector
import com.xiaonai.cppstudio.project.project_ide_config
import com.xiaonai.cppstudio.project.project_kind
import com.xiaonai.cppstudio.project.project_manager
import com.xiaonai.cppstudio.lsp.clangd.clangd_lsp_config
import com.xiaonai.cppstudio.lsp.clangd.clangd_lsp_project
import com.xiaonai.cppstudio.lsp.clangd.translation.clangd_translation_settings
import com.xiaonai.cppstudio.lsp.clangd.apply_code_action
import com.xiaonai.cppstudio.lsp.clangd.current_diagnostics
import com.xiaonai.cppstudio.lsp.clangd.is_hover_available
import com.xiaonai.cppstudio.lsp.clangd.warm_up_analysis
import com.xiaonai.cppstudio.lsp.clangd.request_code_actions
import com.xiaonai.cppstudio.lsp.clangd.request_definition
import com.xiaonai.cppstudio.lsp.clangd.request_hover
import com.xiaonai.cppstudio.editor.theme.editor_theme_manager
import com.xiaonai.cppstudio.ui.dialogs.editor.editor_exit_confirm_dialog
import com.xiaonai.cppstudio.ui.dialogs.editor.editor_unsaved_file_dialog
import com.xiaonai.cppstudio.ui.screens.editor.*
import com.xiaonai.cppstudio.ui.theme.app_theme_provider
import com.xiaonai.cppstudio.ui.theme.motion
import com.xiaonai.cppstudio.cppstudio_application
import io.github.rosemoe.sora.text.Content
import io.github.rosemoe.sora.langs.textmate.TextMateLanguage
import io.github.rosemoe.sora.lsp.client.languageserver.LspFeature
import io.github.rosemoe.sora.lsp.editor.LspEditor
import io.github.rosemoe.sora.lsp.editor.LspLanguage
import io.github.rosemoe.sora.lsp.editor.LspEditorStatus
import io.github.rosemoe.sora.widget.CodeEditor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class editor_activity : ComponentActivity() {
    private lateinit var project_dir: File
    private lateinit var editor: CodeEditor
    private val state = editor_activity_state()
    private val output_panel_state = editor_output_panel_state()
    private lateinit var detected_project_info: detected_project
    private var applying_editor_content = false
    private var editor_recovery_job: Job? = null
    private var current_textmate_scope: String? = null
    private var block_hint_job: Job? = null
    private var build_job: Job? = null
    private var run_job: Job? = null
    private var compile_job: Job? = null
    private val compile_dialog_state = editor_compile_dialog_state()
    private var pending_export: Pair<File, String>? = null
    private var file_tree_job: Job? = null
    /** 克隆的项目打开后自动执行一次 CMake 配置（生成 compile_commands.json，见 initialize_project） */
    private var pending_auto_configure = false
    /** 项目文件监听：终端(proot)里 git pull / touch / rm 等外部变更自动刷新文件树与打开的 tab */
    private var project_watcher: editor_file_watcher? = null
    private var textmate_prewarm_started = false
    private var clangd_project: clangd_lsp_project? = null
    private var clangd_connect_job: Job? = null
    private var clangd_warmup_job: Job? = null
    private val clangd_skipped_files = mutableSetOf<String>()

    /** 跳转定义进行中（请求 clangd 时短暂置 true，避免重复点击） */
    private val _goto_in_progress = MutableStateFlow(false)
    val goto_in_progress: kotlinx.coroutines.flow.StateFlow<Boolean> = _goto_in_progress

    /** 当前文件代码结构符号（clangd 优先，正则兜底；见 refresh_structure_symbols） */
    private var structure_symbols by mutableStateOf(emptyList<com.xiaonai.cppstudio.editor.core.editor_outline_symbol>())
    private var structure_job: Job? = null

    /** 当前文件的 clangd 诊断（问题页签 + 诊断弹层数据源） */
    private var file_diagnostics by mutableStateOf(emptyList<com.xiaonai.cppstudio.lsp.clangd.clangd_diagnostic>())
    private var diagnostics_job: Job? = null
    /** 诊断详情弹层状态 */
    private var sheet_diagnostic by mutableStateOf<com.xiaonai.cppstudio.lsp.clangd.clangd_diagnostic?>(null)
    private var sheet_actions by mutableStateOf(emptyList<com.xiaonai.cppstudio.lsp.clangd.clangd_code_action>())
    private var sheet_actions_loading by mutableStateOf(false)
    private var sheet_actions_job: Job? = null
    private var hover_sheet_open by mutableStateOf(false)
    private var hover_detail by mutableStateOf<com.xiaonai.cppstudio.lsp.clangd.clangd_hover_detail?>(null)
    private var hover_detail_loading by mutableStateOf(false)
    private var hover_detail_error by mutableStateOf<String?>(null)
    private var hover_detail_job: Job? = null

    /** AI agent（在 project_dir 初始化后于 initialize_project 创建） */
    private var ai_agent: com.xiaonai.cppstudio.ai.ai_agent_loop? = null
    /** AI prompts 目录（供输入补全使用） */
    private var ai_global_prompts_dir: java.io.File? = null
    private var ai_project_prompts_dir: java.io.File? = null
    /** AI 弹窗打开触发器（编辑器选区 AI 动作时 +1） */
    private var ai_open_trigger by mutableStateOf(0)
    private var sidebar_log_open_trigger by mutableStateOf(0)
    private var sidebar_log_close_trigger by mutableStateOf(0)
    /** AI 文件变更通知器（工具改文件后刷新编辑器） */
    private var ai_file_change_notifier: com.xiaonai.cppstudio.ai.ai_file_change_notifier? = null
    /** AI 设置页覆盖层开关 */
    private var show_ai_settings by mutableStateOf(false)
    /** 会话级模型选择 override（null=跟随全局设置）。快照完整连接信息，支持多实例切换。 */
    private var _session_choice by mutableStateOf<com.xiaonai.cppstudio.ai.ai_model_choice?>(null)
    val session_choice: com.xiaonai.cppstudio.ai.ai_model_choice? get() = _session_choice

    /**
     * 以下函数会在每次 Compose 重组时被调用（光标每移动一次就重组一次），
     * 必须走内存缓存 [cached_ai_settings]；直接用 load_ai_settings 会在主线程反复做
     * EncryptedSharedPreferences/Keystore I/O，导致光标移动卡顿。
     */
    /** 当前生效的模型选择（会话 override 优先，否则从全局设置构造） */
    private fun current_ai_choice(): com.xiaonai.cppstudio.ai.ai_model_choice =
        _session_choice ?: com.xiaonai.cppstudio.ai.ai_model_choice.from_settings(
            com.xiaonai.cppstudio.ai.cached_ai_settings(this)
        )

    /** 已配置的提供商实例列表（模型切换面板数据源） */
    private fun current_ai_instances(): List<com.xiaonai.cppstudio.ai.provider_instance> =
        com.xiaonai.cppstudio.ai.cached_ai_settings(this).instances

    private val file_tree_children_cache = mutableMapOf<String, List<editor_file_node>>()
    private lateinit var search_controller: editor_search_controller
    private lateinit var tab_lifecycle: editor_tab_lifecycle
    private val import_editor_font_launcher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            import_editor_font_from_uri(uri)
        }
    }
    /** Android 9 及以下导出到公共 Download 需要写存储权限；授权后补做挂起的导出。 */
    private val storage_permission_launcher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val pending = pending_export
        pending_export = null
        if (pending == null) return@registerForActivityResult
        val (file, name) = pending
        if (granted) {
            export_compiled_binary_to_downloads(file, name)
        } else {
            compile_dialog_state.append(
                "未授予存储权限，已取消导出（文件仍保存在 ${file.absolutePath}）",
                editor_output_line_level.WARNING
            )
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val project_path = intent.getStringExtra("project_path") ?: ""
        val project_name = intent.getStringExtra("project_name")
            ?: File(project_path).name.ifBlank { "项目" }

        project_dir = File(project_path)
        pending_auto_configure = intent.getBooleanExtra("auto_tidy", false)
        state.project_name = project_name.ifBlank { project_dir.name.ifBlank { "项目" } }
        state.expanded_paths = setOf(project_dir.absolutePath)
        state.project_exists = project_dir.exists() && project_dir.isDirectory
        detected_project_info = project_detector.detect_project(project_dir.absolutePath)
        append_detected_project_log()
        state.editor_settings = load_editor_settings(this)
        tab_lifecycle = create_tab_lifecycle()
        editor = create_code_editor()
        prewarm_textmate_languages()
        search_controller = editor_search_controller(
            editor = editor,
            can_search = { state.current_file_path != null },
            can_replace = { state.current_file_path != null && !state.read_only }
        )

        enableEdgeToEdge()
        setContent {
            app_theme_provider {
                editor_activity_content()
            }
        }
        project_watcher = editor_file_watcher(lifecycleScope) { changed_files, tree_dirs ->
            handle_external_file_changes(changed_files, tree_dirs)
        }.apply { start() }
        initialize_project()
    }

    override fun onPause() {
        super.onPause()
        editor_recovery_job?.cancel()
        flush_editor_recovery_drafts()
    }

    override fun onStop() {
        super.onStop()
        persist_editor_session()
    }

    override fun onResume() {
        super.onResume()
        // 从全局设置改完编辑器配置返回后，重载让设置即时生效
        val latest = load_editor_settings(this)
        if (latest != state.editor_settings) {
            state.editor_settings = latest
            apply_editor_settings(latest)
        }
    }

    override fun onDestroy() {
        editor_recovery_job?.cancel()
        flush_editor_recovery_drafts()
        persist_editor_session()
        ai_agent?.shutdown() // 清理 MCP server 进程
        block_hint_job?.cancel()
        build_job?.cancel()
        run_job?.cancel()
        file_tree_job?.cancel()
        project_watcher?.stop()
        project_watcher = null
        clangd_connect_job?.cancel()
        clangd_project?.dispose()
        clangd_project = null
        val tab_editors = state.open_tabs.mapNotNull { tab -> tab.editor }.toSet()
        state.open_tabs.toList().forEach { tab -> release_tab_editor(tab) }
        if (::editor.isInitialized && editor !in tab_editors) {
            runCatching { editor.setText("") }
            runCatching { editor.release() }
        }
        super.onDestroy()
    }

    private fun append_detected_project_log() {
        when (detected_project_info.kind) {
            project_kind.CMAKE -> {
                detected_project_info.build_file_path?.let { path ->
                    output_panel_state.append_log("CMakeLists.txt: $path")
                }
                detected_project_info.build_dir?.let { path ->
                    output_panel_state.append_log("构建目录: $path")
                }
            }
            project_kind.MAKE -> {
                detected_project_info.build_file_path?.let { path ->
                    output_panel_state.append_log("Makefile: $path")
                }
            }
            project_kind.UNKNOWN -> {
                output_panel_state.append_log(
                    "未识别到项目（缺少 CMakeLists.txt / Makefile）",
                    editor_output_line_level.WARNING
                )
            }
        }
    }

    @Composable
    private fun editor_activity_content() {
        val colors = app_theme_provider.colors
        val ai_settings_visibility = remember { MutableTransitionState(false) }
        LaunchedEffect(show_ai_settings) {
            if (ai_settings_visibility.targetState != show_ai_settings) {
                ai_settings_visibility.targetState = show_ai_settings
            }
        }
        val ai_settings_exiting = ai_settings_visibility.currentState && !ai_settings_visibility.targetState

        BackHandler(enabled = true) {
            when {
                state.show_unsaved_dialog -> Unit
                state.show_exit_dialog -> state.show_exit_dialog = false
                else -> request_close_editor()
            }
        }

        LaunchedEffect(colors) {
            apply_colors_to_open_editors()
        }

        LaunchedEffect(Unit) {
            editor_theme_manager.version.collect {
                apply_colors_to_open_editors()
            }
        }

        LaunchedEffect(state.read_only, state.open_tabs.size) {
            open_editors().forEach { tab_editor -> tab_editor.isEditable = !state.read_only }
        }

        // 代码结构符号：打开文件时刷新（编辑中由 handle_editor_content_changed 防抖触发）
        LaunchedEffect(state.current_file_path) {
            refresh_structure_symbols()
            schedule_diagnostics_refresh()
        }

        editor_screen(
            project_name = state.project_name,
            project_root_path = project_dir.absolutePath,
            editor = editor,
            current_file_name = state.current_file_name,
            // 光标状态以 provider/StateFlow 下传，在叶子组件里读取，
            // 避免每次光标移动重组整个 editor_screen（见 editor_screen 注释）
            cursor_line_provider = { state.cursor_line },
            cursor_column_provider = { state.cursor_column },
            cursor_selected_provider = { state.cursor_selected },
            has_changes = state.has_changes,
            loading = state.loading,
            read_only = state.read_only,
            can_undo = state.can_undo,
            can_redo = state.can_redo,
            file_nodes = state.file_nodes,
            expanded_paths = state.expanded_paths,
            file_tree_loading = state.file_tree_loading,
            project_exists = state.project_exists,
            has_open_file = state.current_file_path != null,
            tabs = state.open_tabs.map { tab ->
                editor_tab_item(
                    path = tab.file_path,
                    title = tab.file_name,
                    has_changes = tab.has_changes,
                    pinned = tab.pinned
                )
            },
            selected_tab_path = state.current_file_path,
            toolbar_visible = state.toolbar_visible,
            output_panel_state = output_panel_state,
            terminal_cwd = project_dir.takeIf { it.isDirectory }?.absolutePath
                ?: toolchain_runtime_provider.paths().home_dir.absolutePath,
            terminal_extra_environment = if (project_dir.isDirectory) {
                toolchain_manager.project_environment(project_dir.absolutePath).environment
            } else {
                emptyMap()
            },
            on_toggle_toolbar = { state.toolbar_visible = !state.toolbar_visible },
            on_project_config_apply = { config, on_saved -> apply_project_config(config, on_saved) },
            on_project_config_compile = { config -> compile_project_from_config(config) },
            on_compile_cancel = { compile_job?.cancel() },
            compile_dialog_state = compile_dialog_state,
            on_select_tab = { path -> request_select_tab(path) },
            on_pin_tab = { path -> toggle_pin_tab(path) },
            on_close_tab = { path -> request_close_tab(path) },
            on_close_other_tabs = { path -> request_close_other_tabs(path) },
            on_close_all_tabs = { request_close_all_tabs() },
            on_build = { handle_build_button_click() },
            on_run = { run_cpp_project() },
            on_test = { run_tests() },
            on_configure = { configure_project() },
            on_save = { request_save_file() },
            on_format = { format_current_file() },
            on_toggle_call_args = { toggle_call_args_multiline() },
            on_toggle_read_only = { toggle_read_only() },
            on_undo = { undo() },
            on_redo = { redo() },
            on_search_change = { query, match_case, whole_word, regex ->
                update_search(query, match_case, whole_word, regex)
            },
            on_search_previous = { goto_search_result(forward = false) },
            on_search_next = { goto_search_result(forward = true) },
            on_replace_current = { replacement -> replace_current_match(replacement) },
            on_replace_all = { replacement -> replace_all_matches(replacement) },
            on_clear_search = { clear_search() },
            on_insert_symbol = { symbol -> insert_symbol(symbol) },
            on_create_file = { parent_path, name, template -> create_project_file(parent_path, name, template) },
            on_create_folder = { parent_path, name -> create_project_folder(parent_path, name) },
            on_refresh_files = { path -> refresh_file_tree(path) },
            on_rename_file_tree_node = { path, new_name -> rename_project_entry(path, new_name) },
            on_delete_file_tree_node = { path -> delete_project_entry(path) },
            on_directory_click = { path -> toggle_directory(path) },
            on_file_click = { path -> request_open_file(path) },
            on_file_position_click = { path, line, column -> open_file_at(path, line, column) },
            structure_file_name = state.current_file_path?.let { File(it).name },
            structure_symbols = structure_symbols,
            on_structure_navigate = { line -> navigate_to_structure_line(line) },
            on_project_search = { keyword -> search_project(keyword) },
            file_diagnostics = file_diagnostics,
            on_diagnostic_click = { diagnostic -> open_diagnostic_sheet(diagnostic) },
            ai_agent = ai_agent,
            on_open_ai_settings = { show_ai_settings = true },
            ai_current_choice = current_ai_choice(),
            ai_instances = current_ai_instances(),
            on_ai_model_choice = { choice -> _session_choice = choice },
            ai_global_prompts_dir = ai_global_prompts_dir,
            ai_project_prompts_dir = ai_project_prompts_dir,
            ai_open_trigger = ai_open_trigger,
            sidebar_log_open_trigger = sidebar_log_open_trigger,
            sidebar_log_close_trigger = sidebar_log_close_trigger,
            ai_settings_visible = show_ai_settings || ai_settings_exiting
        )

        AnimatedVisibility(
            visibleState = ai_settings_visibility,
            enter = slideInHorizontally(tween(motion.BASE, easing = motion.quiet), initialOffsetX = { it }) +
                fadeIn(tween(motion.BASE, easing = motion.soft)),
            exit = slideOutHorizontally(tween(motion.BASE, easing = motion.quiet), targetOffsetX = { it }) +
                fadeOut(tween(motion.BASE, easing = motion.soft))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(colors.editor_bg)
            ) {
                com.xiaonai.cppstudio.ui.screens.ai.ai_settings_screen(
                    initial = com.xiaonai.cppstudio.ai.load_ai_settings(this@editor_activity),
                    on_back = { show_ai_settings = false },
                    on_save = { new_settings ->
                        com.xiaonai.cppstudio.ai.save_ai_settings(this@editor_activity, new_settings)
                        show_ai_settings = false
                    },
                    on_change = { new_settings ->
                        com.xiaonai.cppstudio.ai.save_ai_settings(this@editor_activity, new_settings)
                    },
                    project_dir = project_dir
                )
            }
        }

        // 设置页退出动画期间继续拦截返回键，避免第二次返回把下层 AI 页一起关掉。
        BackHandler(enabled = show_ai_settings || ai_settings_exiting) {
            if (show_ai_settings) show_ai_settings = false
        }

        // 诊断详情弹层（问题页签点击条目打开）
        sheet_diagnostic?.let { diagnostic ->
            BackHandler { sheet_diagnostic = null }
            com.xiaonai.cppstudio.ui.screens.editor.editor_diagnostic_sheet(
                diagnostic = diagnostic,
                actions = sheet_actions,
                actions_loading = sheet_actions_loading,
                on_apply_action = { action -> apply_diagnostic_action(action) },
                on_dismiss = { sheet_diagnostic = null }
            )
        }

        // 符号详情弹层（长按工具栏「详情」打开）
        if (hover_sheet_open) {
            BackHandler { hover_sheet_open = false }
            com.xiaonai.cppstudio.ui.screens.editor.editor_hover_detail_sheet(
                detail = hover_detail,
                loading = hover_detail_loading,
                error = hover_detail_error,
                on_dismiss = { hover_sheet_open = false }
            )
        }

        if (state.show_exit_dialog) {
            editor_exit_confirm_dialog(
                on_confirm = { confirm_close_editor() },
                on_cancel = { state.show_exit_dialog = false }
            )
        }

        if (state.show_unsaved_dialog) {
            editor_unsaved_file_dialog(
                file_name = state.current_file_name,
                on_save = { save_pending_action() },
                on_discard = { discard_pending_action() }
            )
        }
    }

    private fun create_tab_lifecycle(): editor_tab_lifecycle {
        return editor_tab_lifecycle(
            context = this,
            settings = { state.editor_settings },
            create_textmate_language = { file_path -> create_configured_textmate_language(file_path) },
            with_applying_content = { action -> with_applying_editor_content(action) },
            on_content_changed = { handle_editor_content_changed() },
            on_selection_changed = { changed_editor -> handle_editor_selection_changed(changed_editor) },
            current_comment_action = { current_line_comment_action() },
            on_toggle_comment = { toggle_line_comment() },
            on_ai_action = { action, selection -> handle_ai_action_from_editor(action, selection) },
            can_show_details = { can_request_hover_details() },
            on_show_details = { open_hover_detail_sheet() },
            can_show_goto = {
                val path = state.current_file_path
                path != null && is_cpp_source_file(path) && clangd_project != null
            },
            on_goto_definition = { goto_definition() },
            initial_styles_timeout_ms = initial_editor_styles_timeout_ms
        )
    }

    /** 长按工具栏「详情」按钮可见性：当前文件是 .go 且 clangd 已连接且 hover 设置开启。 */
    private fun can_request_hover_details(): Boolean {
        val path = state.current_file_path ?: return false
        if (!is_cpp_source_file(path)) return false
        if (!state.editor_settings.clangd_hover) return false
        val project = clangd_project ?: return false
        return project.is_hover_available(File(path))
    }

    /** 打开符号详情弹层：取当前选区（或光标）中点请求 clangd hover，结果交给底部弹窗。 */
    private fun open_hover_detail_sheet() {
        val project = clangd_project ?: run {
            app_toast.show(this, "clangd 未就绪", app_toast.LENGTH_SHORT)
            return
        }
        val path = state.current_file_path ?: return
        if (!is_cpp_source_file(path)) return
        if (!::editor.isInitialized) return

        // 选区中点落在符号内部；无选区时用光标位置
        val cursor = editor.cursor
        val char_index = if (cursor.isSelected && cursor.right().index > cursor.left().index) {
            (cursor.left().index + cursor.right().index) / 2
        } else {
            cursor.left().index
        }
        val position = editor.text.getIndexer().getCharPosition(char_index)

        hover_sheet_open = true
        hover_detail_loading = true
        hover_detail = null
        hover_detail_error = null
        hover_detail_job?.cancel()
        hover_detail_job = lifecycleScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    project.request_hover(File(path), editor, position.line, position.column)
                }
                hover_detail_loading = false
                if (result == null) {
                    hover_detail_error = "未找到该位置的符号详情"
                } else {
                    hover_detail = result
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                hover_detail_loading = false
                hover_detail_error = "查询失败: ${e.message}"
            }
        }
    }

    /** 编辑器选区 AI 动作：打开 AI 弹窗并注入提示 */
    private fun handle_ai_action_from_editor(action: String, selection: String) {
        val agent = ai_agent ?: run {
            app_toast.show(this, "AI 助手未就绪", app_toast.LENGTH_SHORT)
            return
        }
        val prompt = when (action) {
            "explain" -> "请解释这段代码：\n```\n${selection.take(2000)}\n```"
            "fix" -> "这段代码有问题，请帮我修复：\n```\n${selection.take(2000)}\n```"
            else -> "请处理这段代码：\n```\n${selection.take(2000)}\n```"
        }
        ai_open_trigger += 1
        agent.send_user_message(prompt)
    }

    private fun create_code_editor(): CodeEditor {
        return tab_lifecycle.create_editor(null)
    }

    private fun create_tab_editor(file_path: String?): CodeEditor {
        return tab_lifecycle.create_editor(file_path)
    }

    private fun with_applying_editor_content(action: () -> Unit) {
        applying_editor_content = true
        try {
            action()
        } finally {
            applying_editor_content = false
        }
    }

    private fun handle_editor_content_changed() {
        if (!applying_editor_content) {
            capture_active_tab_state()
            active_tab()?.has_changes = true
            state.has_changes = true
            schedule_editor_recovery_persist()
        }
        update_history_state()
        schedule_block_end_hints_update()
        schedule_structure_refresh()
        schedule_diagnostics_refresh()
    }

    private fun handle_editor_selection_changed(changed_editor: CodeEditor) {
        val line = changed_editor.cursor.leftLine
        val column = changed_editor.cursor.leftColumn
        state.cursor_line = line + 1
        state.cursor_column = column + 1
        state.cursor_selected = changed_editor.cursor.isSelected

        active_tab()?.let { tab ->
            tab.cursor_line = line
            tab.cursor_column = column
        }
    }

    private fun update_editor_settings(settings: editor_settings_state) {
        apply_editor_settings(settings)
    }

    private fun apply_editor_settings(settings: editor_settings_state) {
        val clangd_settings_changed = state.editor_settings.clangd_enabled != settings.clangd_enabled ||
            state.editor_settings.clangd_completion != settings.clangd_completion ||
            state.editor_settings.clangd_signature_help != settings.clangd_signature_help ||
            state.editor_settings.clangd_document_highlight != settings.clangd_document_highlight ||
            state.editor_settings.clangd_formatting != settings.clangd_formatting ||
            state.editor_settings.clangd_hover != settings.clangd_hover
        state.editor_settings = settings
        save_editor_settings(this, settings)
        clangd_project?.update_translation_settings(
            clangd_translation_settings(
                enabled = settings.clangd_translate_documentation,
                endpoint = settings.clangd_translation_endpoint,
                backend_api_key = settings.clangd_translation_api_key
            )
        )
        if (clangd_settings_changed) {
            reset_clangd_project()
        }
        open_editors().forEach { tab_editor ->
            apply_editor_behavior_settings(
                context = this,
                editor = tab_editor,
                settings = settings,
                file_path = state.open_tabs.firstOrNull { tab -> tab.editor == tab_editor }?.file_path ?: state.current_file_path,
                current_language = current_textmate_language(tab_editor)
            )
            tab_editor.invalidate()
        }
        schedule_block_end_hints_update()
    }

    private fun open_editors(): List<CodeEditor> {
        return (state.open_tabs.mapNotNull { tab -> tab.editor } + listOfNotNull(if (::editor.isInitialized) editor else null))
            .distinct()
    }

    private fun apply_colors_to_open_editors() {
        open_editors().forEach { tab_editor ->
            apply_editor_colors(this, tab_editor)
            tab_editor.invalidate()
        }
    }

    private fun apply_current_editor_behavior_settings(target: CodeEditor, settings: editor_settings_state) {
        apply_editor_behavior_settings(
            context = this,
            editor = target,
            settings = settings,
            file_path = state.current_file_path,
            current_language = current_textmate_language(target)
        )
    }

    private fun request_import_editor_font() {
        import_editor_font_launcher.launch("font/*")
    }

    private fun apply_project_config(config: project_ide_config, on_saved: () -> Unit) {
        project_manager.save_project_ide_config(project_dir.absolutePath, config)
            .onSuccess {
                on_saved()
                app_toast.show(this, "项目配置已应用", app_toast.LENGTH_SHORT)
                // 配置变更后重启 clangd，让其重新加载构建约束等
                reset_clangd_project()
            }
            .onFailure { error ->
                app_toast.show(this, "项目配置保存失败: ${error.message}", app_toast.LENGTH_LONG)
            }
    }

    /** 给构建环境叠加项目配置选定的编译器（CC / CXX）。 */
    private fun build_environment(
        base: Map<String, String>,
        build: project_build_config
    ): Map<String, String> {
        val clang = build.compiler == "clang"
        return base + mapOf(
            "CC" to if (clang) "/usr/bin/clang" else "/usr/bin/gcc",
            "CXX" to if (clang) "/usr/bin/clang++" else "/usr/bin/g++"
        )
    }

    /**
     * 项目配置页「编译」：按面板当前配置构建，
     * 日志写入编译弹窗，可执行文件自动导出到公共 Download/cppstudio。
     */
    private fun compile_project_from_config(config: project_ide_config) {
        if (!detected_project_info.is_cpp) {
            app_toast.show(this, "当前项目不是 C/C++ 项目", app_toast.LENGTH_SHORT)
            return
        }
        if (build_job?.isActive == true || run_job?.isActive == true || compile_job?.isActive == true) {
            app_toast.show(this, "任务正在运行中", app_toast.LENGTH_SHORT)
            return
        }
        val project_environment = toolchain_manager.project_environment(project_dir.absolutePath)
        if (project_environment.missing.isNotEmpty()) {
            app_toast.show(this, project_environment.missing.joinToString("；"), app_toast.LENGTH_LONG)
            return
        }
        // 编译前先把面板配置落盘，保证编译参数与所见一致
        project_manager.save_project_ide_config(project_dir.absolutePath, config)

        compile_job = lifecycleScope.launch {
            if (!save_dirty_open_files(show_toast = false)) {
                app_toast.show(this@editor_activity, "编译取消，文件保存失败", app_toast.LENGTH_SHORT)
                return@launch
            }
            compile_dialog_state.show()
            compile_dialog_state.append(
                "正在编译（${config.build.build_type} / ${config.build.compiler} / ${config.build.cpp_std}）",
                editor_output_line_level.INFO
            )
            val success = try {
                proot_manager.execute_command_with_environment(
                    command = build_command(config.build),
                    working_dir = project_dir.absolutePath,
                    extra_environment = build_environment(project_environment.environment, config.build),
                    on_log = { line -> compile_dialog_state.append(line, output_level_for_line(line)) }
                )
            } catch (_: CancellationException) {
                compile_dialog_state.append("编译已停止", editor_output_line_level.WARNING)
                return@launch
            } finally {
                compile_dialog_state.finish()
            }

            if (success) {
                val binary = resolve_run_binary(config.build)
                if (binary != null && binary.isFile) {
                    compile_dialog_state.append(
                        "编译完成: ${binary.absolutePath} (${binary.length() / 1024}KB)",
                        editor_output_line_level.SUCCESS
                    )
                    export_compiled_binary(binary)
                } else {
                    compile_dialog_state.append(
                        "编译完成（未找到可执行文件，可能是库项目）",
                        editor_output_line_level.SUCCESS
                    )
                }
            } else {
                compile_dialog_state.append("编译失败", editor_output_line_level.ERROR)
            }
        }
    }

    /** 导出编译产物到公共 Download/cppstudio；Android 9 及以下未授权时先申请权限再补导出。 */
    private fun export_compiled_binary(bin_file: File) {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.Q &&
            androidx.core.content.ContextCompat.checkSelfPermission(
                this, android.Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            pending_export = bin_file to bin_file.name
            compile_dialog_state.append("等待存储权限，用于导出到 Download/cppstudio/bin", editor_output_line_level.INFO)
            storage_permission_launcher.launch(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
            return
        }
        export_compiled_binary_to_downloads(bin_file, bin_file.name)
    }

    private fun export_compiled_binary_to_downloads(file: File, name: String) {
        runCatching {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                // Android 10+：走 MediaStore，无需存储权限
                val values = android.content.ContentValues().apply {
                    put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, name)
                    put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "application/octet-stream")
                    put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, "Download/cppstudio")
                }
                val uri = contentResolver.insert(
                    android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                    values
                ) ?: throw IllegalStateException("无法创建下载条目")
                contentResolver.openOutputStream(uri)?.use { output ->
                    file.inputStream().use { input -> input.copyTo(output) }
                } ?: throw IllegalStateException("无法写入下载文件")
                "Download/cppstudio/$name"
            } else {
                @Suppress("DEPRECATION")
                val downloads = android.os.Environment.getExternalStoragePublicDirectory(
                    android.os.Environment.DIRECTORY_DOWNLOADS
                )
                val dir = File(downloads, "cppstudio").apply { mkdirs() }
                val target = File(dir, name)
                file.copyTo(target, overwrite = true)
                target.absolutePath
            }
        }.onSuccess { path ->
            compile_dialog_state.append("已导出: $path", editor_output_line_level.SUCCESS)
        }.onFailure { error ->
            compile_dialog_state.append(
                "导出失败: ${error.message}（文件仍保存在 ${file.absolutePath}）",
                editor_output_line_level.WARNING
            )
        }
    }

    private fun initialize_project() {
        // 创建 AI agent（带项目文件工具）
        setup_ai_agent()

        reload_file_tree {
            lifecycleScope.launch {
                if (!state.project_exists) {
                    state.status_text = "项目不存在"
                    return@launch
                }

                prewarm_textmate_languages()
                restore_editor_session()
                sync_project_watcher_dirs()
                // 克隆进来的项目、或还没有编译数据库的 CMake 项目：自动配置一次，
                // 让 clangd 拿到真实编译参数（否则只能走 fallbackFlags 的启发式）。
                val auto_requested = pending_auto_configure
                pending_auto_configure = false
                val toolchain_ready = toolchain_manager.is_cpp_installed() && toolchain_manager.is_cmake_installed()
                if (toolchain_ready && detected_project_info.kind == project_kind.CMAKE) {
                    val compile_db = File(File(project_dir, "build"), "compile_commands.json")
                    if (auto_requested || !compile_db.isFile) {
                        configure_project(auto = true)
                    }
                }
            }
        }
    }

    /**
     * 创建带项目文件工具的 AI agent。
     * 工具：read/write/edit/grep/ls（项目根限定）+ bash（proot 执行，带 Go 环境）。
     * 环境提供器：实时收集当前文件/光标/项目结构。
     */
    private fun setup_ai_agent() {
        val project = project_dir
        val home_dir = toolchain_runtime_provider.paths().home_dir
        val ai_root = java.io.File(home_dir, ".ai")
        ai_root.mkdirs()
        val global_skills_dir = java.io.File(ai_root, "skills")
        val global_prompts_dir = java.io.File(ai_root, "prompts")
        val sessions_dir = java.io.File(ai_root, "sessions")
        val project_skills_dir = java.io.File(project, ".ai/skills")
        val project_prompts_dir = java.io.File(project, ".ai/prompts")
        // 暴露给输入补全 UI 使用
        ai_global_prompts_dir = global_prompts_dir
        ai_project_prompts_dir = project_prompts_dir

        // 释放内置 skill 到全局目录（首次）
        runCatching { com.xiaonai.cppstudio.ai.skills.release_builtin_skills(this, global_skills_dir) }

        val go_env = runCatching {
            toolchain_manager.project_environment(project.absolutePath).environment
        }.getOrDefault(emptyMap())

        val registry = com.xiaonai.cppstudio.ai.tools.ai_tool_registry().apply {
            register(com.xiaonai.cppstudio.ai.tools.read_tool(project))
            register(com.xiaonai.cppstudio.ai.tools.edit_tool(project))
            register(com.xiaonai.cppstudio.ai.tools.grep_tool(project))
            register(com.xiaonai.cppstudio.ai.tools.ls_tool(project))
            register(com.xiaonai.cppstudio.ai.tools.write_tool(project))
            register(com.xiaonai.cppstudio.ai.tools.bash_tool(project, go_env))
        }

        val skill_manager = com.xiaonai.cppstudio.ai.skills.ai_skill_manager(global_skills_dir, project_skills_dir, plugin_skill_dirs_provider = { com.xiaonai.cppstudio.plugins.plugin_manager.skill_dirs() })
        registry.register(com.xiaonai.cppstudio.ai.tools.create_skill_tool(skill_manager))
        val input_processor = com.xiaonai.cppstudio.ai.ai_input_processor(
            project_dir = project,
            skill_manager = skill_manager,
            global_prompts_dir = global_prompts_dir,
            project_prompts_dir = project_prompts_dir
        )
        // 会话按项目隔离：scope = p-<项目名>-<路径哈希>，历史列表只见本项目会话；
        // legacy_ids 让旧版以项目名平铺的会话文件迁移进本项目 scope
        val session_store = com.xiaonai.cppstudio.ai.ai_session_store(
            sessions_dir,
            scope = com.xiaonai.cppstudio.ai.ai_session_store.project_scope(project),
            legacy_ids = listOf(project.name)
        )
        val mcp_manager = com.xiaonai.cppstudio.ai.mcp.ai_mcp_manager(project)
        val file_change_notifier = com.xiaonai.cppstudio.ai.ai_file_change_notifier()
        val steering_queue = com.xiaonai.cppstudio.ai.ai_steering_queue()

        // 文件变更监听：AI 改文件后刷新编辑器
        ai_file_change_notifier = file_change_notifier
        file_change_notifier.set_listener { changed_paths -> refresh_files_after_ai_edit(changed_paths) }

        ai_agent = com.xiaonai.cppstudio.ai.ai_agent_loop(
            settings_provider = {
                // 会话级模型选择：切了实例/模型则覆盖全局设置（快照含 base_url/key，多实例可切）
                // 用内存缓存版本：agent 每轮都会调用，避免重复 Keystore I/O（保存设置时会同步缓存）
                val base = com.xiaonai.cppstudio.ai.cached_ai_settings(this)
                _session_choice?.let { c ->
                    base.copy(
                        provider = c.provider,
                        model = c.model,
                        base_url = c.base_url.ifBlank { base.base_url },
                        api_key = c.api_key.ifBlank { base.api_key }
                    )
                } ?: base
            },
            env_provider = {
                com.xiaonai.cppstudio.ai.ai_environment_context(
                    project_dir = project,
                    project_name = project.name,
                    current_file_path = state.current_file_path,
                    current_file_name = state.current_file_name,
                    cursor_line = state.cursor_line,
                    cursor_column = state.cursor_column,
                    build_file_content = runCatching {
                        listOf("CMakeLists.txt", "Makefile").map { java.io.File(project, it) }.firstOrNull { it.isFile }?.readText()
                    }.getOrNull(),
                    project_tree_overview = runCatching { com.xiaonai.cppstudio.ai.collect_tree_overview(project) }.getOrNull()
                )
            },
            tool_registry = registry,
            scope_launcher = { block -> lifecycleScope.launch { block() } },
            input_processor = input_processor,
            session_store = session_store,
            session_id = project.name,
            skill_manager = skill_manager,
            mcp_manager = mcp_manager,
            file_change_notifier = file_change_notifier,
            steering_queue = steering_queue
        )

        // 异步初始化（恢复会话 + 发现 skill + 启动 MCP）
        lifecycleScope.launch { ai_agent?.initialize() }
    }

    /** AI 改文件后刷新编辑器对应 tab */
    private fun refresh_files_after_ai_edit(changed_paths: List<String>, reload_tree: Boolean = true) {
        for (path in changed_paths) {
            val file = java.io.File(path)
            val tab = state.open_tabs.firstOrNull { java.io.File(it.file_path).absolutePath == file.absolutePath }
            if (tab != null) {
                runCatching {
                    val new_content = file.readText()
                    val tab_editor = tab.editor
                    if (tab_editor == null) {
                        tab.document.replace(0, tab.document.length, new_content)
                    } else {
                        // 编辑器与 tab.document 不是同一个 Content 实例，
                        // 外部工具（可视化设计器/AI）改盘后必须同步刷新当前编辑器。
                        val cursor_line = tab_editor.cursor.leftLine
                        val cursor_column = tab_editor.cursor.leftColumn
                        with_applying_editor_content {
                            tab_lifecycle.set_content(tab_editor, new_content)
                        }
                        val safe_line = cursor_line.coerceIn(0, (tab_editor.text.lineCount - 1).coerceAtLeast(0))
                        tab_editor.setSelection(
                            safe_line,
                            cursor_column.coerceIn(0, tab_editor.text.getColumnCount(safe_line))
                        )
                    }
                    tab.content = new_content
                    tab.has_changes = false
                    if (tab == active_tab()) {
                        state.content = new_content
                        state.has_changes = false
                        state.status_text = relative_project_path(project_dir, file)
                    }
                }
            }
        }
        // 刷新文件树
        if (reload_tree) reload_file_tree()
    }

    /** 外部（终端 git pull / touch / rm 等）改了项目文件：定向刷新文件树 + 同步被改的打开 tab。 */
    private fun handle_external_file_changes(changed_files: Set<String>, tree_dirs: Set<String>) {
        if (!state.project_exists) return
        if (tree_dirs.isNotEmpty()) refresh_file_tree_dirs(tree_dirs)
        if (changed_files.isNotEmpty()) refresh_files_after_external_change(changed_files)
    }

    /**
     * 外部修改的打开 tab 从磁盘同步：有未保存修改的 tab 保留用户编辑不覆盖；
     * 磁盘内容与当前一致的（比如自己保存触发的事件）跳过，避免光标被无谓重置。
     */
    private fun refresh_files_after_external_change(changed_paths: Set<String>) {
        val refreshable = changed_paths.mapNotNull { path ->
            val canonical = File(path).absolutePath
            val tab = state.open_tabs.firstOrNull { File(it.file_path).absolutePath == canonical }
                ?: return@mapNotNull null
            if (tab.has_changes) return@mapNotNull null
            val disk = runCatching { File(canonical).readText() }.getOrNull() ?: return@mapNotNull null
            if (disk == tab.content) return@mapNotNull null
            canonical
        }
        if (refreshable.isNotEmpty()) refresh_files_after_ai_edit(refreshable, reload_tree = false)
    }

    /** 外部变更后定向重载：根目录与已展开目录重读磁盘，未展开目录仅失效缓存（下次展开时再读）。 */
    private fun refresh_file_tree_dirs(evented_dirs: Set<String>) {
        val root_path = project_dir.absolutePath
        evented_dirs.forEach { dir ->
            file_tree_children_cache.remove(dir)
            if (!File(dir).exists()) state.expanded_paths = state.expanded_paths - dir
        }
        val reload_targets = (evented_dirs + root_path)
            .filter { it == root_path || it in state.expanded_paths }
            .filter { File(it).isDirectory }
        if (reload_targets.isEmpty()) {
            state.file_nodes = build_lazy_visible_file_nodes(project_dir, state.expanded_paths, file_tree_children_cache)
            return
        }
        file_tree_job?.cancel()
        file_tree_job = lifecycleScope.launch {
            state.file_tree_loading = true
            val result = withContext(Dispatchers.IO) {
                runCatching { reload_targets.associateWith { load_file_tree_directory(File(it)) } }
            }
            state.file_tree_loading = false
            result.onSuccess { loaded ->
                file_tree_children_cache.putAll(loaded)
                state.file_nodes = build_lazy_visible_file_nodes(project_dir, state.expanded_paths, file_tree_children_cache)
                sync_project_watcher_dirs()
            }
        }
    }

    /** 文件树懒加载：只监听已缓存目录（根+已展开）与打开 tab 的父目录，随展开/开关 tab 增减。 */
    private fun sync_project_watcher_dirs() {
        val watcher = project_watcher ?: return
        if (!::project_dir.isInitialized || !state.project_exists) return
        val dirs = file_tree_children_cache.keys.toHashSet()
        state.open_tabs.forEach { tab ->
            File(tab.file_path).parentFile?.takeIf { it.isDirectory }?.let { dirs.add(it.absolutePath) }
        }
        watcher.set_directories(dirs)
    }

    private fun prewarm_textmate_languages() {
        if (textmate_prewarm_started) return
        textmate_prewarm_started = true
        lifecycleScope.launch(Dispatchers.Default) {
            listOf("prewarm.go", "prewarm.mod").forEach { file_name ->
                runCatching {
                    cppstudio_application.instance.create_textmate_language(file_name)?.let { language ->
                        language.setCompleterKeywords(cpp_completion_keywords)
                        language.destroy()
                    }
                }
            }
        }
    }

    private fun import_editor_font_from_uri(uri: Uri) {
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { import_editor_font(this@editor_activity, uri) }
            }

            result.onSuccess { path ->
                update_editor_settings(
                    state.editor_settings.copy(
                        font_family = "imported",
                        imported_font_path = path
                    )
                )
            }.onFailure { error ->
                app_toast.show(this@editor_activity, "字体导入失败: ${error.message.orEmpty()}", app_toast.LENGTH_LONG)
            }
        }
    }


    private fun request_open_file(file_path: String) {
        open_file(file_path)
    }

    private fun request_select_tab(file_path: String) {
        if (file_path == state.current_file_path) return

        val index = find_tab_index(file_path)
        if (index >= 0) {
            attach_editor_tab(index)
            persist_editor_session()
        }
    }

    private fun request_close_tab(file_path: String) {
        capture_active_tab_state()
        val index = find_tab_index(file_path)
        if (index < 0) return

        val tab = state.open_tabs[index]
        if (tab.pinned) return

        if (tab.has_changes) {
            attach_editor_tab(index, capture_current = false)
            state.pending_action = editor_pending_action.CloseTab(file_path)
            state.show_unsaved_dialog = true
            return
        }

        close_tab(file_path)
    }

    private fun request_close_other_tabs(keep_file_path: String) {
        capture_active_tab_state()
        val dirty_index = find_dirty_closable_tab_index(state.open_tabs, keep_file_path)
        if (dirty_index >= 0) {
            attach_editor_tab(dirty_index, capture_current = false)
            state.pending_action = editor_pending_action.CloseOtherTabs(keep_file_path)
            state.show_unsaved_dialog = true
            return
        }

        close_other_tabs(keep_file_path)
    }

    private fun request_close_all_tabs() {
        capture_active_tab_state()
        val dirty_index = find_dirty_closable_tab_index(state.open_tabs)
        if (dirty_index >= 0) {
            attach_editor_tab(dirty_index, capture_current = false)
            state.pending_action = editor_pending_action.CloseAllTabs
            state.show_unsaved_dialog = true
            return
        }

        close_all_tabs()
    }

    private fun request_close_editor() {
        state.show_exit_dialog = true
    }

    private fun confirm_close_editor() {
        state.show_exit_dialog = false
        close_editor_after_confirmation()
    }

    private fun close_editor_after_confirmation() {
        capture_active_tab_state()
        val dirty_index = find_dirty_tab_index(state.open_tabs)
        if (dirty_index >= 0) {
            attach_editor_tab(dirty_index, capture_current = false)
            state.pending_action = editor_pending_action.CloseEditor
            state.show_unsaved_dialog = true
            return
        }

        finish()
    }

    private fun request_save_file(show_toast: Boolean = true, on_saved: (() -> Unit)? = null) {
        lifecycleScope.launch {
            if (save_current_file(show_toast)) {
                on_saved?.invoke()
            }
        }
    }

    /**
     * 格式化当前文件：clang-format -i（走 proot，用项目里的 .clang-format）。
     *
     * 不用编辑器内置格式化：clang-format 是 C/C++ 的事实标准，且能读到项目的
     * .clang-format 配置（新建项目时已自动生成一份）。
     */
    private fun format_current_file() {
        val file_path = state.current_file_path ?: return
        if (state.read_only) {
            app_toast.show(this, "只读模式不能格式化", app_toast.LENGTH_SHORT)
            return
        }
        if (!is_cpp_source_file(file_path)) {
            val cursor = editor.cursor
            val accepted = if (cursor.isSelected) {
                editor.formatCodeAsync(cursor.left(), cursor.right())
            } else {
                editor.formatCodeAsync()
            }
            if (!accepted) app_toast.show(this, "当前语言暂不支持格式化", app_toast.LENGTH_SHORT)
            return
        }
        if (!toolchain_manager.is_clang_format_installed()) {
            app_toast.show(this, "clang-format 未安装，请在「开发工具」中安装", app_toast.LENGTH_LONG)
            return
        }
        lifecycleScope.launch {
            if (!save_current_file(show_toast = false)) return@launch
            val environment = toolchain_manager.project_environment(project_dir.absolutePath)
            val ok = proot_manager.execute_command_with_environment(
                command = toolchain_manager.clang_format_proot_path() + " -i " + shell_quote(file_path),
                working_dir = project_dir.absolutePath,
                extra_environment = environment.environment,
                on_log = { line -> output_panel_state.append_log(line) }
            )
            if (ok) {
                refresh_files_after_ai_edit(listOf(file_path), reload_tree = false)
                app_toast.show(this@editor_activity, "已格式化 ${File(file_path).name}", app_toast.LENGTH_SHORT)
            } else {
                app_toast.show(this@editor_activity, "格式化失败，检查 .clang-format 与输出面板", app_toast.LENGTH_LONG)
            }
        }
    }

    private fun handle_build_button_click() {
        if (output_panel_state.task_running) {
            if (!output_panel_state.task_stopping) {
                output_panel_state.task_stopping = true
                build_job?.cancel()
                run_job?.cancel()
            }
            return
        }
        if (!detected_project_info.is_cpp) {
            app_toast.show(this, "当前项目不是 C/C++ 项目（缺少 CMakeLists.txt / Makefile）", app_toast.LENGTH_SHORT)
            return
        }
        build_cpp_project()
    }

    /**
     * 构建当前项目。
     *
     * CMake 项目：`cmake -S . -B build`（顺便导出 compile_commands.json 给 clangd）
     * + `cmake --build build -j N`；Makefile 项目：`make -j N`。
     * 输出流式写入输出面板，产物在 build/ 或项目根。
     */
    private fun build_cpp_project() {
        sidebar_log_open_trigger++
        if (build_job?.isActive == true || run_job?.isActive == true) {
            app_toast.show(this, "任务正在运行中", app_toast.LENGTH_SHORT)
            return
        }

        val project_environment = toolchain_manager.project_environment(project_dir.absolutePath)
        if (project_environment.missing.isNotEmpty()) {
            val message = project_environment.missing.joinToString("；")
            app_toast.show(this, message, app_toast.LENGTH_LONG)
            output_panel_state.append_output("错误: $message", editor_output_line_level.ERROR)
            return
        }

        build_job = lifecycleScope.launch {
            output_panel_state.selected_tab = editor_output_tab.Output
            output_panel_state.clear_output()
            output_panel_state.task_running = true
            output_panel_state.task_stopping = false
            if (!save_dirty_open_files(show_toast = false)) {
                output_panel_state.append_output("构建取消，文件保存失败", editor_output_line_level.ERROR)
                output_panel_state.task_running = false
                output_panel_state.task_stopping = false
                return@launch
            }

            val build = project_manager.read_project_build_config(project_dir.absolutePath)
            output_panel_state.append_output(
                "构建配置: ${build.build_type} · ${build.compiler} · ${build.cpp_std}",
                editor_output_line_level.INFO
            )
            output_panel_state.append_output("正在构建...", editor_output_line_level.INFO)

            val success = try {
                proot_manager.execute_command_with_environment(
                    command = build_command(build),
                    working_dir = project_dir.absolutePath,
                    extra_environment = build_environment(project_environment.environment, build),
                    on_log = { line -> output_panel_state.append_output(line, output_level_for_line(line)) }
                )
            } catch (_: CancellationException) {
                output_panel_state.append_output("构建已停止", editor_output_line_level.WARNING)
                return@launch
            } finally {
                output_panel_state.task_running = false
                output_panel_state.task_stopping = false
            }

            if (success) {
                val binary = resolve_run_binary(build)
                if (binary != null) {
                    output_panel_state.append_output("产物: ${binary.absolutePath}", editor_output_line_level.SUCCESS)
                }
                output_panel_state.append_output("构建完成", editor_output_line_level.SUCCESS)
                app_toast.show(this@editor_activity, "构建完成", app_toast.LENGTH_SHORT)
                // build/ 里多了新产物与 compile_commands.json，刷新文件树
                refresh_file_tree(File(project_dir, "build").absolutePath)
            } else {
                output_panel_state.append_output("构建失败", editor_output_line_level.ERROR)
                app_toast.show(this@editor_activity, "构建失败", app_toast.LENGTH_LONG)
            }
        }
    }

    /**
     * 运行当前项目：产物存在则直接起终端跑；不存在先构建。
     * 需要标准输入的程序（scanf / std::cin）可以直接在终端里交互。
     */
    private fun run_cpp_project() {
        if (!detected_project_info.is_cpp) {
            app_toast.show(this, "当前项目不是 C/C++ 项目", app_toast.LENGTH_SHORT)
            return
        }
        val project_environment = toolchain_manager.project_environment(project_dir.absolutePath)
        if (project_environment.missing.isNotEmpty()) {
            val message = project_environment.missing.joinToString("；")
            app_toast.show(this, message, app_toast.LENGTH_LONG)
            output_panel_state.append_output("错误: $message", editor_output_line_level.ERROR)
            return
        }
        if (build_job?.isActive == true || run_job?.isActive == true) {
            app_toast.show(this, "任务正在运行中", app_toast.LENGTH_SHORT)
            return
        }

        run_job = lifecycleScope.launch {
            if (!save_dirty_open_files(show_toast = false)) {
                app_toast.show(this@editor_activity, "运行取消，文件保存失败", app_toast.LENGTH_SHORT)
                return@launch
            }
            val build = project_manager.read_project_build_config(project_dir.absolutePath)
            val binary = resolve_run_binary(build)
            if (binary == null || !binary.isFile) {
                app_toast.show(this@editor_activity, "还没构建过，正在先构建...", app_toast.LENGTH_SHORT)
                build_cpp_project()
                return@launch
            }
            val command = "./" + project_dir.toURI().relativize(binary.toURI()).path +
                (if (build.run_args.isBlank()) "" else " " + build.run_args)
            startActivity(
                Intent(this@editor_activity, terminal_activity::class.java)
                    .putExtra(terminal_activity.EXTRA_RUN_COMMAND, command)
                    .putExtra(terminal_activity.EXTRA_RUN_TITLE, binary.name)
                    .putExtra(terminal_activity.EXTRA_RUN_WORKING_DIR, project_dir.absolutePath)
                    .putStringArrayListExtra(
                        terminal_activity.EXTRA_RUN_ENVIRONMENT,
                        ArrayList(project_environment.environment.map { (key, value) -> "$key=$value" })
                    )
            )
        }
    }

    /**
     * CMake 配置（原 Go 版本的 go mod tidy 位）：生成 build/ 与 compile_commands.json。
     * [auto] = true 为克隆项目打开后的自动执行（失败只写输出面板，不弹 toast）。
     */
    private fun configure_project(auto: Boolean = false) {
        if (detected_project_info.kind != project_kind.CMAKE) {
            if (!auto) app_toast.show(this, "当前项目不是 CMake 项目", app_toast.LENGTH_SHORT)
            return
        }
        if (build_job?.isActive == true || run_job?.isActive == true) {
            if (!auto) app_toast.show(this, "任务正在运行中", app_toast.LENGTH_SHORT)
            return
        }
        val project_environment = toolchain_manager.project_environment(project_dir.absolutePath)
        if (project_environment.missing.isNotEmpty()) {
            val message = project_environment.missing.joinToString("；")
            if (!auto) app_toast.show(this, message, app_toast.LENGTH_LONG)
            output_panel_state.append_output("错误: $message", editor_output_line_level.ERROR)
            return
        }

        build_job = lifecycleScope.launch {
            output_panel_state.selected_tab = editor_output_tab.Output
            output_panel_state.clear_output()
            output_panel_state.task_running = true
            output_panel_state.task_stopping = false
            output_panel_state.append_output(
                if (auto) "新项目，自动生成编译数据库（cmake -B build）..." else "cmake 配置中...",
                editor_output_line_level.NORMAL
            )
            val build = project_manager.read_project_build_config(project_dir.absolutePath)
            val success = try {
                proot_manager.execute_command_with_environment(
                    command = configure_command(build),
                    working_dir = project_dir.absolutePath,
                    extra_environment = build_environment(project_environment.environment, build),
                    on_log = { line -> output_panel_state.append_output(line, output_level_for_line(line)) }
                )
            } catch (_: CancellationException) {
                output_panel_state.append_output("配置已停止", editor_output_line_level.WARNING)
                return@launch
            } finally {
                output_panel_state.task_running = false
                output_panel_state.task_stopping = false
            }

            if (success) {
                output_panel_state.append_output("配置完成，compile_commands.json 已就绪", editor_output_line_level.SUCCESS)
                if (!auto) app_toast.show(this@editor_activity, "配置完成", app_toast.LENGTH_SHORT)
                refresh_file_tree(File(project_dir, "build").absolutePath)
            } else {
                output_panel_state.append_output("配置失败", editor_output_line_level.ERROR)
                if (!auto) app_toast.show(this@editor_activity, "配置失败", app_toast.LENGTH_LONG)
            }
        }
    }

    /** 运行测试：CMake 项目走 ctest，Makefile 项目走 make test。 */
    private fun run_tests() {
        if (!detected_project_info.is_cpp) {
            app_toast.show(this, "当前项目不是 C/C++ 项目", app_toast.LENGTH_SHORT)
            return
        }
        if (build_job?.isActive == true || run_job?.isActive == true) {
            app_toast.show(this, "任务正在运行中", app_toast.LENGTH_SHORT)
            return
        }
        val project_environment = toolchain_manager.project_environment(project_dir.absolutePath)
        if (project_environment.missing.isNotEmpty()) {
            val message = project_environment.missing.joinToString("；")
            app_toast.show(this, message, app_toast.LENGTH_LONG)
            output_panel_state.append_output("错误: $message", editor_output_line_level.ERROR)
            return
        }

        build_job = lifecycleScope.launch {
            output_panel_state.selected_tab = editor_output_tab.Output
            output_panel_state.clear_output()
            output_panel_state.task_running = true
            output_panel_state.task_stopping = false
            output_panel_state.append_output("正在运行测试...", editor_output_line_level.INFO)
            if (!save_dirty_open_files(show_toast = false)) {
                output_panel_state.append_output("测试取消，文件保存失败", editor_output_line_level.ERROR)
                output_panel_state.task_running = false
                output_panel_state.task_stopping = false
                return@launch
            }
            val build = project_manager.read_project_build_config(project_dir.absolutePath)
            val success = try {
                proot_manager.execute_command_with_environment(
                    command = test_command(build),
                    working_dir = project_dir.absolutePath,
                    extra_environment = build_environment(project_environment.environment, build),
                    on_log = { line -> output_panel_state.append_output(line, output_level_for_line(line)) }
                )
            } catch (_: CancellationException) {
                output_panel_state.append_output("测试已停止", editor_output_line_level.WARNING)
                return@launch
            } finally {
                output_panel_state.task_running = false
                output_panel_state.task_stopping = false
            }

            if (success) {
                output_panel_state.append_output("测试通过", editor_output_line_level.SUCCESS)
                app_toast.show(this@editor_activity, "测试通过", app_toast.LENGTH_SHORT)
            } else {
                output_panel_state.append_output("测试失败", editor_output_line_level.ERROR)
                app_toast.show(this@editor_activity, "测试失败", app_toast.LENGTH_LONG)
            }
        }
    }

    /** 并行任务数：0 = 自动（取设备核数，最多 8）。 */
    private fun job_count(build: project_build_config): Int {
        if (build.parallel_jobs > 0) return build.parallel_jobs
        return Runtime.getRuntime().availableProcessors().coerceIn(1, 8)
    }

    /** CMake 配置命令（同时导出 compile_commands.json 给 clangd）。 */
    private fun configure_command(build: project_build_config): String {
        val parts = mutableListOf(
            "cmake",
            "-S .",
            "-B build",
            "-DCMAKE_BUILD_TYPE=" + build.build_type,
            "-DCMAKE_EXPORT_COMPILE_COMMANDS=ON",
            "-DCMAKE_C_STANDARD=" + build.c_std.removePrefix("c"),
            "-DCMAKE_CXX_STANDARD=" + build.cpp_std.removePrefix("c++")
        )
        if (build.compiler == "clang") {
            parts.add("-DCMAKE_C_COMPILER=clang")
            parts.add("-DCMAKE_CXX_COMPILER=clang++")
        }
        if (build.cmake_args.isNotBlank()) parts.add(build.cmake_args)
        return parts.joinToString(" ")
    }

    /** 完整构建命令：配置 + 编译（CMake 项目），或 make（纯 Makefile 项目）。 */
    private fun build_command(build: project_build_config): String {
        if (detected_project_info.kind == project_kind.MAKE) {
            val args = if (build.make_args.isBlank()) "" else " " + build.make_args
            return "make -j" + job_count(build) + args
        }
        return configure_command(build) + " && cmake --build build --parallel " + job_count(build)
    }

    /** 测试命令：ctest（CMake）或 make test（Makefile）。 */
    private fun test_command(build: project_build_config): String {
        if (detected_project_info.kind == project_kind.MAKE) {
            return "make test"
        }
        // 先确保配置过（ctest 需要 build/CTestTestfile.cmake）
        return configure_command(build) + " && ctest --test-dir build --output-on-failure"
    }

    /**
     * 定位本次构建出的可执行文件。
     *
     * 顺序：项目配置里的 run_entry → build/<项目名> → <项目根>/<项目名> → build/app
     * → build/ 下第一个可执行文件。库项目可能一个都没有（返回 null，构建仍算成功）。
     */
    private fun resolve_run_binary(build: project_build_config): File? {
        val explicit = build.run_entry.takeIf { it.isNotBlank() }
            ?.let { File(File(project_dir, it).absolutePath) }
        if (explicit?.isFile == true) return explicit

        val build_dir = File(project_dir, "build")
        val candidates = listOf(
            File(build_dir, project_dir.name),
            File(project_dir, project_dir.name),
            File(build_dir, "app")
        )
        candidates.firstOrNull { it.isFile }?.let { return it }

        return build_dir.listFiles()
            ?.filter { it.isFile && it.extension.isEmpty() && it.canExecute() }
            ?.minByOrNull { it.lastModified() }
    }

    private fun output_level_for_line(line: String): editor_output_line_level {
        val text = line.lowercase()
        return when {
            "error" in text || "failed" in text || "panic:" in text -> editor_output_line_level.ERROR
            "warning" in text -> editor_output_line_level.WARNING
            else -> editor_output_line_level.NORMAL
        }
    }

    private fun shell_quote(value: String): String {
        if (value.isEmpty()) return "''"
        return "'" + value.replace("'", "'\\''") + "'"
    }


    private fun save_pending_action() {
        request_save_file(show_toast = false) {
            state.show_unsaved_dialog = false
            run_pending_action()
        }
    }

    private fun discard_pending_action() {
        active_tab()?.let { tab ->
            remove_editor_recovery_draft(this, project_dir, tab.file_path)
            tab.has_changes = false
        }
        state.has_changes = false
        state.show_unsaved_dialog = false
        run_pending_action()
    }

    private fun run_pending_action() {
        val action = state.pending_action
        state.pending_action = null
        when (action) {
            is editor_pending_action.CloseTab -> request_close_tab(action.file_path)
            is editor_pending_action.CloseOtherTabs -> request_close_other_tabs(action.keep_file_path)
            editor_pending_action.CloseAllTabs -> request_close_all_tabs()
            editor_pending_action.CloseEditor -> close_editor_after_confirmation()
            null -> Unit
        }
    }

    private fun open_file(file_path: String) {
        capture_active_tab_state()
        val existing_index = find_tab_index(File(file_path).absolutePath)
        if (existing_index >= 0) {
            attach_editor_tab(existing_index, capture_current = false)
            persist_editor_session()
            return
        }

        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                load_project_file(project_dir, file_path)
            }

            result.onSuccess { loaded_file ->
                open_loaded_file_tab(loaded_file)
            }.onFailure { error ->
                app_toast.show(this@editor_activity, "打开失败: ${error.message}", app_toast.LENGTH_LONG)
            }
        }
    }

    /**
     * 跳转到光标所在标识符的定义。请求 clangd 的 textDocument/definition，
     * 拿到第一个 Location 后打开对应文件并定位。标准库等只读路径跳转失败时提示。
     */
    fun goto_definition() {
        if (_goto_in_progress.value) return
        val tab = active_tab() ?: return
        val file = File(tab.file_path)
        val project = clangd_project ?: run {
            app_toast.show(this, "clangd 未就绪", app_toast.LENGTH_SHORT)
            return
        }
        if (!is_cpp_source_file(file.absolutePath)) return
        val cursor_line = editor.cursor.leftLine
        val cursor_column = editor.cursor.leftColumn

        _goto_in_progress.value = true
        lifecycleScope.launch {
            try {
                val location = withContext(Dispatchers.IO) {
                    project.request_definition(file, editor, cursor_line, cursor_column)
                }
                if (location == null) {
                    app_toast.show(this@editor_activity, "未找到定义", app_toast.LENGTH_SHORT)
                } else {
                    open_file_at(location.file_path, location.line, location.column)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                app_toast.show(this@editor_activity, "跳转失败: ${e.message}", app_toast.LENGTH_SHORT)
            } finally {
                _goto_in_progress.value = false
            }
        }
    }

    private fun open_file_at(file_path: String, line: Int, column: Int) {
        val normalized_file_path = File(file_path).absolutePath
        val existing_index = find_tab_index(normalized_file_path)
        if (existing_index >= 0) {
            attach_editor_tab(existing_index)
            move_cursor_to(line, column)
            persist_editor_session()
            return
        }

        capture_active_tab_state()
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                load_project_file(project_dir, normalized_file_path)
            }

            result.onSuccess { loaded_file ->
                open_loaded_file_tab(loaded_file)
                move_cursor_to(line, column)
            }.onFailure { error ->
                app_toast.show(this@editor_activity, "打开失败: ${error.message}", app_toast.LENGTH_LONG)
            }
        }
    }

    private fun create_open_tab(
        loaded_file: editor_loaded_file,
        initial_content: String = loaded_file.content
    ): editor_open_tab {
        val file = loaded_file.file
        return editor_open_tab(
            initial_file_path = file.absolutePath,
            initial_file_name = file.name,
            initial_status_text = relative_project_path(project_dir, file),
            initial_content = initial_content
        )
    }

    private fun schedule_editor_recovery_persist() {
        if (!::project_dir.isInitialized) return
        val drafts = editor_recovery_snapshot()
        if (drafts.isEmpty()) return

        editor_recovery_job?.cancel()
        editor_recovery_job = lifecycleScope.launch {
            withContext(NonCancellable + Dispatchers.IO) {
                drafts.forEach { (file_path, content) ->
                    save_editor_recovery_draft(
                        context = this@editor_activity,
                        project_dir = project_dir,
                        file_path = file_path,
                        content = content
                    ).onFailure { error ->
                        logger_manager.e(
                            tag = "editor_recovery",
                            msg = "保存未保存代码恢复草稿失败: ${error.message}",
                            tr = error
                        )
                    }
                }
            }
        }
    }

    private fun flush_editor_recovery_drafts() {
        if (!::project_dir.isInitialized) return
        capture_active_tab_state()
        editor_recovery_snapshot().forEach { (file_path, content) ->
            save_editor_recovery_draft(
                context = this,
                project_dir = project_dir,
                file_path = file_path,
                content = content
            ).onFailure { error ->
                logger_manager.e(
                    tag = "editor_recovery",
                    msg = "写入未保存代码恢复草稿失败: ${error.message}",
                    tr = error
                )
            }
        }
    }

    private fun editor_recovery_snapshot(): List<Pair<String, String>> {
        capture_active_tab_state()
        return state.open_tabs
            .filter { tab -> tab.has_changes }
            .map { tab -> tab.file_path to tab.content }
    }

    private suspend fun open_loaded_file_tab(loaded_file: editor_loaded_file): editor_open_tab {
        val tab = create_open_tab(loaded_file)
        prepare_tab_editor_for_display(tab)
        state.open_tabs.add(tab)
        reorder_tabs_keep_active()
        attach_editor_tab(find_tab_index(tab.file_path), capture_current = false)
        persist_editor_session()
        return tab
    }

    private fun move_cursor_to(line: Int, column: Int) {
        val line_count = editor.text.lineCount.coerceAtLeast(1)
        val safe_line = line.coerceIn(0, line_count - 1)
        val safe_column = column.coerceIn(0, editor.text.getColumnCount(safe_line))
        editor.setSelection(safe_line, safe_column)
        editor.ensurePositionVisible(safe_line, safe_column)
        state.cursor_line = safe_line + 1
        state.cursor_column = safe_column + 1
        state.cursor_selected = false
        active_tab()?.let { tab ->
            tab.cursor_line = safe_line
            tab.cursor_column = safe_column
        }
        editor.requestFocus()
    }

    private suspend fun restore_editor_session() {
        val session = load_editor_session(this, project_dir) ?: run {
            restore_pinned_tabs()
            return
        }
        if (session.tabs.isEmpty()) return

        state.loading = true
        val saved_tabs = withContext(Dispatchers.IO) {
            val loaded_files = load_pinned_project_files(project_dir, session.tabs.map { it.file_path })
                .associateBy { it.file.absolutePath }
            session.tabs.map { saved_tab ->
                Triple(
                    saved_tab,
                    loaded_files[saved_tab.file_path],
                    loaded_files[saved_tab.file_path]?.let {
                        load_editor_recovery_draft(this@editor_activity, project_dir, saved_tab.file_path)
                    }
                )
            }
        }
        state.loading = false

        saved_tabs.forEach { (saved_tab, loaded_file, recovered_content) ->
            if (loaded_file == null) return@forEach
            val tab = create_open_tab(
                loaded_file = loaded_file,
                initial_content = recovered_content ?: loaded_file.content
            ).apply {
                pinned = saved_tab.pinned
                cursor_line = saved_tab.cursor_line
                cursor_column = saved_tab.cursor_column
                if (recovered_content != null) {
                    has_changes = true
                    status_text = "未保存（已恢复）"
                }
            }
            if (find_tab_index(tab.file_path) < 0) {
                prepare_tab_editor(tab)
                state.open_tabs.add(tab)
            }
        }

        reorder_tabs_keep_active()
        val active_index = session.active_path
            ?.let { find_tab_index(it) }
            ?.takeIf { it >= 0 }
            ?: 0
        if (state.open_tabs.isNotEmpty()) {
            attach_editor_tab(active_index, capture_current = false)
        }
        save_pinned_tabs()
        persist_editor_session()
    }

    private suspend fun restore_pinned_tabs() {
        val pinned_paths = load_pinned_tab_paths(this, project_dir)
        if (pinned_paths.isEmpty()) return

        state.loading = true
        val loaded_files = withContext(Dispatchers.IO) {
            load_pinned_project_files(project_dir, pinned_paths)
        }
        state.loading = false

        loaded_files.forEach { loaded_file ->
            val tab = create_open_tab(loaded_file).apply { pinned = true }
            if (find_tab_index(tab.file_path) < 0) {
                prepare_tab_editor(tab)
                state.open_tabs.add(tab)
            }
        }
        reorder_tabs_keep_active()
        if (state.open_tabs.isNotEmpty() && state.current_file_path == null) {
            attach_editor_tab(0, capture_current = false)
        }
        save_pinned_tabs()
        persist_editor_session()
    }

    private fun save_pinned_tabs() {
        save_pinned_tab_paths(
            context = this,
            project_dir = project_dir,
            paths = pinned_tab_paths(state.open_tabs)
        )
    }

    private fun persist_editor_session() {
        if (!::project_dir.isInitialized) return
        // tab 开开关关都会走到这里：顺手同步监听目录（新增 tab 的父目录要挂上观察器）
        sync_project_watcher_dirs()
        capture_active_tab_state()
        save_editor_session(
            context = this,
            project_dir = project_dir,
            tabs = state.open_tabs.map { tab ->
                editor_session_tab(
                    file_path = tab.file_path,
                    cursor_line = tab.cursor_line,
                    cursor_column = tab.cursor_column,
                    pinned = tab.pinned
                )
            },
            active_path = state.current_file_path
        )
    }

    private suspend fun save_dirty_open_files(show_toast: Boolean): Boolean {
        capture_active_tab_state()
        val dirty_tabs = state.open_tabs.filter { tab -> tab.has_changes }
        if (dirty_tabs.isEmpty()) return true

        for (tab in dirty_tabs) {
            val content = tab.editor?.text?.toString() ?: tab.content
            val result = withContext(Dispatchers.IO) {
                save_project_file(tab.file_path, content)
            }
            result.onSuccess {
                tab.content = content
                tab.has_changes = false
                tab.status_text = relative_project_path(project_dir, File(tab.file_path))
                remove_editor_recovery_draft(this, project_dir, tab.file_path)
            }.onFailure { error ->
                app_toast.show(this, "保存失败: ${error.message}", app_toast.LENGTH_LONG)
                return false
            }
        }

        active_tab()?.let { tab ->
            state.content = tab.content
            state.has_changes = tab.has_changes
            state.status_text = if (tab.has_changes) tab.status_text else relative_project_path(project_dir, File(tab.file_path))
        } ?: run {
            state.has_changes = false
        }
        update_history_state()
        refresh_file_tree()
        if (show_toast) {
            app_toast.show(this, "已保存所有文件", app_toast.LENGTH_SHORT)
        }
        return true
    }

    private suspend fun save_current_file(show_toast: Boolean): Boolean {
        val file_path = state.current_file_path
        if (file_path == null) {
            app_toast.show(this, "没有打开文件", app_toast.LENGTH_SHORT)
            return false
        }

        val content = editor.text.toString()
        val result = withContext(Dispatchers.IO) {
            save_project_file(file_path, content)
        }

        result.onSuccess {
            active_tab()?.let { tab ->
                tab.content = content
                tab.has_changes = false
                tab.status_text = relative_project_path(project_dir, File(file_path))
            }
            state.content = content
            state.has_changes = false
            state.status_text = "已保存 ${File(file_path).name}"
            remove_editor_recovery_draft(this, project_dir, file_path)
            persist_editor_session()
            update_history_state()
            refresh_file_tree()
            if (show_toast) {
                app_toast.show(this, "已保存", app_toast.LENGTH_SHORT)
            }
            on_project_file_saved(file_path)
        }.onFailure { error ->
            app_toast.show(this, "保存失败: ${error.message}", app_toast.LENGTH_LONG)
        }

        return result.isSuccess
    }

    private fun on_project_file_saved(file_path: String) {
        val saved_file = File(file_path)
        val cmake_file = File(project_dir, "CMakeLists.txt")
        // 保存 CMakeLists.txt 后重新配置并重启 clangd：编译参数/宏定义可能变了
        if (saved_file.absolutePath == cmake_file.absolutePath &&
            detected_project_info.kind == project_kind.CMAKE &&
            build_job?.isActive != true
        ) {
            output_panel_state.append_log("CMakeLists.txt 已保存，重新配置并重启 clangd")
            configure_project(auto = true)
            reset_clangd_project()
        }
    }

    private fun toggle_pin_tab(file_path: String) {
        capture_active_tab_state()
        val tab = state.open_tabs.getOrNull(find_tab_index(file_path)) ?: return
        tab.pinned = !tab.pinned
        reorder_tabs_keep_active()
        save_pinned_tabs()
        persist_editor_session()
    }

    private fun close_tab(file_path: String) {
        val active_path = state.current_file_path
        val index = find_tab_index(file_path)
        if (index < 0 || state.open_tabs[index].pinned) return

        val closing_tab = state.open_tabs[index]
        val closing_file_path = closing_tab.file_path
        val closing_active_tab = closing_file_path == active_path
        if (state.open_tabs.size == 1) {
            state.open_tabs.removeAt(index)
            release_tab_editor(closing_tab)
            reset_editor_state()
            return
        }

        if (closing_active_tab) {
            val new_index_after_close = index.coerceAtMost(state.open_tabs.lastIndex - 1)
            val next_tab = state.open_tabs[if (index < state.open_tabs.lastIndex) index + 1 else index - 1]
            state.open_tabs.removeAt(index)
            activate_editor_tab(new_index_after_close, next_tab)
        } else {
            state.open_tabs.removeAt(index)
            state.selected_tab_index = find_tab_index(active_path)
        }
        release_tab_editor(closing_tab)
        persist_editor_session()
    }

    private fun close_other_tabs(keep_file_path: String) {
        val remaining_tabs = remaining_tabs_after_close_others(state.open_tabs, keep_file_path)
        if (remaining_tabs.isEmpty() || remaining_tabs.size == state.open_tabs.size) return

        val removed_tabs = state.open_tabs.filter { tab -> tab !in remaining_tabs }
        state.open_tabs.clear()
        state.open_tabs.addAll(remaining_tabs)
        removed_tabs.forEach { tab -> release_tab_editor(tab) }
        reorder_tabs_keep_active()
        val next_index = find_tab_index(keep_file_path).takeIf { it >= 0 } ?: 0
        attach_editor_tab(next_index, capture_current = false)
        persist_editor_session()
    }

    private fun close_all_tabs() {
        val active_path = state.current_file_path
        val pinned_tabs = pinned_tabs(state.open_tabs)
        val removed_tabs = state.open_tabs.filter { tab -> tab !in pinned_tabs }
        state.open_tabs.clear()
        state.open_tabs.addAll(pinned_tabs)
        removed_tabs.forEach { tab -> release_tab_editor(tab) }

        if (state.open_tabs.isEmpty()) {
            reset_editor_state()
            return
        }

        val next_index = find_tab_index(active_path).takeIf { it >= 0 } ?: 0
        attach_editor_tab(next_index, capture_current = false)
        persist_editor_session()
    }

    private fun reorder_tabs_keep_active() {
        val active_path = state.current_file_path
        val ordered_tabs = ordered_pinned_first_tabs(state.open_tabs)
        state.open_tabs.clear()
        state.open_tabs.addAll(ordered_tabs)
        state.selected_tab_index = find_tab_index(active_path)
    }

    private fun attach_editor_tab(index: Int, capture_current: Boolean = true) {
        if (index !in state.open_tabs.indices) return
        if (capture_current && state.selected_tab_index != index) {
            capture_active_tab_state()
        }

        activate_editor_tab(index, state.open_tabs[index])
    }

    private fun activate_editor_tab(index: Int, tab: editor_open_tab) {
        val tab_editor = tab.editor ?: prepare_tab_editor(tab)
        editor = tab_editor
        search_controller.set_editor(editor)
        state.selected_tab_index = index
        state.current_file_path = tab.file_path
        state.current_file_name = tab.file_name
        state.content = tab.content
        state.status_text = tab.status_text
        state.has_changes = tab.has_changes

        clear_editor_diagnostics()
        restore_editor_selection(tab)
        update_history_state()
        editor.requestFocus()
        schedule_block_end_hints_update()
        connect_clangd_if_needed(tab)
    }

    private fun disabled_clangd_features(settings: editor_settings_state): Set<LspFeature> {
        return buildSet {
            if (!settings.clangd_completion) add(LspFeature.Completion)
            if (!settings.clangd_signature_help) add(LspFeature.SignatureHelp)
            if (!settings.clangd_document_highlight) add(LspFeature.DocumentHighlight)
            if (!settings.clangd_formatting) add(LspFeature.Formatting)
            if (!settings.clangd_hover) add(LspFeature.Hover)
        }
    }

    private fun reset_clangd_project() {
        clangd_connect_job?.cancel()
        restore_editor_languages_from_lsp()
        clangd_project?.dispose()
        clangd_project = null
        active_tab()?.let { connect_clangd_if_needed(it) }
    }

    private fun restore_editor_languages_from_lsp() {
        open_editors().forEach { tab_editor ->
            val language = tab_editor.editorLanguage
            if (language is LspLanguage) {
                language.wrapperLanguage?.let { tab_editor.setEditorLanguage(it) }
            }
        }
    }

    private fun connect_clangd_if_needed(tab: editor_open_tab) {
        clangd_connect_job?.cancel()
        val file = File(tab.file_path)
        if (!is_cpp_source_file(file.absolutePath)) return
        if (!state.editor_settings.clangd_enabled) return
        if (!toolchain_manager.is_clangd_installed()) {
            log_clangd_skip_once(file, "clangd 未安装，请在「开发工具」中安装 clangd")
            return
        }

        val project_environment = toolchain_manager.project_environment(project_dir.absolutePath)
        val disabled_features = disabled_clangd_features(state.editor_settings)
        // clangd 实际路径由探测结果决定（/usr/bin/clangd 或 /usr/lib/llvmXX/bin/clangd）
        val clangd_command = toolchain_manager.clangd_proot_path()
        val lsp_project = clangd_project ?: clangd_lsp_project(
            project_dir = project_dir,
            disabled_features = disabled_features,
            translation_settings = clangd_translation_settings(
                enabled = state.editor_settings.clangd_translate_documentation,
                endpoint = state.editor_settings.clangd_translation_endpoint,
                backend_api_key = state.editor_settings.clangd_translation_api_key
            ),
            config_factory = { working_dir ->
                clangd_lsp_config(
                    runtime_paths = toolchain_runtime_provider.paths(),
                    project_dir = project_dir,
                    path = project_environment.environment["PATH"] ?: toolchain_manager.proot_path(),
                    clangd_command = clangd_command,
                    build_dir = File(project_dir, "build"),
                    extra_environment = project_environment.environment,
                    disabled_features = disabled_features,
                    on_stderr = on_stderr@{ line ->
                        val message = clean_clangd_log_line(line) ?: return@on_stderr
                        lifecycleScope.launch(Dispatchers.Main) { output_panel_state.append_log("clangd: $message") }
                    }
                )
            }
        ).also { clangd_project = it }

        clangd_connect_job = lifecycleScope.launch {
            val lsp_editor = lsp_project.get_or_create_editor(file, editor)
            lsp_editor.isEnableHover = state.editor_settings.clangd_hover
            lsp_editor.isEnableSignatureHelp = state.editor_settings.clangd_signature_help
            lsp_editor.eventListener = { _, new_status, old_status ->
                if (new_status != old_status) {
                    log_clangd_status(file, new_status)
                    if (new_status == LspEditorStatus.CONNECTED) {
                        // 连接完成后 clangd 很快会推送首批诊断，稍等再读容器
                        schedule_diagnostics_refresh(1500L)
                        // VS Code 式预热：后台强制 clangd 完成当前文件依赖树类型检查，
                        // 让 net/http 这类冷包的首次签名提示/补全不再超时
                        warm_up_clangd(file)
                    }
                }
            }
            if (!lsp_editor.isConnected) {
                output_panel_state.append_log("clangd: 正在连接 ${file.name}")
                val connected = lsp_project.connect(file, editor)
                if (!connected) {
                    output_panel_state.append_log("clangd: 连接失败 ${file.name}", editor_output_line_level.ERROR)
                }
            }
        }
    }

    /** clangd stderr 日志清洗：去掉带级别前缀的行，保留有用信息。 */
    private fun clean_clangd_log_line(line: String): String? {
        val trimmed = line.trim()
        if (trimmed.isEmpty()) return null
        return line.replace(Regex("^[A-Z]\\[[^]]+]\\s*"), "")
    }

    private fun log_clangd_skip_once(file: File, reason: String) {
        if (clangd_skipped_files.add("${file.absolutePath}:$reason")) {
            output_panel_state.append_log("clangd: 跳过 ${file.name}，$reason", editor_output_line_level.WARNING)
        }
    }

    private fun log_clangd_status(file: File, status: LspEditorStatus) {
        val message = when (status) {
            LspEditorStatus.CONNECTED -> "clangd: 已连接 ${file.name}"
            LspEditorStatus.DISCONNECTED -> "clangd: 已断开 ${file.name}"
            else -> return
        }
        lifecycleScope.launch { output_panel_state.append_log(message) }
    }

    // ---- 代码结构（大纲） ----

    /** 编辑中防抖刷新结构符号（150ms）。 */
    private fun schedule_structure_refresh() {
        structure_job?.cancel()
        structure_job = lifecycleScope.launch {
            delay(150)
            refresh_structure_symbols()
        }
    }

    /**
     * 重算当前文件的大纲符号：主线程读文本，Default 线程解析。
     * clangd documentSymbol 接入前的正则兜底实现。
     */
    private fun refresh_structure_symbols() {
        val file_path = state.current_file_path
        if (file_path == null || !file_path.endsWith(".go")) {
            structure_symbols = emptyList()
            return
        }
        val file_name = File(file_path).name
        val text = editor.text.toString()
        structure_job?.cancel()
        structure_job = lifecycleScope.launch(Dispatchers.Default) {
            val parsed = com.xiaonai.cppstudio.editor.core.editor_outline_parser.parse_cpp_outline(text, file_name)
            withContext(Dispatchers.Main) { structure_symbols = parsed }
        }
    }

    /** 代码结构面板点击跳转：光标落到目标行并滚动可见。 */
    private fun navigate_to_structure_line(line: Int) {
        val line_count = editor.text.lineCount.coerceAtLeast(1)
        val target = line.coerceIn(0, line_count - 1)
        editor.setSelection(target, 0)
        runCatching { editor.ensureSelectionVisible() }
        state.cursor_line = target + 1
        state.cursor_column = 1
    }

    // ---- LSP 诊断（问题页签 / 诊断弹层） ----

    /** 编辑后防抖刷新诊断（clangd 推送有延迟，默认 800ms 后读容器）。 */
    /** VS Code 式预热：连上 clangd 后台请求一次 documentSymbol，强制完成依赖树类型检查。 */
    private fun warm_up_clangd(file: File) {
        val project = clangd_project ?: return
        clangd_warmup_job?.cancel()
        clangd_warmup_job = lifecycleScope.launch(Dispatchers.IO) {
            val start = android.os.SystemClock.elapsedRealtime()
            val result = runCatching { project.warm_up_analysis(file) }
            val ms = android.os.SystemClock.elapsedRealtime() - start
            withContext(Dispatchers.Main) {
                // 预热耗时反映依赖树加载速度（net/http 这类大包首次会明显更久）
                output_panel_state.append_log("clangd: 预热${if (result.isSuccess) "完成" else "失败"} ${ms}ms")
            }
        }
    }

    private fun schedule_diagnostics_refresh(delay_ms: Long = 800L) {
        diagnostics_job?.cancel()
        diagnostics_job = lifecycleScope.launch {
            delay(delay_ms)
            refresh_file_diagnostics()
        }
    }

    private fun refresh_file_diagnostics() {
        val project = clangd_project
        val path = state.current_file_path
        if (project == null || path == null) {
            file_diagnostics = emptyList()
            return
        }
        file_diagnostics = project.current_diagnostics(File(path))
    }

    /** 打开诊断详情弹层：跳到该行并异步查询快速修复。 */
    private fun open_diagnostic_sheet(diagnostic: com.xiaonai.cppstudio.lsp.clangd.clangd_diagnostic) {
        sheet_diagnostic = diagnostic
        sheet_actions = emptyList()
        sheet_actions_loading = true
        navigate_to_structure_line(diagnostic.line)
        sheet_actions_job?.cancel()
        sheet_actions_job = lifecycleScope.launch {
            val project = clangd_project
            val path = state.current_file_path
            val actions = if (project != null && path != null) {
                runCatching {
                    project.request_code_actions(File(path), editor, diagnostic.line)
                }.getOrDefault(emptyList())
            } else {
                emptyList()
            }
            sheet_actions = actions
            sheet_actions_loading = false
        }
    }

    /** 应用快速修复后：关闭弹层并延时刷新诊断。 */
    private fun apply_diagnostic_action(action: com.xiaonai.cppstudio.lsp.clangd.clangd_code_action) {
        val project = clangd_project
        val path = state.current_file_path
        if (project != null && path != null) {
            val ok = runCatching { project.apply_code_action(File(path), action) }.getOrDefault(false)
            if (!ok) {
                app_toast.show(this, "修复应用失败", app_toast.LENGTH_SHORT)
            }
        }
        sheet_diagnostic = null
        schedule_diagnostics_refresh()
    }

    // ---- 全局搜索（proot grep） ----

    /** grep 输出行解析：./relative/path.go:行号:内容 */
    private val grep_hit_pattern = Regex("^(\\./[^:]+):(\\d+):(.*)$")

    /**
     * 在项目内全局搜索（proot `grep -rn --include=*.go`，排除 vendor/.git/bin，
     * 限 300 条命中）。空关键字返回空结果。
     */
    private suspend fun search_project(keyword: String): List<editor_project_search_hit> {
        val trimmed = keyword.trim()
        if (trimmed.length < 2 || !project_dir.isDirectory) return emptyList()
        val hits = mutableListOf<editor_project_search_hit>()
        val environment = toolchain_manager.project_environment(project_dir.absolutePath).environment
        val escaped = trimmed.replace("'", "'\\''")
        proot_manager.execute_command_with_environment(
            command = "grep -rn --include=\"*.go\" --exclude-dir=vendor --exclude-dir=.git --exclude-dir=bin -- '$escaped' . | head -n 300",
            working_dir = project_dir.absolutePath,
            extra_environment = environment,
            on_log = { line ->
                grep_hit_pattern.find(line.trim())?.let { match ->
                    val relative = match.groupValues[1].removePrefix("./")
                    val absolute = File(project_dir, relative).absolutePath
                    hits += editor_project_search_hit(
                        path = absolute,
                        relative_path = relative,
                        file_name = relative.substringAfterLast('/'),
                        line = match.groupValues[2].toIntOrNull() ?: return@let,
                        preview = match.groupValues[3].take(160)
                    )
                }
            }
        )
        return hits
    }

    private fun restore_editor_selection(tab: editor_open_tab) {
        val line_count = editor.text.lineCount.coerceAtLeast(1)
        val line = tab.cursor_line.coerceIn(0, line_count - 1)
        val column = tab.cursor_column.coerceIn(0, editor.text.getColumnCount(line))
        editor.setSelection(line, column)
        state.cursor_line = line + 1
        state.cursor_column = column + 1
        state.cursor_selected = false
    }

    private fun reset_editor_state() {
        state.toolbar_visible = true
        state.selected_tab_index = -1
        state.current_file_path = null
        current_textmate_scope = null
        state.current_file_name = "未打开文件"
        state.content = ""
        state.cursor_line = 1
        state.cursor_column = 1
        state.cursor_selected = false
        state.can_undo = false
        state.can_redo = false
        state.has_changes = false
        state.loading = false
        state.status_text = "请选择左侧文件"
        editor = create_code_editor()
        search_controller.set_editor(editor)
        clear_search()
    }

    private fun capture_active_tab_state() {
        val tab = active_tab() ?: return
        val content = editor.text.toString()
        tab.content = content
        state.content = content
        tab.cursor_line = editor.cursor.leftLine
        tab.cursor_column = editor.cursor.leftColumn
    }

    private fun active_tab(): editor_open_tab? {
        return state.open_tabs.getOrNull(state.selected_tab_index)
    }

    private fun release_tab_editor(tab: editor_open_tab) {
        clangd_project?.let { project ->
            val file = File(tab.file_path)
            lifecycleScope.launch(Dispatchers.IO) { project.close_file(file) }
        }
        tab_lifecycle.release(tab)
    }

    private fun find_tab_index(file_path: String?): Int {
        if (file_path == null) return -1
        return state.open_tabs.indexOfFirst { it.file_path == file_path }
    }

    private fun reload_file_tree(on_complete: (() -> Unit)? = null) {
        file_tree_job?.cancel()
        file_tree_job = lifecycleScope.launch {
            state.file_tree_loading = true
            val root = project_dir
            val root_path = root.absolutePath
            val root_loaded = root_path in file_tree_children_cache
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val exists = root.exists() && root.isDirectory
                    val root_children = if (exists && !root_loaded) {
                        load_file_tree_directory(root)
                    } else {
                        null
                    }
                    exists to root_children
                }
            }
            state.file_tree_loading = false
            result.onSuccess { (exists, root_children) ->
                if (root_children != null) file_tree_children_cache[root_path] = root_children
                state.project_exists = exists
                state.file_nodes = build_lazy_visible_file_nodes(root, state.expanded_paths, file_tree_children_cache)
            }.onFailure { error ->
                state.project_exists = false
                state.file_nodes = emptyList()
                app_toast.show(this@editor_activity, "刷新文件失败: ${error.message}", app_toast.LENGTH_LONG)
            }
            sync_project_watcher_dirs()
            on_complete?.invoke()
        }
    }

    private fun toggle_directory(path: String) {
        val absolute_path = File(path).absolutePath
        if (absolute_path in state.expanded_paths) {
            state.expanded_paths = state.expanded_paths - absolute_path
            reload_file_tree()
            return
        }

        state.expanded_paths = state.expanded_paths + absolute_path
        load_file_tree_directory_if_needed(absolute_path)
    }

    private fun refresh_file_tree(directory_path: String? = null, on_complete: (() -> Unit)? = null) {
        if (!directory_path.isNullOrBlank()) {
            val absolute_path = File(directory_path).absolutePath
            state.expanded_paths = state.expanded_paths + absolute_path
            file_tree_children_cache.remove(absolute_path)
            load_file_tree_directory_if_needed(absolute_path, force = true, on_complete = on_complete)
        } else {
            file_tree_children_cache.remove(project_dir.absolutePath)
            reload_file_tree(on_complete)
        }
    }

    private fun load_file_tree_directory_if_needed(path: String, force: Boolean = false, on_complete: (() -> Unit)? = null) {
        if (!force && path in file_tree_children_cache) {
            state.file_nodes = build_lazy_visible_file_nodes(project_dir, state.expanded_paths, file_tree_children_cache)
            sync_project_watcher_dirs()
            on_complete?.invoke()
            return
        }
        file_tree_job?.cancel()
        file_tree_job = lifecycleScope.launch {
            state.file_tree_loading = true
            val result = withContext(Dispatchers.IO) {
                runCatching { load_file_tree_directory(File(path)) }
            }
            state.file_tree_loading = false
            result.onSuccess { children ->
                file_tree_children_cache[path] = children
                state.file_nodes = build_lazy_visible_file_nodes(project_dir, state.expanded_paths, file_tree_children_cache)
                sync_project_watcher_dirs()
            }.onFailure { error ->
                app_toast.show(this@editor_activity, "刷新文件失败: ${error.message}", app_toast.LENGTH_LONG)
                state.expanded_paths = state.expanded_paths - path
            }
        }
    }

    private fun create_project_file(
        parent_path: String,
        name: String,
        template: editor_create_file_template
    ) {
        create_project_entry(
            parent_path = parent_path,
            name = name,
            directory = false,
            template_id = template.id
        )
    }

    private fun create_project_folder(parent_path: String, name: String) {
        create_project_entry(parent_path = parent_path, name = name, directory = true)
    }

    private fun create_project_entry(
        parent_path: String,
        name: String,
        directory: Boolean,
        template_id: String = "PLAIN"
    ) {
        lifecycleScope.launch {
            val result = project_manager.create_project_entry(
                project_path = project_dir.absolutePath,
                parent_path = parent_path,
                name = name,
                directory = directory,
                template_id = template_id
            )

            result.onSuccess { target ->
                val parent_path = target.parentFile?.absolutePath
                if (parent_path != null) {
                    state.expanded_paths = state.expanded_paths + parent_path
                }
                if (directory) {
                    state.expanded_paths = state.expanded_paths + target.absolutePath
                } else {
                    app_toast.show(this@editor_activity, "已创建文件", app_toast.LENGTH_SHORT)
                    request_open_file(target.absolutePath)
                }
                refresh_file_tree(parent_path)
            }.onFailure { error ->
                app_toast.show(this@editor_activity, "创建失败: ${error.message.orEmpty()}", app_toast.LENGTH_LONG)
            }
        }
    }

    private fun rename_project_entry(path: String, new_name: String) {
        lifecycleScope.launch {
            val result = project_manager.rename_project_entry(
                project_path = project_dir.absolutePath,
                path = path,
                new_name = new_name
            )

            result.onSuccess { (old_path, new_path) ->
                sync_tabs_after_rename(old_path, new_path)
                sync_file_tree_cache_after_rename(old_path, new_path)
                state.expanded_paths = state.expanded_paths
                    .map { replace_path_prefix(it, old_path, new_path) }
                    .toSet()
                save_pinned_tabs()
                refresh_file_tree(File(new_path).parentFile?.absolutePath)
            }.onFailure { error ->
                app_toast.show(this@editor_activity, "重命名失败: ${error.message.orEmpty()}", app_toast.LENGTH_LONG)
            }
        }
    }

    private fun delete_project_entry(path: String) {
        lifecycleScope.launch {
            val source = project_manager.resolve_project_entry_for_delete(project_dir.absolutePath, path).getOrElse { error ->
                app_toast.show(this@editor_activity, "删除失败: ${error.message.orEmpty()}", app_toast.LENGTH_LONG)
                return@launch
            }
            val source_path = source.absolutePath
            val dirty_tab = state.open_tabs.firstOrNull { tab ->
                is_same_or_child_path(tab.file_path, source_path) && tab.has_changes
            }
            if (dirty_tab != null) {
                app_toast.show(this@editor_activity, "请先保存或关闭未保存文件: ${dirty_tab.file_name}", app_toast.LENGTH_LONG)
                return@launch
            }

            val result = project_manager.delete_project_entry(project_dir.absolutePath, source_path)
            result.onSuccess { (deleted_path, parent_path) ->
                remove_tabs_for_deleted_entry(deleted_path)
                remove_file_tree_cache_for_path(deleted_path)
                state.expanded_paths = state.expanded_paths
                    .filterNot { is_same_or_child_path(it, deleted_path) }
                    .toSet()
                save_pinned_tabs()
                refresh_file_tree(parent_path)
            }.onFailure { error ->
                app_toast.show(this@editor_activity, "删除失败: ${error.message.orEmpty()}", app_toast.LENGTH_LONG)
            }
        }
    }

    private fun sync_file_tree_cache_after_rename(old_path: String, new_path: String) {
        val updated_cache = file_tree_children_cache.mapKeys { (path, _) -> replace_path_prefix(path, old_path, new_path) }
        file_tree_children_cache.clear()
        file_tree_children_cache.putAll(updated_cache)
        remove_file_tree_cache_for_path(new_path)
        file_tree_children_cache.remove(File(new_path).parentFile?.absolutePath)
        sync_project_watcher_dirs()
    }

    private fun remove_file_tree_cache_for_path(path: String) {
        file_tree_children_cache.keys
            .filter { is_same_or_child_path(it, path) }
            .forEach { file_tree_children_cache.remove(it) }
        sync_project_watcher_dirs()
    }

    private fun sync_tabs_after_rename(old_path: String, new_path: String) {
        state.open_tabs.forEach { tab ->
            if (is_same_or_child_path(tab.file_path, old_path)) {
                val old_tab_path = tab.file_path
                if (tab.has_changes) {
                    rename_editor_recovery_draft(this, project_dir, old_tab_path, replace_path_prefix(old_tab_path, old_path, new_path))
                }
                val updated_path = replace_path_prefix(tab.file_path, old_path, new_path)
                tab.file_path = updated_path
                tab.file_name = File(updated_path).name
                tab.status_text = relative_project_path(project_dir, File(updated_path))
            }
        }

        val current_path = state.current_file_path
        if (current_path != null && is_same_or_child_path(current_path, old_path)) {
            val updated_path = replace_path_prefix(current_path, old_path, new_path)
            state.current_file_path = updated_path
            state.current_file_name = File(updated_path).name
            state.status_text = relative_project_path(project_dir, File(updated_path))
            state.selected_tab_index = find_tab_index(updated_path)
            apply_textmate_language(updated_path)
        }
    }

    private fun remove_tabs_for_deleted_entry(deleted_path: String) {
        val active_path = state.current_file_path
        val selected_index = state.selected_tab_index
        val affected_tabs = state.open_tabs.filter { tab -> is_same_or_child_path(tab.file_path, deleted_path) }
        if (affected_tabs.isEmpty()) return

        state.open_tabs.removeAll(affected_tabs.toSet())
        affected_tabs.forEach { tab -> release_tab_editor(tab) }
        if (state.open_tabs.isEmpty()) {
            reset_editor_state()
            return
        }

        if (active_path != null && !is_same_or_child_path(active_path, deleted_path)) {
            val active_index = find_tab_index(active_path)
            if (active_index >= 0) {
                state.selected_tab_index = active_index
                return
            }
        }
        attach_editor_tab(selected_index.coerceIn(0, state.open_tabs.lastIndex), capture_current = false)
    }

    private fun toggle_read_only() {
        state.read_only = !state.read_only
    }

    private fun undo() {
        if (state.current_file_path != null) {
            editor.undo()
            update_history_state()
        }
    }

    private fun redo() {
        if (state.current_file_path != null) {
            editor.redo()
            update_history_state()
        }
    }

    private fun current_line_comment_action(): Boolean? {
        return current_line_comment_state()?.should_uncomment
    }

    private fun current_line_comment_state(): editor_line_comment_state? {
        if (state.current_file_path == null) return null

        val cursor = editor.cursor
        val text = editor.text
        val line_count = text.lineCount
        if (line_count <= 0) return null

        var start_line = cursor.leftLine.coerceIn(0, line_count - 1)
        var end_line = cursor.rightLine.coerceIn(0, line_count - 1)
        if (start_line > end_line) {
            val tmp = start_line
            start_line = end_line
            end_line = tmp
        }

        if (cursor.isSelected && cursor.rightColumn == 0 && end_line > start_line) {
            end_line--
        }

        val target_lines = (start_line..end_line).map { line -> line to text.getLineString(line) }
        return create_line_comment_state(target_lines)
    }

    private fun toggle_line_comment() {
        if (state.read_only || state.current_file_path == null) return

        val comment_state = current_line_comment_state() ?: return
        val text = editor.text

        text.beginBatchEdit()
        try {
            comment_state.lines.asReversed().forEach { (line, line_text) ->
                if (line_text.isBlank()) return@forEach

                val indent_length = line_comment_indent_length(line_text)
                if (comment_state.should_uncomment) {
                    if (!is_toggleable_line_comment(line_text)) return@forEach

                    val after_indent = line_text.substring(indent_length)
                    when {
                        after_indent.startsWith("// ") -> text.delete(line, indent_length, line, indent_length + 3)
                        after_indent.startsWith("//") -> text.delete(line, indent_length, line, indent_length + 2)
                    }
                } else if (!is_line_commented(line_text)) {
                    text.insert(line, indent_length, "// ")
                }
            }
        } finally {
            text.endBatchEdit()
        }

        update_history_state()
        editor.invalidate()
    }

    /**
     * 浮动条「展开/折叠参数」：光标所在函数调用的括号内，
     * 把参数一行一个展开（Go 多行风格，尾逗号 + 闭括号单独一行），再点折叠回单行。
     * 纯文本变换（call_args_formatter），不依赖 clangd；一次替换保证一次 undo 可整体撤销。
     */
    private fun toggle_call_args_multiline() {
        if (state.read_only) return
        val file_path = state.current_file_path ?: return
        if (!file_path.endsWith(".go", ignoreCase = true)) {
            app_toast.show(this, "仅支持 Go 文件", app_toast.LENGTH_SHORT)
            return
        }

        val text = editor.text
        val content = text.toString()
        val cursor_index = editor.cursor.left().index
        val range = call_args_formatter.find_enclosing_call(content, cursor_index)
        if (range == null) {
            app_toast.show(this, "光标不在函数调用的括号内", app_toast.LENGTH_SHORT)
            return
        }
        val transform = call_args_formatter.format_call_args(content, cursor_index) ?: return

        text.beginBatchEdit()
        try {
            text.replace(range.open_offset, range.close_offset + 1, transform.new_call_text)
        } finally {
            text.endBatchEdit()
        }

        // 恢复光标到参数区内（展开：最后一个参数行尾逗号后；折叠：闭括号前）
        val new_index = (range.open_offset + transform.new_cursor).coerceIn(0, text.length)
        val indexer = text.indexer
        editor.setSelection(indexer.getCharLine(new_index), indexer.getCharColumn(new_index))
        update_history_state()
        editor.invalidate()
    }

    private fun insert_symbol(symbol: String) {
        if (state.read_only || state.current_file_path == null) return

        val commit_text = resolve_editor_symbol_commit_text(symbol)
        if (commit_text == "\t") {
            editor.indentOrCommitTab()
        } else if (symbol in setOf("()", "{}", "[]", "\"\"", "''")) {
            // 符号栏成对插入，并让光标停在中间；IME 的括号自动补全已关闭，避免出现重复右括号。
            editor.commitText(symbol, false, false)
            editor.setSelection(
                editor.cursor.leftLine,
                editor.cursor.leftColumn - symbol.length / 2
            )
        } else {
            editor.commitText(commit_text, false, false)
        }
        update_history_state()
    }

    private fun update_search(
        query: String,
        match_case: Boolean,
        whole_word: Boolean,
        regex: Boolean
    ): Boolean {
        val result = search_controller.update_search(query, match_case, whole_word, regex)
        result.status_text?.let { status -> state.status_text = status }
        return result.has_match
    }

    private fun goto_search_result(forward: Boolean) {
        search_controller.goto_search_result(forward)?.let { status ->
            state.status_text = status
        }
    }

    private fun replace_current_match(replacement: String) {
        val result = search_controller.replace_current_match(replacement)
        if (result.changed) update_history_state()
        result.status_text?.let { status -> state.status_text = status }
    }

    private fun replace_all_matches(replacement: String) {
        val result = search_controller.replace_all_matches(replacement)
        if (result.changed) update_history_state()
        result.status_text?.let { status -> state.status_text = status }
    }

    private fun clear_search() {
        search_controller.clear_search()
    }


    private suspend fun prepare_tab_editor_for_display(tab: editor_open_tab): CodeEditor {
        return tab_lifecycle.prepare_for_display(tab)
    }

    private fun prepare_tab_editor(tab: editor_open_tab): CodeEditor {
        return tab_lifecycle.prepare(tab)
    }

    private fun set_editor_document(content: Content) {
        tab_lifecycle.set_content(editor, content)
        schedule_block_end_hints_update()
    }

    private fun set_editor_content(content: String) {
        tab_lifecycle.set_content(editor, content)
        schedule_block_end_hints_update()
    }

    private fun schedule_block_end_hints_update() {
        block_hint_job?.cancel()
        if (!::editor.isInitialized || !state.editor_settings.block_end_hints || state.current_file_path == null || !is_cpp_source_file(state.current_file_path)) {
            if (::editor.isInitialized) editor.inlayHints = null
            return
        }

        val file_path = state.current_file_path
        val tab_editor = editor
        block_hint_job = lifecycleScope.launch {
            delay(block_end_hint_update_delay_ms)
            val hints = build_editor_block_end_hints(tab_editor) ?: return@launch
            if (file_path == state.current_file_path && editor === tab_editor) {
                editor.inlayHints = hints
            }
        }
    }

    private fun apply_textmate_language(file_path: String) {
        val scope_name = cppstudio_application.instance.get_language_scope_name(file_path)
        val current_textmate_language = current_textmate_language()

        if (current_textmate_scope == scope_name && current_textmate_language != null) {
            apply_textmate_language_settings(current_textmate_language, state.editor_settings, file_path)
            editor.setEditorLanguage(current_textmate_language)
            apply_current_editor_behavior_settings(editor, state.editor_settings)
            return
        }

        val language = create_configured_textmate_language(file_path)
        editor.setEditorLanguage(language)

        current_textmate_scope = scope_name
        apply_current_editor_behavior_settings(editor, state.editor_settings)
    }

    private fun current_textmate_language(target: CodeEditor = editor): TextMateLanguage? {
        return when (val language = target.editorLanguage) {
            is TextMateLanguage -> language
            is LspLanguage -> language.wrapperLanguage as? TextMateLanguage
            else -> null
        }
    }

    private fun create_configured_textmate_language(file_path: String): TextMateLanguage {
        return (cppstudio_application.instance.create_textmate_language(file_path)
            ?: TextMateLanguage.create("source.go", false)).also { language ->
            apply_textmate_language_settings(language, state.editor_settings, file_path)
        }
    }

    private fun clear_editor_diagnostics() {
        if (::editor.isInitialized) {
            editor.diagnostics = null
        }
    }

    private fun update_history_state() {
        state.can_undo = state.current_file_path != null && editor.isUndoEnabled && editor.canUndo()
        state.can_redo = state.current_file_path != null && editor.isUndoEnabled && editor.canRedo()
    }

    private companion object {
        private const val block_end_hint_update_delay_ms = 180L
        private const val initial_editor_styles_timeout_ms = 800L
    }

}
