package com.jmwl.cppstudio

import android.app.Application
import android.content.Intent
import android.os.Build
import com.jmwl.cppstudio.activity.crash.crash_activity
import com.jmwl.cppstudio.core.logging.logger_manager
import com.jmwl.cppstudio.editor.theme.editor_theme_manager
import com.jmwl.cppstudio.plugins.plugin_manager
import com.jmwl.cppstudio.service.keep_alive_service
import com.jmwl.cppstudio.toolchain.toolchain_manager
import com.jmwl.cppstudio.toolchain.toolchain_runtime_provider
import com.jmwl.cppstudio.ui.theme.theme_manager
import com.jmwl.cppstudio.utils.app_lifecycle_observer
import io.github.rosemoe.sora.langs.textmate.TextMateLanguage
import io.github.rosemoe.sora.langs.textmate.registry.FileProviderRegistry
import io.github.rosemoe.sora.langs.textmate.registry.GrammarRegistry
import io.github.rosemoe.sora.langs.textmate.registry.provider.AssetsFileResolver
import org.eclipse.tm4e.core.internal.oniguruma.Oniguruma
import java.io.File

/** C 头文件后缀（.h / .hpp 之外仍按 C 高亮的少见后缀）。 */
private val C_HEADER_SUFFIXES = listOf(".h")

/** C++ 源码 / 头文件后缀。 */
private val CPP_SOURCE_SUFFIXES = listOf(
    ".cpp", ".cc", ".cxx", ".c++", ".cp",
    ".hpp", ".hh", ".hxx", ".h++",
    ".ipp", ".tpp", ".inl", ".ino"
)

class cppstudio_application : Application() {

    companion object {
        lateinit var instance: cppstudio_application
    }

    private var textmate_initialized = false

    var keep_alive_service_: keep_alive_service? = null

    override fun onCreate() {
        super.onCreate()
        instance = this

        theme_manager.init(this)
        logger_manager.init(this)
        logger_manager.is_debug = false
        logger_manager.enable_file_log = true
        logger_manager.errors_only = true
        com.jmwl.cppstudio.feedback.feedback_settings_store.init(this)

        val create_dirs = listOf(
            File(filesDir, "home"),
            File(filesDir, "home/cppstudio/proot-tmps"),
            File(filesDir, "home/cppstudio/alpine-rootfs")
        )
        for (dir in create_dirs) {
            if (!dir.exists()) dir.mkdirs()
        }

        toolchain_runtime_provider.init(
            context = this,
            cppstudio_dir = File(filesDir, "home/cppstudio"),
            home_dir = File(filesDir, "home"),
            rootfs_dir = File(filesDir, "home/cppstudio/alpine-rootfs"),
            proot_tmp_dir = File(filesDir, "home/cppstudio/proot-tmps")
        )

        // 软件源镜像：读取持久化的 apk 镜像选择，并同步进 rootfs 的 /etc/apk/repositories
        com.jmwl.cppstudio.toolchain.apk_mirror_store.init(this)

        plugin_manager.init(this)
        // 内置插件随 APK 发布：启动时释放到插件目录（版本升级覆盖，启用状态不动）
        plugin_manager.release_builtin_plugins(this)

        init_textmate()
        start_keep_alive_service()
        app_lifecycle_observer.init(this)
        setup_uncaught_exception_handler()
    }

    private fun init_textmate() {
        if (textmate_initialized) return

        try {
            FileProviderRegistry.getInstance().addFileProvider(AssetsFileResolver(assets))
            configure_textmate_regex_engine()

            editor_theme_manager.init(this)

            GrammarRegistry.getInstance().loadGrammars("textmate/languages.json")

            textmate_initialized = true
        } catch (e: Exception) {
            logger_manager.e("cppstudio_application", "Failed to init TextMate: ${e.message}", e)
        }
    }

    private fun configure_textmate_regex_engine() {
        runCatching {
            Oniguruma().setUseNativeOniguruma(true)
        }.onFailure { error ->
            logger_manager.e("cppstudio_application", "TextMate native Oniguruma unavailable: ${error.message}", error)
        }
    }

    fun get_language_scope_name(file_name: String): String {
        val lower = file_name.lowercase()
        return when {
            lower.endsWith(".c") -> "source.c"
            // .h 既可能是 C 也可能是 C++ 头文件：按 C++ 高亮（C++ 语法是 C 的超集）
            C_HEADER_SUFFIXES.any { lower.endsWith(it) } -> "source.c"
            CPP_SOURCE_SUFFIXES.any { lower.endsWith(it) } -> "source.cpp"
            lower.endsWith(".json") -> "source.json"
            lower == "cmakelists.txt" || lower.endsWith(".cmake") -> "source.cmake"
            lower.equals(".env", ignoreCase = true) ||
                lower.startsWith(".env.") -> "source.dotenv"
            // 其余（Makefile / 脚本 / 配置）按 C++ 处理：注释与字符串高亮至少可用
            else -> "source.cpp"
        }
    }

    fun create_textmate_language(file_name: String): TextMateLanguage? {
        if (!textmate_initialized) return null

        val scope_name = get_language_scope_name(file_name)
        return try {
            TextMateLanguage.create(scope_name, false)
        } catch (e: Exception) {
            logger_manager.e("cppstudio_application", "Failed to create TextMate language $scope_name: ${e.message}")
            null
        }
    }

    private fun setup_uncaught_exception_handler() {
        Thread.setDefaultUncaughtExceptionHandler { _, throwable ->
            handle_crash(throwable)
        }
    }

    private fun handle_crash(throwable: Throwable) {
        try {
            val log = throwable.javaClass.simpleName + ": " + (throwable.message ?: "Unknown error")
            val stack = get_stack_trace_string(throwable)
            val intent = Intent(this, crash_activity::class.java)
            intent.putExtra("crash_log", log)
            intent.putExtra("crash_stack", stack)
            // 只叠加不清栈：保留崩溃前的页面，崩溃页「返回上页」时系统会按需重建上一页
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
        } catch (e: Exception) {
            logger_manager.e("cppstudio_application", "Failed to show crash activity: ${e.message}")
        }
    }

    private fun get_stack_trace_string(throwable: Throwable): String {
        val sw = java.io.StringWriter()
        val pw = java.io.PrintWriter(sw)
        throwable.printStackTrace(pw)
        return sw.toString()
    }

    private fun start_keep_alive_service() {
        try {
            val intent = Intent(this, keep_alive_service::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
        } catch (e: Exception) {
            logger_manager.e("cppstudio_application", "Failed to start service: ${e.message}")
        }
    }
}
