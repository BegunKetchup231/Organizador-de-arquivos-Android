package com.example.organizadordearquivos

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.GridLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.MobileAds
import com.google.android.material.card.MaterialCardView
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.log2
import kotlin.math.pow
import androidx.preference.PreferenceManager

class MainActivity : AppCompatActivity() {

    // --- Views da UI (ATUALIZADAS PARA O NOVO LAYOUT) ---
    private lateinit var cardSelectFolder: MaterialCardView
    private lateinit var tvHelperText: TextView
    private lateinit var tvSelectedPath: TextView
    private lateinit var actionsGridLayout: GridLayout
    private lateinit var progressContainer: MaterialCardView
    private lateinit var scrollViewStatus: ScrollView

    // Views que ainda são necessárias
    private lateinit var btnOrganizeCategory: Button
    private lateinit var btnCleanFiles: Button
    private lateinit var btnRemoveEmptyFolders: Button
    private lateinit var btnOrganizeByDate: Button
    private lateinit var tvStatus: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var progressStatusText: TextView
    private lateinit var mAdView: AdView

    // --- Variáveis de Estado e Configurações ---
    private var workDirectoryUri: Uri? = null
    private val _isProcessing = MutableStateFlow(false)
    private val isProcessing = _isProcessing.asStateFlow()
    private val PREFS_NAME = "OrganizerPrefs"
    private val PREF_LAST_URI = "last_uri"
    private lateinit var openDocumentTreeLauncher: ActivityResultLauncher<Uri?>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initializeViews()
        setSupportActionBar(findViewById(R.id.topAppBar))
        supportActionBar?.setDisplayShowTitleEnabled(false)
        setupOpenDocumentTreeLauncher()
        loadLastUsedUri()
        setupClickListeners()
        observeProcessingState()

        MobileAds.initialize(this) {}
        val adRequest = AdRequest.Builder().build()
        mAdView.loadAd(adRequest)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_settings -> {
                startActivity(Intent(this, SettingsActivity::class.java))
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun initializeViews() {
        cardSelectFolder = findViewById(R.id.card_select_folder)
        tvHelperText = findViewById(R.id.tv_helper_text)
        tvSelectedPath = findViewById(R.id.tv_selected_path)
        actionsGridLayout = findViewById(R.id.actions_grid_layout)
        progressContainer = findViewById(R.id.progress_container)
        scrollViewStatus = findViewById(R.id.status_scroll_view)
        tvStatus = findViewById(R.id.tvStatus)
        btnOrganizeCategory = findViewById(R.id.btnOrganizeCategory)
        btnOrganizeByDate = findViewById(R.id.btnOrganizeByDate)
        btnCleanFiles = findViewById(R.id.btnCleanFiles)
        btnRemoveEmptyFolders = findViewById(R.id.btnRemoveEmptyFolders)
        progressBar = findViewById(R.id.progressBar)
        progressStatusText = findViewById(R.id.progressStatusText)
        mAdView = findViewById(R.id.adView)
    }

    private fun setupClickListeners() {
        cardSelectFolder.setOnClickListener { selectFolder() }

        btnOrganizeCategory.setOnClickListener {
            organizeByCategory()
        }
        btnOrganizeByDate.setOnClickListener {
            organizeByDate()
        }
        btnCleanFiles.setOnClickListener {
            cleanFiles()
        }
        btnRemoveEmptyFolders.setOnClickListener {
            showConfirmationDialog("Remover Pastas Vazias", "Isso excluirá pastas vazias. Deseja continuar?", ::removeEmptyFolders)
        }
    }

    private fun setupOpenDocumentTreeLauncher() {
        openDocumentTreeLauncher = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            uri?.let {
                contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                this@MainActivity.workDirectoryUri = it
                this@MainActivity.saveLastUsedUri(it)
                this@MainActivity.showActiveStateUI(it)
            } ?: run {
                updateStatus("Seleção de pasta cancelada.")
            }
        }
    }

    private fun loadLastUsedUri() {
        val prefs: SharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val uriString = prefs.getString(PREF_LAST_URI, null)
        if (uriString.isNullOrEmpty()) return
        try {
            val uri = uriString.toUri() // Agora esta linha funcionará
            if (contentResolver.persistedUriPermissions.any { it.uri == uri }) {
                val documentFile = DocumentFile.fromTreeUri(this, uri)
                if (documentFile != null && documentFile.exists()) {
                    this@MainActivity.workDirectoryUri = uri
                    this@MainActivity.showActiveStateUI(uri)
                }
            }
        } catch (e: Exception) {
            saveLastUsedUri(null)
        }
    }

    private fun showActiveStateUI(uri: Uri) {
        val documentFile = DocumentFile.fromTreeUri(this, uri)
        val folderName = documentFile?.name ?: uri.path
        tvHelperText.visibility = View.GONE
        tvSelectedPath.text = "Operando em: $folderName"
        tvSelectedPath.visibility = View.VISIBLE
        actionsGridLayout.visibility = View.VISIBLE
        updateButtonStates()
    }

    private fun observeProcessingState() {
        lifecycleScope.launch {
            isProcessing.collectLatest { processing ->
                updateButtonStates()
                val visibility = if (processing) View.VISIBLE else View.GONE
                progressContainer.visibility = visibility
                scrollViewStatus.visibility = visibility
                if (!processing) {
                    updateOperationProgress(0)
                } else {
                    tvStatus.text = ""
                }
            }
        }
    }

    private fun updateButtonStates() {
        val isFolderSelected = workDirectoryUri != null
        val processing = isProcessing.value
        cardSelectFolder.isClickable = !processing
        btnOrganizeCategory.isEnabled = isFolderSelected && !processing
        btnOrganizeByDate.isEnabled = isFolderSelected && !processing
        btnCleanFiles.isEnabled = isFolderSelected && !processing
        btnRemoveEmptyFolders.isEnabled = isFolderSelected && !processing
    }

    private fun organizeByCategory() {
        val uri = workDirectoryUri ?: return
        val organizer = CategoryOrganizer(applicationContext)

        lifecycleScope.launch {
            updateStatus("\nAnalisando a pasta...")
            _isProcessing.value = true

            try {
                // 1. CHAMA A ANÁLISE PRIMEIRO
                val analysisResult = organizer.analyze(uri)
                _isProcessing.value = false // Termina o indicador de "analisando"

                // 2. VERIFICA SE ALGO FOI ENCONTRADO
                if (analysisResult.fileCountsByCategory.isEmpty()) {
                    updateStatus("Nenhum arquivo para organizar foi encontrado.")
                    return@launch // Encerra a operação
                }

                // 3. MONTA A MENSAGEM DETALHADA PARA O DIÁLOGO
                val messageBuilder = StringBuilder("A organização moverá:\n")
                analysisResult.fileCountsByCategory.forEach { (category, count) ->
                    messageBuilder.append("\n- $count arquivos para a categoria '$category'")
                }
                messageBuilder.append("\n\nDeseja continuar?")

                // 4. CHAMA O DIÁLOGO DE CONFIRMAÇÃO COM A NOVA MENSAGEM
                showConfirmationDialog(
                    "Confirmar Organização",
                    messageBuilder.toString()
                ) {
                    // A ação de confirmação é chamar a organização de verdade
                    runCategoryOrganization(organizer, uri)
                }

            } catch (e: Exception) {
                _isProcessing.value = false
                updateStatus("ERRO durante a análise: ${e.message}")
            }
        }
    }

    private fun runCategoryOrganization(organizer: CategoryOrganizer, uri: Uri) {
        lifecycleScope.launch {
            _isProcessing.value = true
            try {
                val result = organizer.organize(uri, ::updateStatus, ::updateOperationProgress)
                updateStatus("\n--- Resumo: ${result.movedFiles} arquivos movidos para suas categorias.")
            } catch (e: Exception) {
                updateStatus("ERRO na organização: ${e.message}")
            } finally {
                _isProcessing.value = false
            }
        }
    }

    private fun organizeByDate() {
        val uri = workDirectoryUri ?: return
        val dateOrganizer = DateOrganizer(applicationContext)

        lifecycleScope.launch {
            updateStatus("\nAnalisando a pasta para organização por data...")
            _isProcessing.value = true

            try {
                // 1. CHAMA A ANÁLISE PRIMEIRO
                val filesToMoveCount = dateOrganizer.analyze(uri)
                _isProcessing.value = false

                // 2. VERIFICA SE ALGO FOI ENCONTRADO
                if (filesToMoveCount == 0) {
                    updateStatus("Nenhum arquivo para organizar por data foi encontrado.")
                    return@launch
                }

                // 3. MONTA A MENSAGEM DETALHADA
                val message = "Encontrados $filesToMoveCount arquivos para organizar por data.\n\nDeseja continuar?"

                // 4. MOSTRA O DIÁLOGO DE CONFIRMAÇÃO
                showConfirmationDialog(
                    "Confirmar Organização por Data",
                    message
                ) {
                    // A ação de confirmação é chamar a organização de verdade
                    runDateOrganization(dateOrganizer, uri)
                }

            } catch (e: Exception) {
                _isProcessing.value = false
                updateStatus("ERRO durante a análise: ${e.message}")
            }
        }
    }

    private fun runDateOrganization(organizer: DateOrganizer, uri: Uri) {
        lifecycleScope.launch {
            _isProcessing.value = true
            try {
                val movedCount = organizer.organize(uri, ::updateStatus, ::updateOperationProgress)
                updateStatus("\n--- Resumo: $movedCount arquivos movidos por data.")
            } catch (e: Exception) {
                updateStatus("ERRO na organização: ${e.message}")
            } finally {
                _isProcessing.value = false
            }
        }
    }

    private fun cleanFiles() {
        val uri = workDirectoryUri ?: return
        val cleaner = TempFileCleaner(applicationContext)

        lifecycleScope.launch {
            updateStatus("\nAnalisando arquivos para limpeza...")
            _isProcessing.value = true

            try {
                // 1. CHAMA A ANÁLISE PRIMEIRO
                val analysisResult = cleaner.analyze(uri)
                _isProcessing.value = false

                // 2. VERIFICA SE ALGO FOI ENCONTRADO
                if (analysisResult.filesFound == 0) {
                    updateStatus("Nenhum arquivo temporário ou vazio encontrado.")
                    return@launch
                }

                // 3. MONTA A MENSAGEM DETALHADA
                val filesCount = analysisResult.filesFound
                val spaceToFree = convertBytes(analysisResult.spaceToFree) // Usamos nossa função auxiliar
                val message = "Foram encontrados $filesCount arquivos inúteis, totalizando $spaceToFree de espaço que pode ser liberado.\n\nDeseja excluí-los permanentemente?"

                // 4. MOSTRA O DIÁLOGO DE CONFIRMAÇÃO
                showConfirmationDialog(
                    "Confirmar Limpeza",
                    message
                ) {
                    // A ação de confirmação é chamar a limpeza de verdade
                    runActualCleaning(cleaner, uri)
                }

            } catch (e: Exception) {
                _isProcessing.value = false
                updateStatus("ERRO durante a análise: ${e.message}")
            }
        }
    }

    private fun runActualCleaning(cleaner: TempFileCleaner, uri: Uri) {
        lifecycleScope.launch {
            _isProcessing.value = true
            try {
                val result = cleaner.clean(uri, ::updateStatus, ::updateOperationProgress)
                val freedSpace = convertBytes(result.spaceToFree)
                updateStatus("\n--- Resumo: ${result.filesFound} arquivos removidos ($freedSpace liberados).")
            } catch (e: Exception) {
                updateStatus("ERRO na limpeza: ${e.message}")
            } finally {
                _isProcessing.value = false
            }
        }
    }

    private fun removeEmptyFolders() {
        val uri = workDirectoryUri ?: return
        val remover = EmptyFolderRemover(applicationContext)
        lifecycleScope.launch {
            _isProcessing.value = true
            try {
                val removedCount = remover.remove(uri, ::updateStatus, ::updateOperationProgress)
                updateStatus("\n--- Resumo: $removedCount pastas vazias removidas.")
            } catch (e: Exception) { updateStatus("ERRO: ${e.message}") }
            finally { _isProcessing.value = false }
        }
    }

    private fun updateStatus(message: String) {
        runOnUiThread {
            tvStatus.append("\n$message")
            scrollViewStatus.post { scrollViewStatus.fullScroll(View.FOCUS_DOWN) }
        }
    }

    private fun updateOperationProgress(percentage: Int) {
        runOnUiThread {
            progressBar.progress = percentage
            progressStatusText.text = "Progresso - ($percentage%)"
        }
    }

    private fun convertBytes(num: Long): String {
        if (num < 1024) return "$num bytes"
        val units = listOf("bytes", "KB", "MB", "GB", "TB")
        val i = (log2(num.toDouble()) / log2(1024.0)).toInt()
        val size = num / 1024.0.pow(i.toDouble())
        return String.format(Locale.getDefault(), "%.1f %s", size, units[i])
    }

    private fun saveLastUsedUri(uri: Uri?) {
        val prefs: SharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit { putString(PREF_LAST_URI, uri?.toString()) }
    }

    private fun showConfirmationDialog(title: String, message: String, onConfirm: () -> Unit) {
        // 1. Acessa as configurações padrão do aplicativo
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)

        // 2. Lê o valor do nosso interruptor.
        // A chave "confirmations_enabled" é a mesma que definimos no root_preferences.xml.
        // O `true` é o valor padrão caso a configuração nunca tenha sido tocada.
        val showDialogs = sharedPreferences.getBoolean("confirmations_enabled", true)

        if (showDialogs) {
            // 3a. Se a opção estiver LIGADA, mostra o diálogo de confirmação como antes.
            AlertDialog.Builder(this).setTitle(title).setMessage(message)
                .setPositiveButton("Confirmar") { dialog, _ ->
                    onConfirm()
                    dialog.dismiss()
                }
                .setNegativeButton("Cancelar") { dialog, _ ->
                    updateStatus("Operação cancelada.")
                    dialog.dismiss()
                }.show()
        } else {
            // 3b. Se a opção estiver DESLIGADA, executa a ação diretamente, sem perguntar.
            onConfirm()
        }
    }

    private fun selectFolder() {
        updateStatus("Por favor, selecione a pasta que deseja organizar")
        openDocumentTreeLauncher.launch(null)
    }
}