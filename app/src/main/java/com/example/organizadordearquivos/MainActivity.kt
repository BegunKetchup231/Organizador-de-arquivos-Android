package com.example.organizadordearquivos // VERIFIQUE SE ESTE NOME DE PACOTE CORRESPONDE AO SEU

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.log2
import kotlin.math.pow

class MainActivity : AppCompatActivity() {

    // --- Views da UI ---
    private lateinit var tvStatus: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var progressStatusText: TextView
    private lateinit var btnSelectDownloads: Button
    private lateinit var btnOrganizeCategory: Button
    private lateinit var btnCleanFiles: Button
    private lateinit var btnRemoveEmptyFolders: Button
    private lateinit var btnOrganizeByDate: Button

    // URI da pasta selecionada
    private var downloadsTreeUri: Uri? = null

    // --- Constantes ---
    private val PREFS_NAME = "DownloadOrganizerPrefs"
    private val PREF_DOWNLOADS_URI = "downloads_uri"

    // --- ActivityResultLaunchers ---
    private lateinit var openDocumentTreeLauncher: ActivityResultLauncher<Uri?>
    // NOVO: Launcher para o resultado da tela de permissão
    private lateinit var manageStoragePermissionLauncher: ActivityResultLauncher<Intent>

    // Mapeamento de categorias e extensões (permanece igual)
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

    private val TEMP_EXTENSIONS = listOf(".tmp", ".bak", ".~tmp", ".~bak", ".temp", ".~lock")

    private val _isProcessing = MutableStateFlow(false)
    private val isProcessing = _isProcessing.asStateFlow()


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initializeViews()
        setupLaunchers() // NOVO: Agrupando inicialização dos launchers
        loadDownloadsUri()
        updateButtonStates()
        setupClickListeners()
        observeProcessingState()
    }

    // --- Funções de Inicialização ---

    private fun initializeViews() {
        tvStatus = findViewById(R.id.tvStatus)
        progressBar = findViewById(R.id.progressBar)
        progressStatusText = findViewById(R.id.progressStatusText)
        btnSelectDownloads = findViewById(R.id.btnSelectDownloads)
        btnOrganizeCategory = findViewById(R.id.btnOrganizeCategory)
        btnCleanFiles = findViewById(R.id.btnCleanFiles)
        btnRemoveEmptyFolders = findViewById(R.id.btnRemoveEmptyFolders)
        btnOrganizeByDate = findViewById(R.id.btnOrganizeByDate)
    }

    // NOVO: Função para agrupar a inicialização dos launchers
    private fun setupLaunchers() {
        // Launcher para selecionar uma árvore de diretórios (SAF)
        openDocumentTreeLauncher = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            uri?.let {
                contentResolver.takePersistableUriPermission(
                    it,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
                downloadsTreeUri = it
                saveDownloadsUri(it)
                updateStatus("Pasta selecionada com sucesso: ${it.path}")
                updateButtonStates()
            } ?: run {
                updateStatus("Seleção de pasta cancelada.")
            }
        }

        // NOVO: Launcher para tratar o retorno da tela de permissão de acesso total
        manageStoragePermissionLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            // Verifica se a permissão foi concedida após o usuário voltar da tela de Configurações
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                if (Environment.isExternalStorageManager()) {
                    updateStatus("Permissão de acesso a todos os arquivos concedida!")
                    tryToLoadDownloadsFolder() // Tenta carregar a pasta Downloads automaticamente
                } else {
                    updateStatus("A permissão de acesso a todos os arquivos não foi concedida.")
                    showPermissionDeniedDialog()
                }
            }
        }
    }

    private fun setupClickListeners() {
        // NOVO: O botão principal agora chama a verificação de permissão
        btnSelectDownloads.setOnClickListener { checkAndRequestPermissions() }

        // O resto permanece igual
        btnOrganizeCategory.setOnClickListener {
            showConfirmationDialog("Organizar por Categoria", "Isso moverá arquivos e pastas. Deseja continuar?", ::organizeFilesInCategory)
        }
        btnCleanFiles.setOnClickListener {
            showConfirmationDialog("Limpar Arquivos", "Isso excluirá arquivos vazios e temporários. Deseja continuar?", ::cleanFiles)
        }
        btnRemoveEmptyFolders.setOnClickListener {
            showConfirmationDialog("Remover Pastas Vazias", "Isso excluirá pastas vazias. Deseja continuar?", ::removeEmptyFolders)
        }
        btnOrganizeByDate.setOnClickListener {
            showConfirmationDialog("Organizar por Data", "Isso moverá arquivos para pastas de Ano/Mês. Deseja continuar?", ::organizeByDate)
        }
    }

    // O resto do seu código não foi alterado...
    // ...
    // A única alteração foi a adição das funções de permissão abaixo.

    // --- Funções de UI (Diálogo, Status, Progresso) ---

    private fun showConfirmationDialog(title: String, message: String, onConfirm: () -> Unit) {
        AlertDialog.Builder(this).setTitle(title).setMessage(message)
            .setPositiveButton("Confirmar") { dialog, _ ->
                onConfirm()
                dialog.dismiss()
            }
            .setNegativeButton("Cancelar") { dialog, _ ->
                updateStatus("Operação cancelada.")
                dialog.dismiss()
            }.show()
    }

    // NOVO: Lógica completa para verificar e pedir a permissão de acesso total
    private fun checkAndRequestPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) { // Permissão existe a partir do Android 11
            if (Environment.isExternalStorageManager()) {
                // Permissão já está concedida, carrega a pasta Downloads
                updateStatus("Permissão já concedida. Carregando pasta Downloads...")
                tryToLoadDownloadsFolder()
            } else {
                // Permissão não concedida, abre a tela de configurações para o usuário conceder
                try {
                    val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                    intent.data = Uri.fromParts("package", packageName, null)
                    manageStoragePermissionLauncher.launch(intent)
                } catch (e: Exception) {
                    updateStatus("Não foi possível abrir a tela de permissão. Selecione a pasta manualmente.")
                    selectDownloadsFolderWithSAF() // Fallback para o seletor manual
                }
            }
        } else {
            // Para Android 10 e inferior, usa o método antigo de seleção manual
            selectDownloadsFolderWithSAF()
        }
    }

    // NOVO: Tenta carregar a pasta Downloads automaticamente uma vez que a permissão é concedida
    private fun tryToLoadDownloadsFolder() {
        try {
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val documentFile = DocumentFile.fromFile(downloadsDir)
            if (documentFile.canRead()) {
                downloadsTreeUri = documentFile.uri
                // Persiste a permissão para acesso futuro, embora com MANAGE_EXTERNAL_STORAGE não seja estritamente necessário
                contentResolver.takePersistableUriPermission(
                    downloadsTreeUri!!,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
                saveDownloadsUri(downloadsTreeUri)
                updateStatus("Pasta Downloads carregada automaticamente!")
                updateButtonStates()
            } else {
                updateStatus("Não foi possível acessar a pasta Downloads. Selecione manualmente.")
                selectDownloadsFolderWithSAF()
            }
        } catch (e: Exception) {
            updateStatus("Erro ao acessar a pasta Downloads: ${e.message}")
            selectDownloadsFolderWithSAF()
        }
    }

    // NOVO: Diálogo para informar o usuário caso ele negue a permissão
    private fun showPermissionDeniedDialog() {
        AlertDialog.Builder(this)
            .setTitle("Permissão Necessária")
            .setMessage("Para carregar a pasta Downloads automaticamente, o app precisa da permissão de 'Acesso a todos os arquivos'. Você ainda pode selecionar a pasta manualmente.")
            .setPositiveButton("OK") { dialog, _ -> dialog.dismiss() }
            .show()
    }

    // NOVO: Função antiga renomeada para ser o método de fallback (seleção manual)
    private fun selectDownloadsFolderWithSAF() {
        updateStatus("Por favor, selecione sua pasta")
        openDocumentTreeLauncher.launch(null)
    }

    // Todas as suas funções originais de status, progresso, auxiliares e de organização permanecem aqui, intactas...
    // ...
    // [SEU CÓDIGO ORIGINAL E INALTERADO VAI AQUI]
    // ...

    // --- Gerenciamento da URI de Downloads e Estado da UI ---

    private fun observeProcessingState() {
        lifecycleScope.launch {
            isProcessing.collectLatest { processing ->
                updateButtonStates()
                if (processing) {
                    progressBar.visibility = View.VISIBLE
                    progressStatusText.visibility = View.VISIBLE
                } else {
                    progressBar.visibility = View.GONE
                    progressStatusText.visibility = View.GONE
                    // Reseta o progresso para a próxima operação
                    updateOperationProgress(0)
                }
            }
        }
    }

    private fun saveDownloadsUri(uri: Uri?) {
        val prefs: SharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit { putString(PREF_DOWNLOADS_URI, uri?.toString()) }
    }

    private fun loadDownloadsUri() {
        val prefs: SharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val uriString = prefs.getString(PREF_DOWNLOADS_URI, null)
        if (uriString.isNullOrEmpty()) return

        try {
            val uri = uriString.toUri()
            if (contentResolver.persistedUriPermissions.any { it.uri == uri }) {
                val documentFile = DocumentFile.fromTreeUri(this, uri)
                if (documentFile != null && documentFile.exists()) {
                    downloadsTreeUri = uri
                    updateStatus("Pasta carregada: ${documentFile.name ?: uri.path}")
                } else {
                    updateStatus("Pasta salva não existe. Selecione novamente.")
                    saveDownloadsUri(null)
                }
            } else {
                updateStatus("Permissão perdida. Selecione a pasta novamente.")
                saveDownloadsUri(null)
            }
        } catch (e: Exception) {
            updateStatus("Erro ao carregar URI. Selecione a pasta novamente.")
            saveDownloadsUri(null)
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

    private fun updateStatus(message: String) {
        runOnUiThread {
            tvStatus.append("\n$message")
        }
    }

    private fun updateOperationProgress(percentage: Int) {
        runOnUiThread {
            progressBar.progress = percentage
            progressStatusText.text = "Progresso - ($percentage%)"
        }
    }

    private fun DocumentFile.getExtension(): String {
        return this.name?.substringAfterLast('.', "")?.lowercase(Locale.ROOT) ?: ""
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

    private fun moveFile(fileToMove: DocumentFile, destinationDir: DocumentFile, finalFileName: String): DocumentFile? {
        try {
            val fileWithFinalName = if (fileToMove.name != finalFileName) {
                if (fileToMove.renameTo(finalFileName)) {
                    fileToMove.parentFile?.findFile(finalFileName) ?: throw IOException("Falha ao encontrar o arquivo após renomear para '$finalFileName'.")
                } else {
                    throw IOException("Falha ao renomear o arquivo de origem.")
                }
            } else {
                fileToMove
            }
            val movedUri = DocumentsContract.moveDocument(contentResolver, fileWithFinalName.uri, fileWithFinalName.parentFile!!.uri, destinationDir.uri)
            return movedUri?.let { DocumentFile.fromSingleUri(this, it) }
        } catch (e: Exception) {
            updateStatus("Aviso: Usando método de cópia para '${fileToMove.name}' -> '$finalFileName'.")
            return try {
                val newFile = destinationDir.createFile(fileToMove.type ?: "application/octet-stream", finalFileName)
                if (newFile != null) {
                    contentResolver.openInputStream(fileToMove.uri)?.use { input ->
                        contentResolver.openOutputStream(newFile.uri)?.use { output ->
                            input.copyTo(output)
                        }
                    }
                } else {
                    throw IOException("Não foi possível criar o arquivo de destino '$finalFileName'.")
                }
                if (fileToMove.delete()) newFile else {
                    newFile.delete()
                    updateStatus("ERRO: O arquivo original '${fileToMove.name}' não pôde ser deletado. Operação revertida.")
                    null
                }
            } catch (copyError: Exception) {
                updateStatus("ERRO CRÍTICO ao copiar '${fileToMove.name}': ${copyError.message}")
                null
            }
        }
    }

    private fun organizeFilesInCategory() {
        val uri = downloadsTreeUri ?: return
        lifecycleScope.launch {
            _isProcessing.value = true
            updateStatus("\n--- Iniciando Organização por Categoria ---")
            try {
                withContext(Dispatchers.IO) {
                    val root = DocumentFile.fromTreeUri(applicationContext, uri) ?: throw IOException("Pasta não acessível.")
                    val items = root.listFiles().toList()
                    val foldersToIgnore = setOf("Arquivos", "Pastas_Organizadas", "Organizado_Por_Data")
                    val filesToMove = items.filter { it.isFile && !it.name.orEmpty().startsWith('.') }
                    val foldersToMove = items.filter { it.isDirectory && it.name !in foldersToIgnore }
                    val totalItems = filesToMove.size + foldersToMove.size
                    if (totalItems == 0) {
                        updateStatus("Nenhum item para organizar.")
                        return@withContext
                    }
                    val mainArchiveFolder = findOrCreateDirectory(root, "Arquivos")!!
                    val organizedFoldersBase = findOrCreateDirectory(root, "Pastas_Organizadas")!!
                    var movedFilesCount = 0
                    var movedFoldersCount = 0
                    var itemsProcessed = 0

                    val existingFolderNames = organizedFoldersBase.listFiles().mapNotNull { it.name }.toMutableSet()
                    foldersToMove.forEach { folder ->
                        var finalFolderName = folder.name!!
                        if (existingFolderNames.contains(finalFolderName)) {
                            var suffix = 1
                            do { finalFolderName = "${folder.name}_${suffix++}" } while (existingFolderNames.contains(finalFolderName))
                        }
                        if (moveFile(folder, organizedFoldersBase, finalFolderName) != null) {
                            updateStatus("Pasta movida: '$finalFolderName'")
                            movedFoldersCount++
                            existingFolderNames.add(finalFolderName)
                        } else {
                            updateStatus("Falha ao mover pasta: '${folder.name}'")
                        }
                        itemsProcessed++
                        updateOperationProgress((itemsProcessed * 100) / totalItems) // ATUALIZADO
                    }

                    filesToMove.forEach { file ->
                        val extension = file.getExtension()
                        val categoryName = FILE_CATEGORIES.getOrDefault(extension, "Diversos")
                        val categoryFolder = findOrCreateDirectory(mainArchiveFolder, categoryName)!!
                        val finalDestFolder = findOrCreateDirectory(categoryFolder, extension.uppercase(Locale.ROOT))!!
                        var finalFileName = file.name!!
                        if (finalDestFolder.findFile(finalFileName) != null) {
                            val baseName = finalFileName.substringBeforeLast('.')
                            val ext = finalFileName.substringAfterLast('.')
                            var suffix = 1
                            do { finalFileName = "${baseName}_${suffix++}.$ext" } while (finalDestFolder.findFile(finalFileName) != null)
                        }
                        if (moveFile(file, finalDestFolder, finalFileName) != null) {
                            movedFilesCount++
                        } else {
                            updateStatus("Falha ao mover arquivo: '${file.name}'")
                        }
                        itemsProcessed++
                        updateOperationProgress((itemsProcessed * 100) / totalItems) // ATUALIZADO
                    }
                    updateStatus("\n--- Resumo: $movedFoldersCount pastas e $movedFilesCount arquivos movidos.")
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
                    val stack = ArrayDeque<DocumentFile>().apply { add(rootDir) }
                    while (stack.isNotEmpty()) {
                        val current = stack.removeFirst()
                        current.listFiles().forEach {
                            if (it.isDirectory) stack.add(it)
                            else if (it.isFile && (it.length() == 0L || it.getExtension() in TEMP_EXTENSIONS)) filesToDelete.add(it)
                        }
                    }
                    if (filesToDelete.isEmpty()) {
                        updateStatus("Nenhum arquivo para limpar.")
                        return@withContext
                    }
                    filesToDelete.forEachIndexed { index, file ->
                        val fileSize = file.length()
                        if (file.delete()) {
                            totalFreedSpace += fileSize
                            removedFilesCount++
                        } else {
                            updateStatus("Falha ao remover: ${file.name}")
                        }
                        updateOperationProgress(((index + 1) * 100) / filesToDelete.size) // ATUALIZADO
                    }
                    updateStatus("\n--- Resumo: $removedFilesCount arquivos removidos (${convertBytes(totalFreedSpace)} liberados).")
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
                    val stack = ArrayDeque<DocumentFile>().apply { add(rootDir) }
                    while (stack.isNotEmpty()) {
                        val current = stack.removeFirst()
                        allFolders.add(current)
                        current.listFiles().filter { it.isDirectory }.forEach { stack.add(it) }
                    }
                    if (allFolders.size <= 1) {
                        updateStatus("Nenhuma subpasta encontrada para verificar.")
                        return@withContext
                    }
                    allFolders.asReversed().forEachIndexed { index, folder ->
                        if (folder.listFiles().isEmpty() && folder.uri != rootDir.uri) {
                            if (folder.delete()) removedCount++ else updateStatus("Falha ao remover: ${folder.name}")
                        }
                        updateOperationProgress(((index + 1) * 100) / allFolders.size)
                    }
                    updateStatus("\n--- Resumo: $removedCount pastas vazias removidas.")
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
            var movedCount = 0
            try {
                withContext(Dispatchers.IO) {
                    val root = DocumentFile.fromTreeUri(applicationContext, uri) ?: throw IOException("Pasta não acessível.")
                    val filesToOrganize = root.listFiles().filter { it.isFile && !it.name.orEmpty().startsWith('.') }
                    if (filesToOrganize.isEmpty()) {
                        updateStatus("Nenhum arquivo para organizar.")
                        return@withContext
                    }
                    val dateOutputBase = findOrCreateDirectory(root, "Organizado_Por_Data")!!
                    val yearFormat = SimpleDateFormat("yyyy", Locale.getDefault())
                    val monthFormat = SimpleDateFormat("MM - MMMM", Locale.getDefault())
                    val monthFolderCache = mutableMapOf<String, DocumentFile>()
                    val existingFilesInDestCache = mutableMapOf<String, MutableSet<String>>()
                    filesToOrganize.forEachIndexed { index, file ->
                        val modDate = Date(file.lastModified())
                        val yearString = yearFormat.format(modDate)
                        val monthString = monthFormat.format(modDate)
                        val monthPathKey = "$yearString/$monthString"
                        val monthFolder = monthFolderCache.getOrPut(monthPathKey) {
                            val yearFolder = findOrCreateDirectory(dateOutputBase, yearString)!!
                            findOrCreateDirectory(yearFolder, monthString)!!
                        }
                        val destinationExistingFiles = existingFilesInDestCache.getOrPut(monthPathKey) {
                            monthFolder.listFiles().mapNotNull { it.name }.toMutableSet()
                        }
                        var finalFileName = file.name!!
                        if (destinationExistingFiles.contains(finalFileName)) {
                            val baseName = finalFileName.substringBeforeLast('.')
                            val ext = finalFileName.substringAfterLast('.')
                            var suffix = 1
                            do { finalFileName = "${baseName}_${suffix++}.$ext" } while (destinationExistingFiles.contains(finalFileName))
                        }
                        val movedFile = moveFile(file, monthFolder, finalFileName)
                        if (movedFile != null) {
                            movedCount++
                            destinationExistingFiles.add(movedFile.name!!)
                        } else {
                            updateStatus("Falha ao mover: '${file.name}'")
                        }
                        updateOperationProgress(((index + 1) * 100) / filesToOrganize.size)
                    }
                    updateStatus("\n--- Resumo: $movedCount de ${filesToOrganize.size} arquivos movidos.")
                }
            } catch (e: Exception) {
                updateStatus("ERRO GERAL: ${e.message}")
            } finally {
                _isProcessing.value = false
            }
        }
    }
}