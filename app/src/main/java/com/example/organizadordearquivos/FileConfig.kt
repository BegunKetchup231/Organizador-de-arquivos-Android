package com.example.organizadordearquivos // O pacote deve ser o mesmo

// Usamos um 'object' para criar um singleton, perfeito para guardar constantes
object FileConfig {

    // A sua lista de categorias agora vive aqui
    val FILE_CATEGORIES = mapOf(
        ".jpg" to "Fotos", ".jpeg" to "Fotos", ".png" to "Fotos", ".gif" to "Fotos",
        ".bmp" to "Fotos", ".webp" to "Fotos", ".tiff" to "Fotos", ".tif" to "Fotos",
        ".heic" to "Fotos",

        ".mp4" to "Videos", ".mkv" to "Videos", ".avi" to "Videos", ".mov" to "Videos",
        ".wmv" to "Videos", ".flv" to "Videos", ".webm" to "Videos", ".3gp" to "Videos",

        ".pdf" to "Documentos", ".doc" to "Documentos", ".docx" to "Documentos",
        ".xls" to "Documentos", ".xlsx" to "Documentos", ".ppt" to "Documentos",
        ".pptx" to "Documentos", ".txt" to "Documentos", ".rtf" to "Documentos",
        ".odt" to "Documentos", ".ods" to "Documentos", ".odp" to "Documentos",
        ".csv" to "Documentos", ".md" to "Documentos",

        ".mp3" to "Audio", ".wav" to "Audio", ".flac" to "Audio", ".aac" to "Audio",
        ".ogg" to "Audio", ".wma" to "Audio", ".m4a" to "Audio",

        ".zip" to "Arquivos_Comuns", ".rar" to "Arquivos_Comuns", ".7z" to "Arquivos_Comuns",
        ".exe" to "Arquivos_Comuns", ".apk" to "Arquivos_Comuns", ".iso" to "Arquivos_Comuns",
        ".tar" to "Arquivos_Comuns", ".gz" to "Arquivos_Comuns", ".tgz" to "Arquivos_Comuns",
        ".bz2" to "Arquivos_Comuns", ".xz" to "Arquivos_Comuns", ".msi" to "Arquivos_Comuns",
        ".dmg" to "Arquivos_Comuns"

    )
}