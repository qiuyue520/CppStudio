package com.xiaonai.cppstudio.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

/** GitHub Releases 上解析出的可更新版本信息 */
data class app_update_info(
    val tag: String,
    val title: String,
    val release_notes: String,
    val apk_url: String,
    val apk_name: String,
    val size_bytes: Long
)

sealed interface app_update_check_result {
    data class UpdateAvailable(val info: app_update_info) : app_update_check_result
    data object UpToDate : app_update_check_result
    data class Error(val message: String) : app_update_check_result
}

/** 更新流程状态（驱动弹窗 UI） */
sealed interface app_update_step {
    data object Idle : app_update_step
    data class Available(val info: app_update_info) : app_update_step
    data class Downloading(val percent: Int, val received: Long, val total: Long) : app_update_step
    data class Downloaded(val file: File, val info: app_update_info) : app_update_step
}

/**
 * 基于 GitHub Releases 的应用内自动更新：
 * - [check] 拉取 latest release，与当前 versionName 比较
 * - [start_download] 带进度下载 APK 到应用外部私有目录
 * - [install] 通过 FileProvider 唤起系统安装器
 *
 * 发布约定：GitHub Release 的 tag 形如 `v1.0.2`，附件里放一个 .apk 文件。
 */
class app_update_controller(private val context: Context) {

    companion object {
        /** 更新源仓库（owner/name）；在 cppstudio_backend_config.GITHUB_REPO 配置，留空 = 关闭更新检查。 */
        val REPO: String
            get() = com.xiaonai.cppstudio.editor.config.cppstudio_backend_config.GITHUB_REPO
        private const val PREFS_KEY_DISMISSED_VERSION = "dismissed_version"
        private val tag_version_regex = Regex("""v?(\d+(?:\.\d+)+)""")

        /** 当前版本名（如 "1.0.1"） */
        fun current_version_name(context: Context): String {
            return runCatching {
                context.packageManager.getPackageInfo(context.packageName, 0).versionName
            }.getOrNull() ?: ""
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val http = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    /** 不跟随跳转的客户端：用来读 `releases/latest` 的 302 Location，拿到当前 tag。 */
    private val no_redirect_http = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .followRedirects(false)
        .followSslRedirects(false)
        .build()
    private val prefs = context.getSharedPreferences("app_update_prefs", Context.MODE_PRIVATE)

    var step: app_update_step by mutableStateOf(app_update_step.Idle)
        private set

    private var download_job: Job? = null

    /**
     * 检查 GitHub 最新 release；结果同时写入 [step]（有更新时）。
     * [auto] 为启动时的静默检查：用户点过「以后再说」的版本不再弹窗；
     * 手动检查（设置页）始终弹出。
     */
    suspend fun check(auto: Boolean = false): app_update_check_result = withContext(Dispatchers.IO) {
        val local_version = current_version_name(context)
        runCatching {
            if (REPO.isBlank()) {
                throw IllegalStateException(
                    "未配置更新源：在 cppstudio_backend_config.GITHUB_REPO 填入你的仓库（owner/name）"
                )
            }
            // 优先走网页（不消耗 GitHub API 配额）：未认证的 REST API 只有 60 次/小时，超了就是 403
            val info = runCatching { fetch_latest_via_web() }.getOrNull() ?: fetch_latest_via_api()
            if (!is_newer_version(info.tag, local_version)) {
                app_update_check_result.UpToDate
            } else if (auto && is_version_dismissed(info.tag)) {
                app_update_check_result.UpToDate
            } else {
                step = app_update_step.Available(info)
                app_update_check_result.UpdateAvailable(info)
            }
        }.getOrElse { error ->
            app_update_check_result.Error(error.message ?: "网络请求失败")
        }
    }

    /** 用户对某版本点了「以后再说」：记录版本号，之后启动自动检查不再弹该版本 */
    fun dismiss_update(tag: String) {
        prefs.edit().putString(PREFS_KEY_DISMISSED_VERSION, normalize_version(tag)).apply()
    }

    private fun is_version_dismissed(tag: String): Boolean {
        val dismissed = prefs.getString(PREFS_KEY_DISMISSED_VERSION, null) ?: return false
        return normalize_version(tag) == dismissed && dismissed.isNotBlank()
    }

    /** `v1.0.13` / `1.0.13` 统一成可比的版本号，解析失败时退回原始 tag */
    private fun normalize_version(tag: String): String =
        tag_version_regex.find(tag)?.groupValues?.get(1) ?: tag.trim()

    fun start_download(info: app_update_info) {
        if (download_job?.isActive == true) return
        download_job = scope.launch {
            runCatching {
                val request = Request.Builder().url(info.apk_url).build()
                http.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) throw IllegalStateException("下载失败 (${response.code})")
                    val input = response.body!!.byteStream()
                    val total = response.body!!.contentLength().takeIf { it > 0 } ?: info.size_bytes
                    val dir = (context.getExternalFilesDir("update") ?: File(context.filesDir, "update"))
                        .apply { mkdirs() }
                    val file = File(dir, "cppstudio-update.apk")
                    file.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var received = 0L
                        var last_report = 0L
                        while (true) {
                            val read = input.read(buffer)
                            if (read == -1) break
                            output.write(buffer, 0, read)
                            received += read
                            // 每 512KB 或完成时回报一次进度，避免重组过频
                            if (received - last_report >= 512 * 1024 || received >= total) {
                                last_report = received
                                withContext(Dispatchers.Main) {
                                    step = app_update_step.Downloading(
                                        percent = if (total > 0) ((received * 100) / total).toInt() else 0,
                                        received = received,
                                        total = total
                                    )
                                }
                            }
                        }
                    }
                    file
                }
            }.fold(
                onSuccess = { file -> step = app_update_step.Downloaded(file, info) },
                onFailure = { error ->
                    step = app_update_step.Idle
                    on_error_message = error.message ?: "下载失败"
                }
            )
        }
    }

    fun cancel_download() {
        download_job?.cancel()
        download_job = null
        step = app_update_step.Idle
    }

    /** 最近一次错误消息（供调用方 toast 后清空） */
    var on_error_message: String? by mutableStateOf(null)
        private set

    fun clear_error() {
        on_error_message = null
    }

    fun reset() {
        if (download_job?.isActive != true) {
            step = app_update_step.Idle
        }
    }

    fun install(file: File) {
        // 先确认「允许安装未知应用」权限
        if (!context.packageManager.canRequestPackageInstalls()) {
            runCatching {
                context.startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:${context.packageName}")
                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
            return
        }
        runCatching {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.file_provider", file)
            context.startActivity(
                Intent(Intent.ACTION_VIEW).apply {
                    addFlags(
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or
                            Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_SINGLE_TOP
                    )
                    setDataAndType(uri, "application/vnd.android.package-archive")
                }
            )
        }
    }

    fun shutdown() {
        scope.cancel()
    }

    /**
     * 不走 GitHub REST API 的检查路径。
     *
     * 未认证的 `api.github.com` 按 IP 限流 60 次/小时，超了直接 403（手机上因为走运营商
     * NAT、出口 IP 共享，很容易踩到）。这里改成纯网页请求：
     *   1. `releases/latest` 会 302 到 `/releases/tag/<tag>`，读 Location 拿 tag
     *   2. `releases/expanded_assets/<tag>` 是发布页里资源列表的 HTML 片段，从中取 .apk 链接
     * 两条都不消耗 API 配额，也不会 403。
     */
    private fun fetch_latest_via_web(): app_update_info? {
        val user_agent = "CppStudio/${current_version_name(context)}"

        val latest = Request.Builder()
            .url("https://github.com/$REPO/releases/latest")
            .header("User-Agent", user_agent)
            .build()
        val tag = no_redirect_http.newCall(latest).execute().use { response ->
            if (response.code !in 300..399) return null
            val location = response.header("Location").orEmpty()
            Regex("/releases/tag/([^/?#]+)").find(location)?.groupValues?.get(1)
        } ?: return null

        val assets_page = Request.Builder()
            .url("https://github.com/$REPO/releases/expanded_assets/$tag")
            .header("User-Agent", user_agent)
            .build()
        val html = http.newCall(assets_page).execute().use { response ->
            if (!response.isSuccessful) return null
            response.body?.string().orEmpty()
        }

        val href = Regex("href=\"([^\"]*/releases/download/[^\"]+\\.apk)\"")
            .find(html)?.groupValues?.get(1) ?: return null
        val apk_url = if (href.startsWith("http")) href else "https://github.com$href"
        val apk_name = apk_url.substringAfterLast('/')

        return app_update_info(
            tag = tag,
            title = "CppStudio $tag",
            release_notes = "前往发布页面查看更新说明：https://github.com/$REPO/releases/tag/$tag",
            apk_url = apk_url,
            apk_name = apk_name,
            size_bytes = 0L
        )
    }

    /** 网页路径失败时的兜底：仍然用 REST API（可能撞上 403 限流）。 */
    private fun fetch_latest_via_api(): app_update_info {
        val request = Request.Builder()
            .url("https://api.github.com/repos/$REPO/releases/latest")
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "CppStudio/${current_version_name(context)}")
            .build()
        http.newCall(request).execute().use { response ->
            if (response.code == 403 || response.code == 429) {
                throw IllegalStateException(
                    "GitHub 接口被限流（HTTP ${response.code}），网络出口 IP 共享时常见，稍后再试即可"
                )
            }
            if (!response.isSuccessful) {
                throw IllegalStateException("GitHub 返回 ${response.code}")
            }
            val body = response.body?.string().orEmpty()
            return parse_release(body) ?: throw IllegalStateException("最新 Release 未找到 APK 安装包")
        }
    }

    private fun parse_release(body: String): app_update_info? {
        if (body.isBlank()) return null
        val obj = JSONObject(body)
        if (obj.optBoolean("draft", false) || obj.optBoolean("prerelease", false)) return null
        val tag = obj.optString("tag_name").orEmpty()
        if (tag.isBlank()) return null
        val assets = obj.optJSONArray("assets") ?: return null
        for (i in 0 until assets.length()) {
            val asset = assets.optJSONObject(i) ?: continue
            val name = asset.optString("name").orEmpty()
            if (!name.endsWith(".apk", ignoreCase = true)) continue
            val url = asset.optString("browser_download_url").orEmpty()
            if (url.isBlank()) continue
            return app_update_info(
                tag = tag,
                title = obj.optString("name").ifBlank { tag },
                release_notes = obj.optString("body").orEmpty(),
                apk_url = url,
                apk_name = name,
                size_bytes = asset.optLong("size", 0L)
            )
        }
        return null
    }

    /** 比较 `v1.2.3` 风格版本号：remote 是否比 local 新 */
    private fun is_newer_version(remote_tag: String, local_version: String): Boolean {
        val remote = tag_version_regex.find(remote_tag)?.groupValues?.get(1) ?: return false
        val remote_parts = remote.split('.').map { it.filter(Char::isDigit).toIntOrNull() ?: 0 }
        val local_parts = local_version.split('.').map { it.filter(Char::isDigit).toIntOrNull() ?: 0 }
        for (index in 0 until maxOf(remote_parts.size, local_parts.size)) {
            val r = remote_parts.getOrElse(index) { 0 }
            val l = local_parts.getOrElse(index) { 0 }
            if (r != l) return r > l
        }
        return false
    }
}
