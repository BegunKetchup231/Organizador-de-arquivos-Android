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
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.MobileAds
import com.google.android.material.card.MaterialCardView
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.*
import kotlin.math.log2
import kotlin.math.pow


class MainActivity : AppCompatActivity() {

    // --- Views da UI ---
    private lateinit var cardSelectFolder: MaterialCardView
    private lateinit var tvHelperText: TextView
    private lateinit var tvSelectedPath: TextView
    private lateinit var actionsGridLayout: GridLayout
    private lateinit var progressContainer: MaterialCardView
    private lateinit var statusRecyclerView: RecyclerView
    private lateinit var statusAdapter: StatusAdapter
    private lateinit var btnOrganizeCategory: Button
    private lateinit var btnCleanFiles: Button
    private lateinit var btnRemoveEmptyFolders: Button
    private lateinit var btnOrganizeByDate: Button
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
        statusRecyclerView = findViewById(R.id.status_recycler_view)
        statusAdapter = StatusAdapter(mutableListOf())
        statusRecyclerView.layoutManager = LinearLayoutManager(this).apply { stackFromEnd = true }
        statusRecyclerView.adapter = statusAdapter
        btnOrganizeCategory = findViewById(R.id.btnOrganizeCategory)
        btnOrganizeByDate = findViewById(R.id.btnOrganizeByDate)
        btnCleanFiles = findViewById(R.id.btnCleanFiles)
        btnRemoveEmptyFolders = findViewById(R.id.btnRemoveEmptyFolders)
        progressBar = findViewById(R.id.progressBar)
        progressStatusText = findViewById(R.id.progressStatusText)
        mAdView = findViewById(R.id.adView)
    }

    // FUNÇÃO SETUPCLICKLISTENERS CORRIGIDA
    private fun setupClickListeners() {
        cardSelectFolder.setOnClickListener { selectFolder() }
        btnOrganizeCategory.setOnClickListener { organizeByCategory() }
        btnOrganizeByDate.setOnClickListener { organizeByDate() }
        btnCleanFiles.setOnClickListener { cleanFiles() }
        btnRemoveEmptyFolders.setOnClickListener { removeEmptyFolders() }
    }

    // --- LÓGICA DE TRANSIÇÃO DE UI E ESTADO ---

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
            val uri = uriString.toUri()
            if (contentResolver.persistedUriPermissions.any { it.uri == uri }) {
                this@MainActivity.workDirectoryUri = uri
                this@MainActivity.showActiveStateUI(uri)
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
                statusRecyclerView.visibility = visibility
                if (!processing) {
                    updateOperationProgress(0)
                } else {
                    statusAdapter.clearMessages()
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

    // --- CHAMADAS PARA OS ESPECIALISTAS E WORKMANAGER ---

    private fun observeWork(workId: UUID) {
        WorkManager.getInstance(applicationContext)
            .getWorkInfoByIdLiveData(workId)
            .observe(this) { workInfo ->
                if (workInfo != null) {
                    val progress = workInfo.progress.getInt(OrganizationWorker.KEY_PROGRESS_PERCENT, -1)
                    val status = workInfo.progress.getString(OrganizationWorker.KEY_PROGRESS_STATUS)
                    if (progress != -1) { updateOperationProgress(progress) }
                    if (status != null) { updateStatus(status) }
                    when (workInfo.state) {
                        WorkInfo.State.RUNNING -> {
                            if (!_isProcessing.value) { _isProcessing.value = true }
                        }
                        WorkInfo.State.SUCCEEDED -> {
                            val summary = workInfo.outputData.getString(OrganizationWorker.KEY_RESULT_SUMMARY)
                            if (summary != null) { updateStatus(summary) }
                            if (_isProcessing.value) { _isProcessing.value = false }
                            WorkManager.getInstance(applicationContext).getWorkInfoByIdLiveData(workId).removeObservers(this)
                        }
                        WorkInfo.State.FAILED, WorkInfo.State.CANCELLED -> {
                            if (_isProcessing.value) { _isProcessing.value = false }
                            WorkManager.getInstance(applicationContext).getWorkInfoByIdLiveData(workId).removeObservers(this)
                        }
                        else -> {}
                    }
                }
            }
    }

    private fun organizeByCategory() {
        val uri = workDirectoryUri ?: return
        val organizer = CategoryOrganizer(applicationContext)
        lifecycleScope.launch {
            updateStatus("Analisando a pasta...")
            _isProcessing.value = true
            try {
                val analysisResult = organizer.analyze(uri)
                _isProcessing.value = false
                if (analysisResult.fileCountsByCategory.isEmpty()) {
                    updateStatus("Nenhum arquivo para organizar foi encontrado.")
                    return@launch
                }
                val messageBuilder = StringBuilder("A organização moverá:\n")
                analysisResult.fileCountsByCategory.forEach { (category, count) ->
                    messageBuilder.append("\n- $count arquivos para a categoria '$category'")
                }
                messageBuilder.append("\n\nDeseja continuar?")
                showConfirmationDialog("Confirmar Organização", messageBuilder.toString()) {
                    val inputData = workDataOf(
                        OrganizationWorker.KEY_OPERATION_TYPE to OrganizationWorker.OP_ORGANIZE_BY_CATEGORY,
                        OrganizationWorker.KEY_URI to uri.toString()
                    )
                    val workRequest = OneTimeWorkRequestBuilder<OrganizationWorker>().setInputData(inputData).build()
                    WorkManager.getInstance(applicationContext).enqueue(workRequest)
                    observeWork(workRequest.id)
                }
            } catch (e: Exception) {
                _isProcessing.value = false
                updateStatus("ERRO durante a análise: ${e.message}")
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
                val filesToMoveCount = dateOrganizer.analyze(uri)
                _isProcessing.value = false
                if (filesToMoveCount == 0) {
                    updateStatus("Nenhum arquivo para organizar por data foi encontrado.")
                    return@launch
                }
                val message = "Encontrados $filesToMoveCount arquivos para organizar por data.\n\nDeseja continuar?"
                showConfirmationDialog("Confirmar Organização por Data", message) {
                    val inputData = workDataOf(
                        OrganizationWorker.KEY_OPERATION_TYPE to OrganizationWorker.OP_ORGANIZE_BY_DATE,
                        OrganizationWorker.KEY_URI to uri.toString()
                    )
                    val workRequest = OneTimeWorkRequestBuilder<OrganizationWorker>().setInputData(inputData).build()
                    WorkManager.getInstance(applicationContext).enqueue(workRequest)
                    observeWork(workRequest.id)
                }
            } catch (e: Exception) {
                _isProcessing.value = false
                updateStatus("ERRO durante a análise: ${e.message}")
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
                val analysisResult = cleaner.analyze(uri)
                _isProcessing.value = false
                if (analysisResult.filesFound == 0) {
                    updateStatus("Nenhum arquivo temporário ou vazio encontrado.")
                    return@launch
                }
                val filesCount = analysisResult.filesFound
                val spaceToFree = convertBytes(analysisResult.spaceToFree)
                val message = "Foram encontrados $filesCount arquivos inúteis, totalizando $spaceToFree de espaço que pode ser liberado.\n\nDeseja excluí-los permanentemente?"
                showConfirmationDialog("Confirmar Limpeza", message) {
                    val inputData = workDataOf(
                        OrganizationWorker.KEY_OPERATION_TYPE to OrganizationWorker.OP_CLEAN_TEMP_FILES,
                        OrganizationWorker.KEY_URI to uri.toString()
                    )
                    val workRequest = OneTimeWorkRequestBuilder<OrganizationWorker>().setInputData(inputData).build()
                    WorkManager.getInstance(applicationContext).enqueue(workRequest)
                    observeWork(workRequest.id)
                }
            } catch (e: Exception) {
                _isProcessing.value = false
                updateStatus("ERRO durante a análise: ${e.message}")
            }
        }
    }

    private fun removeEmptyFolders() {
        val uri = workDirectoryUri ?: return
        val remover = EmptyFolderRemover(applicationContext)
        lifecycleScope.launch {
            updateStatus("\nAnalisando pastas vazias...")
            _isProcessing.value = true
            try {
                val emptyFolderCount = remover.analyze(uri)
                _isProcessing.value = false
                if (emptyFolderCount == 0) {
                    updateStatus("Nenhuma pasta vazia encontrada.")
                    return@launch
                }
                val message = "Foram encontradas $emptyFolderCount pastas vazias.\n\nDeseja excluí-las permanentemente?"
                showConfirmationDialog("Confirmar Remoção", message) {
                    val inputData = workDataOf(
                        OrganizationWorker.KEY_OPERATION_TYPE to OrganizationWorker.OP_REMOVE_EMPTY_FOLDERS,
                        OrganizationWorker.KEY_URI to uri.toString()
                    )
                    val workRequest = OneTimeWorkRequestBuilder<OrganizationWorker>().setInputData(inputData).build()
                    WorkManager.getInstance(applicationContext).enqueue(workRequest)
                    observeWork(workRequest.id)
                }
            } catch (e: Exception) {
                _isProcessing.value = false
                updateStatus("ERRO durante a análise: ${e.message}")
            }
        }
    }

    // --- Funções de UI, Persistência e Auxiliares ---

    private fun updateStatus(message: String) {
        runOnUiThread {
            val type = when {
                message.contains("--- Resumo:") || message.contains("sucesso") -> StatusType.SUCCESS
                message.contains("ERRO") || message.contains("Falha") -> StatusType.ERROR
                else -> StatusType.INFO
            }
            val cleanMessage = message.trim()
            statusAdapter.addMessage(StatusMessage(cleanMessage, type))
            statusRecyclerView.scrollToPosition(statusAdapter.itemCount - 1)
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
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
        val showDialogs = sharedPreferences.getBoolean("confirmations_enabled", true)
        if (showDialogs) {
            AlertDialog.Builder(this).setTitle(title).setMessage(message)
                .setPositiveButton("Confirmar") { dialog, _ -> onConfirm(); dialog.dismiss() }
                .setNegativeButton("Cancelar") { dialog, _ -> updateStatus("Operação cancelada."); dialog.dismiss() }
                .show()
        } else {
            onConfirm()
        }
    }

    private fun selectFolder() {
        updateStatus("Por favor, selecione a pasta que deseja organizar")
        openDocumentTreeLauncher.launch(null)
    }
}