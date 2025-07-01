package com.example.organizadordearquivos // VERIFIQUE SE ESTE NOME DE PACOTE CORRESPONDE AO SEU

import java.io.IOException
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
    private fun DocumentFile.getExtension(): String {
        return this.name?.substringAfterLast('.', "")?.lowercase(Locale.ROOT) ?: ""
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
        updateStatus("Por favor, selecione sua pasta")
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

    private fun moveFile(
        fileToMove: DocumentFile,
        destinationDir: DocumentFile,
        finalFileName: String
    ): DocumentFile? {
        try {
            // Passo 1: Renomear o arquivo na origem SE o nome final for diferente.
            // `renameTo` é geralmente uma operação de metadados rápida.
            val fileWithFinalName = if (fileToMove.name != finalFileName) {
                if (fileToMove.renameTo(finalFileName)) {
                    // Sucesso! Agora precisamos da nova referência para o arquivo renomeado.
                    // A referência original `fileToMove` pode se tornar inválida.
                    val parent = fileToMove.parentFile
                    if (parent == null) {
                        throw IOException("O arquivo de origem não tem um diretório pai.")
                    }
                    parent.findFile(finalFileName)
                        ?: throw IOException("Falha ao encontrar o arquivo após renomear para '$finalFileName'.")
                } else {
                    // Se a renomeação falhar, não podemos usar `moveDocument` de forma confiável.
                    // Lançamos uma exceção para pular diretamente para o bloco de fallback (catch).
                    throw IOException("Falha ao renomear o arquivo de origem.")
                }
            } else {
                // O nome já está correto, não é preciso renomear.
                fileToMove
            }

            // Passo 2: Mover o arquivo (que agora tem o nome correto) para o destino.
            val movedUri = DocumentsContract.moveDocument(
                contentResolver,
                fileWithFinalName.uri,
                fileWithFinalName.parentFile!!.uri, // O pai do arquivo (possivelmente renomeado)
                destinationDir.uri
            )
            return movedUri?.let { DocumentFile.fromSingleUri(this, it) }

        } catch (e: Exception) {
            // --- FALLBACK ---
            // Se qualquer operação no bloco `try` falhar, executamos o método de copiar e deletar.
            updateStatus("Aviso: Usando método de cópia para '${fileToMove.name}' -> '$finalFileName'.")
            return try {
                // Cria o arquivo no destino JÁ COM O NOME FINAL CORRETO.
                val newFile = destinationDir.createFile(fileToMove.type ?: "application/octet-stream", finalFileName)

                // Copia o conteúdo do arquivo original para o novo.
                if (newFile != null) {
                    contentResolver.openInputStream(fileToMove.uri)?.use { input ->
                        contentResolver.openOutputStream(newFile.uri)?.use { output ->
                            input.copyTo(output)
                        }
                    }
                } else {
                    throw IOException("Não foi possível criar o arquivo de destino '$finalFileName'.")
                }

                // Se a cópia for bem-sucedida, deleta o arquivo original.
                if (fileToMove.delete()) {
                    newFile // Retorna o novo arquivo criado
                } else {
                    // Isso é um problema: a cópia existe mas o original não foi apagado.
                    // Para evitar duplicatas, deletamos a cópia que acabamos de fazer.
                    newFile.delete()
                    updateStatus("ERRO: O arquivo original '${fileToMove.name}' não pôde ser deletado após a cópia. A operação foi revertida.")
                    null
                }
            } catch (copyError: Exception) {
                updateStatus("ERRO CRÍTICO ao copiar '${fileToMove.name}': ${copyError.message}")
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
                    val downloadsRoot = DocumentFile.fromTreeUri(applicationContext, uri)
                        ?: throw IOException("Não foi possível acessar a pasta selecionada.")

                    val allItems = downloadsRoot.listFiles().toList()
                    // Ignora as pastas que o próprio app cria para evitar movê-las
                    val foldersToIgnore = setOf("Arquivos", "Pastas_Organizadas", "Organizado_Por_Data")

                    val filesToMove = allItems.filter { it.isFile && !it.name.orEmpty().startsWith('.') }
                    val foldersToMove = allItems.filter { it.isDirectory && it.name !in foldersToIgnore }

                    if (filesToMove.isEmpty() && foldersToMove.isEmpty()) {
                        updateStatus("Nenhum arquivo ou pasta para organizar.")
                        return@withContext
                    }

                    // Pastas de destino
                    val mainArchiveFolder = findOrCreateDirectory(downloadsRoot, "Arquivos")!!
                    val organizedFoldersBase = findOrCreateDirectory(downloadsRoot, "Pastas_Organizadas")!!

                    var movedFilesCount = 0
                    var movedFoldersCount = 0
                    val totalItems = filesToMove.size + foldersToMove.size
                    var itemsProcessed = 0

                    // --- 1. Mover Pastas ---
                    // Pega a lista de nomes de pastas já existentes no destino para checar conflitos
                    val existingFolderNames = organizedFoldersBase.listFiles()
                        .mapNotNull { it.name }
                        .toMutableSet()

                    foldersToMove.forEach { folder ->
                        var finalFolderName = folder.name!!
                        // Lida com conflito de nomes para pastas
                        if (existingFolderNames.contains(finalFolderName)) {
                            var suffix = 1
                            do {
                                finalFolderName = "${folder.name}_${suffix++}"
                            } while (existingFolderNames.contains(finalFolderName))
                        }

                        // CORREÇÃO: Passa o terceiro parâmetro 'finalFolderName' para a função moveFile
                        val newFolder = moveFile(folder, organizedFoldersBase, finalFolderName)

                        if (newFolder != null) {
                            updateStatus("Pasta movida: '${newFolder.name}'")
                            movedFoldersCount++
                            existingFolderNames.add(newFolder.name!!) // Atualiza a lista para evitar conflitos na mesma execução
                        } else {
                            updateStatus("Falha ao mover pasta: '${folder.name}'")
                        }
                        itemsProcessed++
                        updateProgress((itemsProcessed * 100) / totalItems)
                    }

                    // --- 2. Mover Arquivos ---
                    filesToMove.forEach { file ->
                        val extension = file.getExtension()
                        val categoryName = FILE_CATEGORIES.getOrDefault(extension, "Diversos")

                        val categoryFolder = findOrCreateDirectory(mainArchiveFolder, categoryName)!!
                        // O nome da pasta final não precisa mais de lógica complexa. Usaremos o nome da categoria.
                        val finalDestFolder = findOrCreateDirectory(categoryFolder, extension.uppercase(Locale.ROOT))!!

                        // CORREÇÃO: Lógica de conflito simplificada antes de chamar moveFile
                        var finalFileName = file.name!!
                        if (finalDestFolder.findFile(finalFileName) != null) {
                            val baseName = finalFileName.substringBeforeLast('.')
                            val ext = finalFileName.substringAfterLast('.')
                            var suffix = 1
                            do {
                                finalFileName = "${baseName}_${suffix++}.$ext"
                            } while (finalDestFolder.findFile(finalFileName) != null)
                        }

                        // CORREÇÃO: Passa o terceiro parâmetro 'finalFileName' para a função moveFile
                        val newFile = moveFile(file, finalDestFolder, finalFileName)

                        if (newFile != null) {
                            updateStatus("Arquivo movido: '${newFile.name}' -> $categoryName")
                            movedFilesCount++
                        } else {
                            updateStatus("Falha ao mover arquivo: '${file.name}'")
                        }
                        itemsProcessed++
                        updateProgress((itemsProcessed * 100) / totalItems)
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
        // Pega a URI da pasta selecionada (ex: Downloads)
        val uri = downloadsTreeUri ?: return

        lifecycleScope.launch {
            _isProcessing.value = true
            updateStatus("\n--- Iniciando Organização por Data ---")

            try {
                // Executa o trabalho pesado em uma thread de I/O para não bloquear a UI
                withContext(Dispatchers.IO) {
                    val downloadsRoot = DocumentFile.fromTreeUri(applicationContext, uri)
                        ?: throw IOException("Não foi possível acessar a pasta selecionada.")

                    // Filtra apenas os arquivos, ignorando pastas e arquivos ocultos
                    val filesToOrganize = downloadsRoot.listFiles().filter {
                        it.isFile && !it.name.orEmpty().startsWith('.')
                    }

                    if (filesToOrganize.isEmpty()) {
                        updateStatus("Nenhum arquivo encontrado para organizar.")
                        return@withContext
                    }

                    // Garante que a pasta base de destino exista
                    val dateOutputBase = findOrCreateDirectory(downloadsRoot, "Organizado_Por_Data")!!
                    var movedCount = 0

                    // Formatos de data para criar os nomes das pastas
                    val yearFormat = SimpleDateFormat("yyyy", Locale.getDefault())
                    val monthFormat = SimpleDateFormat("MM - MMMM", Locale.getDefault())

                    // OTIMIZAÇÃO 1: Cache para as pastas de destino (Mês/Ano)
                    val monthFolderCache = mutableMapOf<String, DocumentFile>()

                    // OTIMIZAÇÃO 2: Cache para nomes de arquivos já existentes nas pastas de destino
                    val existingFilesInDestCache = mutableMapOf<String, MutableSet<String>>()

                    filesToOrganize.forEachIndexed { index, file ->
                        val modDate = Date(file.lastModified())
                        val yearString = yearFormat.format(modDate)
                        val monthString = monthFormat.format(modDate)
                        val monthPathKey = "$yearString/$monthString" // Chave única para o cache

                        // Usa o cache para obter a pasta do mês.
                        // O código dentro de 'getOrPut' só é executado se a pasta
                        // ainda não estiver no cache (ou seja, na primeira vez que o mês aparece).
                        val monthFolder = monthFolderCache.getOrPut(monthPathKey) {
                            updateStatus("Verificando/Criando pasta: $monthPathKey")
                            val yearFolder = findOrCreateDirectory(dateOutputBase, yearString)!!
                            findOrCreateDirectory(yearFolder, monthString)!!
                        }

                        // Usa o cache para obter a lista de nomes de arquivos no destino.
                        // O código aqui só lista os arquivos do disco uma única vez por pasta.
                        val destinationExistingFiles = existingFilesInDestCache.getOrPut(monthPathKey) {
                            monthFolder.listFiles().mapNotNull { it.name }.toMutableSet()
                        }

                        // Lógica de verificação de conflito, agora usando o cache em memória (muito rápido)
                        var finalFileName = file.name!!
                        if (destinationExistingFiles.contains(finalFileName)) {
                            val baseName = finalFileName.substringBeforeLast('.')
                            val ext = finalFileName.substringAfterLast('.')
                            var suffix = 1
                            do {
                                finalFileName = "${baseName}_${suffix++}.$ext"
                            } while (destinationExistingFiles.contains(finalFileName))
                        }

                        // CHAMA A FUNÇÃO moveFile ATUALIZADA, passando o nome final
                        val movedFile = moveFile(file, monthFolder, finalFileName)

                        if (movedFile != null) {
                            // Sucesso!
                            movedCount++
                            // Adiciona o nome do arquivo recém-movido ao cache para que o próximo
                            // arquivo no loop não tente usar o mesmo nome.
                            destinationExistingFiles.add(movedFile.name!!)
                        } else {
                            // A função moveFile já deve ter logado o erro específico.
                            updateStatus("Falha ao mover: '${file.name}'")
                        }

                        // OTIMIZAÇÃO 3: Atualiza o progresso a cada 50 arquivos ou no final
                        if ((index + 1) % 50 == 0 || (index + 1) == filesToOrganize.size) {
                            val progress = ((index + 1) * 100) / filesToOrganize.size
                            updateProgress(progress)
                            updateStatus("Processando ${index + 1} de ${filesToOrganize.size}...")
                        }
                    }

                    updateStatus("\n--- Resumo da Organização por Data ---")
                    updateStatus("$movedCount de ${filesToOrganize.size} arquivos movidos com sucesso para 'Organizado_Por_Data'.")
                }
            } catch (e: Exception) {
                // Captura qualquer erro inesperado durante o processo
                updateStatus("ERRO GERAL: ${e.message}")
                e.printStackTrace() // Ajuda a depurar
            } finally {
                // Garante que o indicador de processamento seja desativado
                _isProcessing.value = false
                updateProgress(0) // Reseta a barra de progresso
            }
        }
    }
}