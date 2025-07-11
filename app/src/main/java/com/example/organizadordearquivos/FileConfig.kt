package com.example.organizadordearquivos // O pacote deve ser o mesmo que o seu

// Usamos um 'object' para criar um singleton, perfeito para guardar constantes
object FileConfig {

    val FILE_CATEGORIES = mapOf(
        // Categoria Fotos: Formatos de imagem, incluindo raw e vetoriais
        ".jpg" to "Fotos", ".jpeg" to "Fotos", ".png" to "Fotos", ".gif" to "Fotos",
        ".bmp" to "Fotos", ".webp" to "Fotos", ".tiff" to "Fotos", ".tif" to "Fotos",
        ".heic" to "Fotos", ".heif" to "Fotos", ".svg" to "Fotos", ".eps" to "Fotos",
        ".ai" to "Fotos", ".psd" to "Fotos", ".raw" to "Fotos", ".cr2" to "Fotos",
        ".nef" to "Fotos", ".orf" to "Fotos", ".arw" to "Fotos", ".dng" to "Fotos",
        ".ico" to "Fotos", ".jp2" to "Fotos", ".jps" to "Fotos", ".jfif" to "Fotos",

        // Categoria Vídeos: Formatos de vídeo, incluindo codecs e streaming
        ".mp4" to "Videos", ".mkv" to "Videos", ".avi" to "Videos", ".mov" to "Videos",
        ".wmv" to "Videos", ".flv" to "Videos", ".webm" to "Videos", ".3gp" to "Videos",
        ".mpg" to "Videos", ".mpeg" to "Videos", ".m4v" to "Videos", ".rmvb" to "Videos",
        ".vob" to "Videos", ".ogv" to "Videos", ".ts" to "Videos", ".f4v" to "Videos",

        // Categoria Documentos: Formatos de texto, planilhas, apresentações e eBooks
        ".pdf" to "Documentos", ".doc" to "Documentos", ".docx" to "Documentos",
        ".xls" to "Documentos", ".xlsx" to "Documentos", ".ppt" to "Documentos",
        ".pptx" to "Documentos", ".txt" to "Documentos", ".rtf" to "Documentos",
        ".odt" to "Documentos", ".ods" to "Documentos", ".odp" to "Documentos",
        ".csv" to "Documentos", ".md" to "Documentos", ".epub" to "Documentos",
        ".mobi" to "Documentos", ".azw" to "Documentos", ".azw3" to "Documentos",
        ".pps" to "Documentos", ".ppsx" to "Documentos", ".xml" to "Documentos",
        ".json" to "Documentos", ".log" to "Documentos", ".pages" to "Documentos",
        ".numbers" to "Documentos", ".key" to "Documentos", ".wps" to "Documentos",

        // Categoria Áudio: Formatos de música, gravações e outros sons
        ".mp3" to "Audio", ".wav" to "Audio", ".flac" to "Audio", ".aac" to "Audio",
        ".ogg" to "Audio", ".wma" to "Audio", ".m4a" to "Audio", ".opus" to "Audio",
        ".aiff" to "Audio", ".alac" to "Audio", ".amr" to "Audio", ".mid" to "Audio",
        ".midi" to "Audio",

        // Categoria Arquivos Comuns: Compactados, executáveis, imagens de disco e outros
        ".zip" to "Arquivos_Comuns", ".rar" to "Arquivos_Comuns", ".7z" to "Arquivos_Comuns",
        ".exe" to "Arquivos_Comuns", ".apk" to "Arquivos_Comuns", ".iso" to "Arquivos_Comuns",
        ".tar" to "Arquivos_Comuns", ".gz" to "Arquivos_Comuns", ".tgz" to "Arquivos_Comuns",
        ".bz2" to "Arquivos_Comuns", ".xz" to "Arquivos_Comuns", ".msi" to "Arquivos_Comuns",
        ".dmg" to "Arquivos_Comuns", ".jar" to "Arquivos_Comuns", ".deb" to "Arquivos_Comuns",
        ".rpm" to "Arquivos_Comuns", ".bin" to "Arquivos_Comuns", ".img" to "Arquivos_Comuns",
        ".vcd" to "Arquivos_Comuns", ".bak" to "Arquivos_Comuns", ".tmp" to "Arquivos_Comuns",
        ".torrent" to "Arquivos_Comuns", ".cab" to "Arquivos_Comuns", ".cbr" to "Arquivos_Comuns",
        ".cbz" to "Arquivos_Comuns"
    )
}