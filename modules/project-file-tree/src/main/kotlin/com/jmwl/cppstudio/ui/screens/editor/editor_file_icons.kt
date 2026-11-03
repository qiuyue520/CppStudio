package com.jmwl.cppstudio.ui.screens.editor

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.DataObject
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Javascript
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Summarize
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Workspaces
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

data class editor_file_icon_info(
    val icon: ImageVector,
    val tint: Color
)

fun editor_file_icon(name: String): editor_file_icon_info {
    val lower_name = name.lowercase()
    return when {
        lower_name.endsWith(".cpp") || lower_name.endsWith(".cc") || lower_name.endsWith(".cxx") ||
            lower_name.endsWith(".c++") || lower_name.endsWith(".cp") ->
            editor_file_icon_info(Icons.Filled.Code, Color(0xFF2563EB))
        lower_name.endsWith(".hpp") || lower_name.endsWith(".hh") || lower_name.endsWith(".hxx") ||
            lower_name.endsWith(".h++") || lower_name.endsWith(".ipp") || lower_name.endsWith(".tpp") ||
            lower_name.endsWith(".inl") ->
            editor_file_icon_info(Icons.Filled.Code, Color(0xFF7C3AED))
        lower_name.endsWith(".c") || lower_name.endsWith(".h") ->
            editor_file_icon_info(Icons.Filled.Terminal, Color(0xFF0891B2))
        lower_name == "cmakelists.txt" || lower_name.endsWith(".cmake") ->
            editor_file_icon_info(Icons.Filled.Summarize, Color(0xFF0EA5E9))
        lower_name == "makefile" || lower_name == "gnumakefile" || lower_name.endsWith(".mk") ->
            editor_file_icon_info(Icons.Filled.Workspaces, Color(0xFF16A34A))
        lower_name.endsWith(".o") || lower_name.endsWith(".a") || lower_name.endsWith(".so") ->
            editor_file_icon_info(Icons.Filled.DataObject, Color(0xFF0F766E))
        lower_name.endsWith(".json") -> editor_file_icon_info(Icons.Filled.DataObject, Color(0xFFD97706))
        lower_name.endsWith(".yaml") || lower_name.endsWith(".yml") ->
            editor_file_icon_info(Icons.Filled.Summarize, Color(0xFF2563EB))
        lower_name.endsWith(".md") || lower_name.endsWith(".markdown") ->
            editor_file_icon_info(Icons.AutoMirrored.Filled.Article, Color(0xFF2563EB))
        lower_name.endsWith(".xml") || lower_name.endsWith(".html") || lower_name.endsWith(".htm") ->
            editor_file_icon_info(Icons.Filled.Code, Color(0xFFEA580C))
        lower_name.endsWith(".css") || lower_name.endsWith(".scss") || lower_name.endsWith(".less") ->
            editor_file_icon_info(Icons.Filled.Palette, Color(0xFFDB2777))
        lower_name.endsWith(".js") || lower_name.endsWith(".mjs") || lower_name.endsWith(".cjs") ->
            editor_file_icon_info(Icons.Filled.Javascript, Color(0xFFCA8A04))
        lower_name.endsWith(".ts") || lower_name.endsWith(".tsx") ->
            editor_file_icon_info(Icons.Filled.Code, Color(0xFF0284C7))
        lower_name.endsWith(".png") || lower_name.endsWith(".jpg") || lower_name.endsWith(".jpeg") ||
            lower_name.endsWith(".gif") || lower_name.endsWith(".webp") || lower_name.endsWith(".svg") ->
            editor_file_icon_info(Icons.Filled.Image, Color(0xFFDB2777))
        lower_name.endsWith(".mp3") || lower_name.endsWith(".wav") || lower_name.endsWith(".aac") ->
            editor_file_icon_info(Icons.Filled.MusicNote, Color(0xFF7C3AED))
        lower_name.endsWith(".mp4") || lower_name.endsWith(".mov") || lower_name.endsWith(".avi") ->
            editor_file_icon_info(Icons.Filled.Movie, Color(0xFFDC2626))
        lower_name.endsWith(".zip") || lower_name.endsWith(".tar") || lower_name.endsWith(".gz") ||
            lower_name.endsWith(".7z") || lower_name.endsWith(".rar") ->
            editor_file_icon_info(Icons.Filled.FolderZip, Color(0xFFD97706))
        lower_name.endsWith(".pdf") -> editor_file_icon_info(Icons.Filled.PictureAsPdf, Color(0xFFDC2626))
        lower_name.endsWith(".sql") || lower_name.endsWith(".db") || lower_name.endsWith(".sqlite") ->
            editor_file_icon_info(Icons.Filled.Storage, Color(0xFF65A30D))
        lower_name.endsWith(".txt") || lower_name.endsWith(".log") || lower_name.endsWith(".properties") ||
            lower_name.endsWith(".toml") || lower_name.endsWith(".ini") ->
            editor_file_icon_info(Icons.Filled.Description, Color(0xFF64748B))
        lower_name.endsWith(".sh") || lower_name.endsWith(".bash") || lower_name.endsWith(".zsh") ->
            editor_file_icon_info(Icons.Filled.Terminal, Color(0xFF334155))
        else -> editor_file_icon_info(Icons.AutoMirrored.Filled.InsertDriveFile, Color(0xFF64748B))
    }
}
