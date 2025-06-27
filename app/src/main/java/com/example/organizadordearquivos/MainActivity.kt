package com.example.organizadordearquivos // VERIFIQUE SE ESTE NOME DE PACOTE CORRESPONDE AO SEU

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.log2
import kotlin.math.pow
import androidx.core.content.edit
import androidx.core.net.toUri

class MainActivity : AppCompatActivity() {

    // --- Views da UI ---
    private lateinit var tvStatus: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var btnSelectDownloads: Button
    private lateinit var btnOrganizeCategory: Button
    private lateinit var btnCleanFiles: Button
    private lateinit var btnRemoveEmptyFolders: Button
    private lateinit var btnOrganizeByDate: Button

    // URI da pasta selecionada pelo usuário via SAF (pode ser nula)
    private var downloadsTreeUri: Uri? = null

    // --- Constantes ---
    private val PREFS_NAME = "DownloadOrganizerPrefs"
    private val PREF_DOWNLOADS_URI = "downloads_uri"

    // --- ActivityResultLauncher para o Storage Access Framework (SAF) ---
    private lateinit var openDocumentTreeLauncher: ActivityResultLauncher<Uri?>

    // --- Mapeamento de categorias e extensões (traduzido do Python) ---
    private val FILE_CATEGORIES = mapOf(
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
        ".dmg" to "Arquivos_Comuns",
    )

    // Extensões de arquivos temporários a serem consideradas na limpeza
    private val TEMP_EXTENSIONS = listOf(".tmp", ".bak", ".~tmp", ".~bak", ".temp", ".~lock")

    // --- Gerenciamento de Estado da UI ---
    private val _isProcessing = MutableStateFlow(false)
    val isProcessing = _isProcessing.asStateFlow()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Inicializa as views
        initializeViews()

        // Configura o ActivityResultLauncher para o SAF
        openDocumentTreeLauncher = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            uri?.let {
                // Persiste a permissão para que o app possa acessar a pasta mesmo após reinicializar
                contentResolver.takePersistableUriPermission(
                    it,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
                downloadsTreeUri = it
                saveDownloadsUri(it) // Salva a URI para uso futuro
                updateStatus("Pasta selecionada com sucesso: ${it.path}")
                updateButtonStates()
            } ?: run {
                updateStatus("Seleção de pasta cancelada.")
            }
        }

        // Carrega a URI salva (se existir) e atualiza os botões
        loadDownloadsUri()
        updateButtonStates()

        // Configura os listeners dos botões
        setupClickListeners()

        // Observa o estado de processamento para habilitar/desabilitar a UI
        observeProcessingState()
    }

    // --- Funções de Inicialização ---

    private fun initializeViews() {
        tvStatus = findViewById(R.id.tvStatus)
        progressBar = findViewById(R.id.progressBar)
        btnSelectDownloads = findViewById(R.id.btnSelectDownloads)
        btnOrganizeCategory = findViewById(R.id.btnOrganizeCategory)
        btnCleanFiles = findViewById(R.id.btnCleanFiles)
        btnRemoveEmptyFolders = findViewById(R.id.btnRemoveEmptyFolders)
        btnOrganizeByDate = findViewById(R.id.btnOrganizeByDate)
    }

    private fun setupClickListeners() {
        btnSelectDownloads.setOnClickListener { selectDownloadsFolder() }
        btnOrganizeCategory.setOnClickListener {
            showConfirmationDialog(
                "Organizar por Categoria",
                "Isso moverá arquivos e pastas para subdiretórios categorizados. Deseja continuar?",
                ::organizeFilesInCategory
            )
        }
        btnCleanFiles.setOnClickListener {
            showConfirmationDialog(
                "Limpar Arquivos",
                "Isso excluirá permanentemente arquivos vazios e temporários. Deseja continuar?",
                ::cleanFiles
            )
        }
        btnRemoveEmptyFolders.setOnClickListener {
            showConfirmationDialog(
                "Remover Pastas Vazias",
                "Isso excluirá permanentemente todas as pastas vazias. Deseja continuar?",
                ::removeEmptyFolders
            )
        }
        btnOrganizeByDate.setOnClickListener {
            showConfirmationDialog(
                "Organizar por Data",
                "Isso moverá arquivos soltos para pastas de Ano/Mês. Deseja continuar?",
                ::organizeByDate
            )
        }
    }

    private fun observeProcessingState() {
        lifecycleScope.launch {
            isProcessing.collectLatest { processing ->
                updateButtonStates()
                progressBar.visibility = if (processing) View.VISIBLE else View.GONE
            }
        }
    }

    // --- Gerenciamento da URI de Downloads e Estado da UI ---

    private fun saveDownloadsUri(uri: Uri?) {
        val prefs: SharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit { putString(PREF_DOWNLOADS_URI, uri?.toString()) }
    }

    private fun loadDownloadsUri() {
        val prefs: SharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val uriString = prefs.getString(PREF_DOWNLOADS_URI, null)

        if (!uriString.isNullOrEmpty()) {
            try {
                // ---- INÍCIO DO BLOCO DE PROTEÇÃO ----
                val uri = uriString.toUri()

                // Verifica se a permissão para a URI ainda é válida
                if (contentResolver.persistedUriPermissions.any { it.uri == uri }) {
                    // Tenta acessar o DocumentFile. Se falhar, o 'catch' irá lidar com isso.
                    val documentFile = DocumentFile.fromTreeUri(this, uri)

                    if (documentFile != null && documentFile.exists()) {
                        downloadsTreeUri = uri
                        val folderName = documentFile.name ?: uri.path // Pega o nome do diretório
                        updateStatus("Pasta de Downloads carregada: $folderName")
                    } else {
                        // A URI é válida, mas a pasta não existe mais
                        updateStatus("A pasta salva não existe mais. Por favor, selecione novamente.")
                        saveDownloadsUri(null) // Limpa a URI inválida
                    }
                } else {
                    // A permissão foi revogada ou perdida
                    updateStatus("Permissão para a pasta salva foi perdida. Por favor, selecione novamente.")
                    saveDownloadsUri(null) // Limpa a URI inválida
                }
                // ---- FIM DO BLOCO DE PROTEÇÃO ----

            } catch (e: Exception) {
                // CAPTURA QUALQUER ERRO ao tentar usar a URI (ex: URI corrompida, inválida)
                updateStatus("Erro ao carregar a pasta salva (URI inválida). Por favor, selecione novamente.")
                saveDownloadsUri(null) // Limpa a URI inválida para evitar futuros crashes
                // Opcional: Logar o erro para depuração
                // Log.e("MainActivity", "Falha ao carregar URI", e)
            }
        }
    }

    private fun updateButtonStates() {
        val isFolderSelected = downloadsTreeUri != null
        val processing = isProcessing.value

        btnSelectDownloads.isEnabled = !processing
        btnOrganizeCategory.isEnabled = isFolderSelected && !processing
        btnOrganizeByDate.isEnabled = isFolderSelected && !processing
        btnCleanFiles.isEnabled = isFolderSelected && !processing
        btnRemoveEmptyFolders.isEnabled = isFolderSelected && !processing
    }

    // --- Funções de UI (Diálogo, Status, Progresso) ---

    private fun showConfirmationDialog(title: String, message: String, onConfirm: () -> Unit) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("Confirmar") { dialog, _ ->
                onConfirm()
                dialog.dismiss()
            }
            .setNegativeButton("Cancelar") { dialog, _ ->
                updateStatus("Operação cancelada pelo usuário.")
                dialog.dismiss()
            }
            .show()
    }

    private fun selectDownloadsFolder() {
        updateStatus("Por favor, selecione sua pasta de Downloads.")
        openDocumentTreeLauncher.launch(null) // Abre o seletor de pastas do sistema
    }

    private fun updateStatus(message: String) {
        runOnUiThread {
            tvStatus.append("\n$message")
        }
    }

    private fun updateProgress(progress: Int) {
        runOnUiThread {
            progressBar.progress = progress
        }
    }

    // --- Funções Auxiliares de Arquivo (Tradução da lógica Python) ---

    private fun DocumentFile.getExtension(): String {
        return name?.substringAfterLast('.', "")?.let { ".$it" }?.lowercase(Locale.ROOT) ?: ""
    }

    private fun findOrCreateDirectory(parent: DocumentFile, name: String): DocumentFile? {
        return parent.findFile(name)?.takeIf { it.isDirectory } ?: parent.createDirectory(name)
    }

    private fun convertBytes(num: Long): String {
        if (num < 1024) return "$num bytes"
        val units = listOf("bytes", "KB", "MB", "GB", "TB")
        val i = (log2(num.toDouble()) / log2(1024.0)).toInt()
        val size = num / 1024.0.pow(i.toDouble())
        return String.format(Locale.getDefault(), "%.1f %s", size, units[i])
    }

    private fun moveFile(file: DocumentFile, destinationDir: DocumentFile): DocumentFile? {
        // Tenta mover usando o metodo mais eficiente do Android
        return try {
            val parentUri = file.parentFile?.uri ?: return null // Proteção contra parent nulo
            val movedUri = DocumentsContract.moveDocument(
                contentResolver,
                file.uri,
                parentUri,
                destinationDir.uri
            )
            movedUri?.let { DocumentFile.fromSingleUri(this, it) }
        } catch (e: Exception) {
            // Fallback: Se o 'move' falhar (ex: mover entre diferentes armazenamentos), copia e deleta.
            // Esta parte é mais lenta, mas mais robusta.
            try {
                val newFile = destinationDir.createFile(file.type ?: "application/octet-stream", file.name!!)
                contentResolver.openInputStream(file.uri)?.use { input ->
                    contentResolver.openOutputStream(newFile!!.uri)?.use { output ->
                        input.copyTo(output)
                    }
                }
                if (file.delete()) newFile else null
            } catch (copyError: Exception) {
                updateStatus("ERRO CRÍTICO ao mover '${file.name}': ${copyError.message}")
                null
            }
        }
    }

    // --- Funções de Organização (Lógica principal do app) ---

    private fun organizeFilesInCategory() {
        val uri = downloadsTreeUri ?: return

        lifecycleScope.launch {
            _isProcessing.value = true
            updateStatus("\n--- Iniciando Organização por Categoria ---")

            try {
                withContext(Dispatchers.IO) {
                    val downloadsRoot = DocumentFile.fromTreeUri(applicationContext, uri) ?: return@withContext

                    val allItems = downloadsRoot.listFiles().toList()
                    val foldersToIgnore = setOf("Arquivos", "Pastas_Organizadas", "Organizado_Por_Data")

                    val filesToMove = allItems.filter { it.isFile && !it.name.orEmpty().startsWith('.') }
                    val foldersToMove = allItems.filter { it.isDirectory && it.name !in foldersToIgnore }

                    if (filesToMove.isEmpty() && foldersToMove.isEmpty()) {
                        updateStatus("Nenhum arquivo ou pasta para organizar.")
                        return@withContext
                    }

                    val mainArchiveFolder = findOrCreateDirectory(downloadsRoot, "Arquivos")!!
                    val organizedFoldersBase = findOrCreateDirectory(downloadsRoot, "Pastas_Organizadas")!!

                    var movedFilesCount = 0
                    var movedFoldersCount = 0
                    val totalItems = filesToMove.size + foldersToMove.size

                    // Mover Pastas
                    foldersToMove.forEachIndexed { index, folder ->
                        val newFolder = moveFile(folder, organizedFoldersBase)
                        if (newFolder != null) {
                            updateStatus("Pasta movida: '${folder.name}'")
                            movedFoldersCount++
                        } else {
                            updateStatus("Falha ao mover pasta: '${folder.name}'")
                        }
                        updateProgress(((index + 1) * 100) / totalItems)
                    }

                    // Mover Arquivos
                    filesToMove.forEachIndexed { index, file ->
                        val extension = file.getExtension()
                        val categoryName = FILE_CATEGORIES.getOrDefault(extension, "Diversos")

                        val categoryFolder = findOrCreateDirectory(mainArchiveFolder, categoryName)!!
                        val extensionFolderName = "${categoryName}${extension.replace(".", "").uppercase(Locale.ROOT)}"
                        val finalDestFolder = findOrCreateDirectory(categoryFolder, extensionFolderName)!!

                        // Lida com conflitos de nome
                        var targetFile = file
                        var finalFileName = file.name!!
                        if (finalDestFolder.findFile(finalFileName) != null) {
                            val baseName = finalFileName.substringBeforeLast('.')
                            val ext = finalFileName.substringAfterLast('.')
                            var suffix = 1
                            do {
                                finalFileName = "${baseName}_${suffix++}.${ext}"
                            } while (finalDestFolder.findFile(finalFileName) != null)

                            try {
                                file.renameTo(finalFileName)
                                targetFile = DocumentFile.fromSingleUri(applicationContext, file.uri)!!
                            } catch (e: Exception) {
                                updateStatus("Falha ao renomear arquivo em conflito: ${file.name}")
                            }
                        }

                        val newFile = moveFile(targetFile, finalDestFolder)
                        if (newFile != null) {
                            updateStatus("Arquivo movido: '$finalFileName' -> $categoryName")
                            movedFilesCount++
                        } else {
                            updateStatus("Falha ao mover arquivo: '${targetFile.name}'")
                        }
                        updateProgress(((foldersToMove.size + index + 1) * 100) / totalItems)
                    }

                    updateStatus("\n--- Resumo da Organização ---")
                    updateStatus("$movedFoldersCount pastas movidas para 'Pastas_Organizadas'.")
                    updateStatus("$movedFilesCount arquivos movidos para 'Arquivos'.")
                    updateStatus("Organização por categoria concluída!")
                }
            } catch (e: Exception) {
                updateStatus("ERRO: ${e.message}")
            } finally {
                _isProcessing.value = false
            }
        }
    }

    private fun cleanFiles() {
        val uri = downloadsTreeUri ?: return

        lifecycleScope.launch {
            _isProcessing.value = true
            updateStatus("\n--- Iniciando Limpeza de Arquivos ---")
            var totalFreedSpace = 0L
            var removedFilesCount = 0

            try {
                withContext(Dispatchers.IO) {
                    val rootDir = DocumentFile.fromTreeUri(applicationContext, uri) ?: return@withContext

                    val filesToDelete = mutableListOf<DocumentFile>()
                    val stack = ArrayDeque<DocumentFile>()
                    stack.add(rootDir)

                    // Percorre todos os arquivos de forma não recursiva (evita StackOverflow)
                    while(stack.isNotEmpty()) {
                        val current = stack.removeFirst()
                        current.listFiles().forEach {
                            if (it.isDirectory) {
                                stack.add(it)
                            } else if (it.isFile) {
                                val extension = it.getExtension()
                                if (it.length() == 0L || extension in TEMP_EXTENSIONS) {
                                    filesToDelete.add(it)
                                }
                            }
                        }
                    }

                    if (filesToDelete.isEmpty()) {
                        updateStatus("Nenhum arquivo vazio ou temporário encontrado.")
                        return@withContext
                    }

                    updateStatus("${filesToDelete.size} arquivos encontrados para limpeza. Removendo...")

                    filesToDelete.forEachIndexed { index, file ->
                        val fileSize = file.length()
                        if (file.delete()) {
                            totalFreedSpace += fileSize
                            removedFilesCount++
                            updateStatus("Removido: ${file.name} (${convertBytes(fileSize)})")
                        } else {
                            updateStatus("Falha ao remover: ${file.name}")
                        }
                        updateProgress(((index + 1) * 100) / filesToDelete.size)
                    }

                    updateStatus("\n--- Resumo da Limpeza ---")
                    updateStatus("$removedFilesCount arquivos removidos.")
                    updateStatus("Espaço liberado: ${convertBytes(totalFreedSpace)}.")
                }
            } catch (e: Exception) {
                updateStatus("ERRO: ${e.message}")
            } finally {
                _isProcessing.value = false
            }
        }
    }

    private fun removeEmptyFolders() {
        val uri = downloadsTreeUri ?: return

        lifecycleScope.launch {
            _isProcessing.value = true
            updateStatus("\n--- Iniciando Remoção de Pastas Vazias ---")
            var removedCount = 0

            try {
                withContext(Dispatchers.IO) {
                    val rootDir = DocumentFile.fromTreeUri(applicationContext, uri) ?: return@withContext
                    val allFolders = mutableListOf<DocumentFile>()

                    // Coleta todas as pastas primeiro
                    val stack = ArrayDeque<DocumentFile>()
                    stack.add(rootDir)
                    while (stack.isNotEmpty()) {
                        val current = stack.removeFirst()
                        allFolders.add(current)
                        current.listFiles().filter { it.isDirectory }.forEach { stack.add(it) }
                    }

                    // Processa da mais profunda para a mais superficial
                    allFolders.asReversed().forEach { folder ->
                        if (folder.listFiles().isEmpty() && folder.uri != rootDir.uri) {
                            if (folder.delete()) {
                                removedCount++
                                updateStatus("Pasta vazia removida: ${folder.name}")
                            } else {
                                updateStatus("Falha ao remover pasta vazia: ${folder.name}")
                            }
                        }
                    }

                    updateStatus("\n--- Resumo da Remoção ---")
                    updateStatus("$removedCount pastas vazias removidas.")
                }
            } catch (e: Exception) {
                updateStatus("ERRO: ${e.message}")
            } finally {
                _isProcessing.value = false
            }
        }
    }

    private fun organizeByDate() {
        val uri = downloadsTreeUri ?: return

        lifecycleScope.launch {
            _isProcessing.value = true
            updateStatus("\n--- Iniciando Organização por Data ---")

            try {
                withContext(Dispatchers.IO) {
                    val downloadsRoot = DocumentFile.fromTreeUri(applicationContext, uri) ?: return@withContext

                    val foldersToIgnore = setOf("Arquivos", "Pastas_Organizadas", "Organizado_Por_Data")
                    val filesToOrganize = downloadsRoot.listFiles().filter {
                        it.isFile && !it.name.orEmpty().startsWith('.')
                    }

                    if (filesToOrganize.isEmpty()) {
                        updateStatus("Nenhum arquivo solto para organizar por data.")
                        return@withContext
                    }

                    val dateOutputBase = findOrCreateDirectory(downloadsRoot, "Organizado_Por_Data")!!
                    var movedCount = 0

                    val yearFormat = SimpleDateFormat("yyyy", Locale.getDefault())
                    val monthFormat = SimpleDateFormat("MM - MMMM", Locale.getDefault())

                    filesToOrganize.forEachIndexed { index, file ->
                        val modDate = Date(file.lastModified())
                        val yearFolder = findOrCreateDirectory(dateOutputBase, yearFormat.format(modDate))!!
                        val monthFolder = findOrCreateDirectory(yearFolder, monthFormat.format(modDate))!!

                        // Lida com conflitos de nome (semelhante à outra função)
                        var targetFile = file
                        var finalFileName = file.name!!
                        if (monthFolder.findFile(finalFileName) != null) {
                            // Renomeia o arquivo original antes de mover
                            val baseName = finalFileName.substringBeforeLast('.')
                            val ext = finalFileName.substringAfterLast('.')
                            var suffix = 1
                            do {
                                finalFileName = "${baseName}_${suffix++}.${ext}"
                            } while (downloadsRoot.findFile(finalFileName) != null) // Verifica na origem
                            file.renameTo(finalFileName)
                            targetFile = downloadsRoot.findFile(finalFileName)!!
                        }

                        val newFile = moveFile(targetFile, monthFolder)
                        if (newFile != null) {
                            updateStatus("Movido por data: '$finalFileName'")
                            movedCount++
                        } else {
                            updateStatus("Falha ao mover por data: '${targetFile.name}'")
                        }
                        updateProgress(((index + 1) * 100) / filesToOrganize.size)
                    }

                    updateStatus("\n--- Resumo da Organização por Data ---")
                    updateStatus("$movedCount arquivos movidos para 'Organizado_Por_Data'.")
                }
            } catch (e: Exception) {
                updateStatus("ERRO: ${e.message}")
            } finally {
                _isProcessing.value = false
            }
        }
    }
}