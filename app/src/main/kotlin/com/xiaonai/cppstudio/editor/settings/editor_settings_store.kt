package com.xiaonai.cppstudio.editor.settings

import com.xiaonai.cppstudio.editor.model.editor_settings_state

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.io.File

internal const val editor_settings_prefs_name = "editor_settings"

internal fun load_editor_settings(context: Context): editor_settings_state {
    val defaults = editor_settings_state()
    val prefs = context.getSharedPreferences(editor_settings_prefs_name, Context.MODE_PRIVATE)
    val imported_font_path = prefs.getString("imported_font_path", defaults.imported_font_path).orEmpty()
    val translation_prefs = translation_key_prefs(context)
    val translation_endpoint = prefs.getString(
        "clangd_translation_endpoint",
        defaults.clangd_translation_endpoint
    ).orEmpty().ifBlank { defaults.clangd_translation_endpoint }
    val translation_api_key = translation_prefs.getString(
        "clangd_translation_api_key",
        defaults.clangd_translation_api_key
    ).orEmpty().ifBlank { defaults.clangd_translation_api_key }

    // 一次性迁移：clangd_hover 默认值由 false 改为 true（光标停在函数名上显示函数介绍）。
    // 仅在本次升级首次运行时强制设 true，之后用户在设置里手动改的值会被尊重。
    if (!prefs.getBoolean("clangd_hover_default_migrated", false)) {
        prefs.edit().putBoolean("clangd_hover", defaults.clangd_hover).putBoolean("clangd_hover_default_migrated", true).apply()
    }

    return editor_settings_state(
        word_wrap = prefs.getBoolean("word_wrap", defaults.word_wrap),
        line_numbers = prefs.getBoolean("line_numbers", defaults.line_numbers),
        bracket_pair_highlight = prefs.getBoolean("bracket_pair_highlight", defaults.bracket_pair_highlight),
        current_line_highlight = prefs.getBoolean("current_line_highlight", defaults.current_line_highlight),
        block_lines = prefs.getBoolean("block_lines", defaults.block_lines),
        block_end_hints = prefs.getBoolean("block_end_hints", defaults.block_end_hints),
        sticky_scroll = prefs.getBoolean("sticky_scroll", defaults.sticky_scroll),
        whitespace_symbols = prefs.getBoolean("whitespace_symbols", prefs.getBoolean("non_printable", defaults.whitespace_symbols)),
        line_separator = prefs.getBoolean("line_separator", prefs.getBoolean("non_printable", defaults.line_separator)),
        pinch_zoom = prefs.getBoolean("pinch_zoom", defaults.pinch_zoom),
        cursor_blink = prefs.getBoolean("cursor_blink", defaults.cursor_blink),
        auto_indent = prefs.getBoolean("auto_indent", defaults.auto_indent),
        auto_completion = prefs.getBoolean("auto_completion", defaults.auto_completion),
        clangd_enabled = prefs.getBoolean("clangd_enabled", defaults.clangd_enabled),
        clangd_completion = prefs.getBoolean("clangd_completion", defaults.clangd_completion),
        clangd_signature_help = prefs.getBoolean("clangd_signature_help", defaults.clangd_signature_help),
        clangd_document_highlight = prefs.getBoolean("clangd_document_highlight", defaults.clangd_document_highlight),
        clangd_formatting = prefs.getBoolean("clangd_formatting", defaults.clangd_formatting),
        clangd_hover = prefs.getBoolean("clangd_hover", defaults.clangd_hover),
        clangd_translate_documentation = prefs.getBoolean("clangd_translate_documentation", defaults.clangd_translate_documentation),
        clangd_translation_endpoint = translation_endpoint,
        clangd_translation_api_key = translation_api_key,
        pack_auto_install = prefs.getBoolean("pack_auto_install", defaults.pack_auto_install),
        font_ligatures = prefs.getBoolean("font_ligatures", defaults.font_ligatures),
        font_size = prefs.getFloat("font_size", defaults.font_size).coerceIn(10f, 24f),
        tab_size = prefs.getInt("tab_size", defaults.tab_size).coerceIn(2, 8),
        font_family = normalize_editor_font_family(
            font_family = prefs.getString("font_family", defaults.font_family).orEmpty(),
            imported_font_path = imported_font_path
        ),
        imported_font_path = imported_font_path
    )
}

internal fun save_editor_settings(context: Context, settings: editor_settings_state) {
    context.getSharedPreferences(editor_settings_prefs_name, Context.MODE_PRIVATE)
        .edit()
        .putBoolean("word_wrap", settings.word_wrap)
        .putBoolean("line_numbers", settings.line_numbers)
        .putBoolean("bracket_pair_highlight", settings.bracket_pair_highlight)
        .putBoolean("current_line_highlight", settings.current_line_highlight)
        .putBoolean("block_lines", settings.block_lines)
        .putBoolean("block_end_hints", settings.block_end_hints)
        .putBoolean("sticky_scroll", settings.sticky_scroll)
        .putBoolean("whitespace_symbols", settings.whitespace_symbols)
        .putBoolean("line_separator", settings.line_separator)
        .putBoolean("pinch_zoom", settings.pinch_zoom)
        .putBoolean("cursor_blink", settings.cursor_blink)
        .putBoolean("auto_indent", settings.auto_indent)
        .putBoolean("clangd_enabled", settings.clangd_enabled)
        .putBoolean("clangd_completion", settings.clangd_completion)
        .putBoolean("clangd_signature_help", settings.clangd_signature_help)
        .putBoolean("clangd_document_highlight", settings.clangd_document_highlight)
        .putBoolean("clangd_formatting", settings.clangd_formatting)
        .putBoolean("clangd_hover", settings.clangd_hover)
        .putBoolean("clangd_translate_documentation", settings.clangd_translate_documentation)
        .putString("clangd_translation_endpoint", settings.clangd_translation_endpoint.trim())
        .putBoolean("clangd_migrated", true)
        .putBoolean("pack_auto_install", settings.pack_auto_install)
        .putBoolean("font_ligatures", settings.font_ligatures)
        .putFloat("font_size", settings.font_size)
        .putInt("tab_size", settings.tab_size)
        .putString("font_family", settings.font_family)
        .putString("imported_font_path", settings.imported_font_path)
        .apply()

    val translation_prefs = translation_key_prefs(context)
    if (translation_prefs.getString("clangd_translation_api_key", "") != settings.clangd_translation_api_key) {
        translation_prefs
            .edit()
            .putString("clangd_translation_api_key", settings.clangd_translation_api_key)
            .apply()
    }
}

private fun normalize_editor_font_family(font_family: String, imported_font_path: String): String {
    return when (font_family) {
        "jetbrains_mono", "roboto" -> font_family
        "imported" -> if (imported_font_path.isNotBlank() && File(imported_font_path).exists()) {
            font_family
        } else {
            editor_settings_state().font_family
        }
        else -> editor_settings_state().font_family
    }
}

@Volatile
private var cached_translation_key_prefs: android.content.SharedPreferences? = null

private fun translation_key_prefs(context: Context): android.content.SharedPreferences {
    cached_translation_key_prefs?.let { return it }
    val prefs = runCatching {
        EncryptedSharedPreferences.create(
            context,
            "clangd_translation_settings",
            MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build(),
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }.getOrElse {
        context.getSharedPreferences("clangd_translation_settings_fallback", Context.MODE_PRIVATE)
    }
    cached_translation_key_prefs = prefs
    return prefs
}
