package com.xiaonai.cppstudio.ui.screens.editor

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xiaonai.cppstudio.editor.model.editor_settings_state
import com.xiaonai.cppstudio.editor.settings.import_editor_font
import com.xiaonai.cppstudio.editor.settings.load_editor_settings
import com.xiaonai.cppstudio.editor.settings.save_editor_settings
import com.xiaonai.cppstudio.ui.components.sub_page_top_bar
import com.xiaonai.cppstudio.ui.theme.app_theme_provider
import com.xiaonai.cppstudio.ui.toast.app_toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun editor_settings_screen(
    on_back: () -> Unit,
    on_theme_click: () -> Unit
) {
    val context = LocalContext.current
    val colors = app_theme_provider.colors
    val scope = rememberCoroutineScope()
    var settings by remember { mutableStateOf(load_editor_settings(context)) }

    fun update_settings(next_settings: editor_settings_state) {
        settings = next_settings
        save_editor_settings(context, next_settings)
    }

    /** 用当前地址/密钥发一句样例，验证翻译服务能不能通。 */
    fun test_translation() {
        val endpoint = settings.clangd_translation_endpoint.trim().trimEnd('/')
        if (endpoint.isBlank()) {
            app_toast.show(context, "先填「翻译服务地址」再测试", app_toast.LENGTH_SHORT)
            return
        }
        app_toast.show(context, "正在测试翻译服务…", app_toast.LENGTH_SHORT)
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { probe_translation_service(endpoint, settings.clangd_translation_api_key) }
            }
            result.onSuccess { text ->
                app_toast.show(context, "服务可用：$text", app_toast.LENGTH_LONG)
            }.onFailure { error ->
                app_toast.show(context, "连接失败：${error.message.orEmpty()}", app_toast.LENGTH_LONG)
            }
        }
    }

    val import_font_launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult

        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { import_editor_font(context, uri) }
            }
            result.onSuccess { path ->
                update_settings(
                    settings.copy(
                        font_family = "imported",
                        imported_font_path = path
                    )
                )
                app_toast.show(context, "字体已导入", app_toast.LENGTH_SHORT)
            }.onFailure { error ->
                app_toast.show(context, "字体导入失败: ${error.message.orEmpty()}", app_toast.LENGTH_LONG)
            }
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = Color.Transparent,
        topBar = { sub_page_top_bar("编辑器设置", on_back) }
    ) { padding_values ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding_values)
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            editor_settings_panel(
                settings = settings,
                on_settings_change = ::update_settings,
                on_import_font = { import_font_launcher.launch("font/*") },
                on_open_theme_settings = on_theme_click,
                on_test_translation = ::test_translation,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

/**
 * 给配置好的翻译服务发一句样例，验证地址 + 密钥是否可用。
 * 与 `clangd_documentation_translator` 用同一套接口约定：POST {地址}/v1/translate。
 * 成功时返回一句提示（含译文片段），失败抛出带原因的异常。
 */
private fun probe_translation_service(endpoint: String, api_key: String): String {
    val url = java.net.URL("$endpoint/v1/translate")
    val connection = url.openConnection() as java.net.HttpURLConnection
    try {
        connection.requestMethod = "POST"
        connection.connectTimeout = 8000
        connection.readTimeout = 20000
        connection.doOutput = true
        connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
        if (api_key.isNotBlank()) {
            connection.setRequestProperty("X-CppStudio-Translation-Key", api_key)
        }
        val payload = org.json.JSONObject()
            .put("text", "Returns the number of elements in the container.")
            .put("target_language", "zh-CN")
            .put("kind", "clangd-hover")
            .toString()
            .toByteArray(Charsets.UTF_8)
        connection.setFixedLengthStreamingMode(payload.size)
        connection.outputStream.use { it.write(payload) }

        val status = connection.responseCode
        val stream = if (status in 200..299) connection.inputStream else connection.errorStream
        val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
        if (status !in 200..299) {
            throw IllegalStateException("HTTP $status ${body.take(120)}")
        }
        val translated = org.json.JSONObject(body).optString("translated_text").trim()
        if (translated.isBlank()) throw IllegalStateException("返回里没有 translated_text")
        return translated.take(24)
    } finally {
        connection.disconnect()
    }
}
