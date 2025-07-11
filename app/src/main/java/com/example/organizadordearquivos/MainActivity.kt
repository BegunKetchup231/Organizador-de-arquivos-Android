package com.example.organizadordearquivos

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
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
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.MobileAds
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.text.NumberFormat
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
    private lateinit var mAdView: AdView
    private var workDirectoryUri: Uri? = null

    // --- Variáveis de Estado e Configurações ---
    private val _isProcessing = MutableStateFlow(false)
    private val isProcessing = _isProcessing.asStateFlow()
    private val PREFS_NAME = "OrganizerPrefs"
    private val PREF_LAST_URI = "last_uri"
    private lateinit var openDocumentTreeLauncher: ActivityResultLauncher<Uri?>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initializeViews()
        setupOpenDocumentTreeLauncher()
        loadLastUsedUri()
        setupClickListeners()
        observeProcessingState()

        // Carregar Anúncio
        MobileAds.initialize(this) {}
        val adRequest = AdRequest.Builder().build()
        mAdView.loadAd(adRequest)
    }

    private fun initializeViews() {
        tvStatus = findViewById(R.id.tvStatus)
        progressBar = findViewById(R.id.progressBar)
        progressStatusText = findViewById(R.id.progressStatusText)
        btnSelectDownloads = findViewById(R.id.btnSelectDownloads)
        btnOrganizeCategory = findViewById(R.id.btnOrganizeCategory)
        btnCleanFiles = findViewById(R.id.btnCleanFiles)
        btnRemoveEmptyFolders = findViewById(R.id.btnRemoveEmptyFolders)
        btnOrganizeByDate = findViewById(R.id.btnOrganizeByDate)
        mAdView = findViewById(R.id.adView)
    }

    private fun setupClickListeners() {
        btnSelectDownloads.setOnClickListener { selectFolder() }
        btnOrganizeCategory.setOnClickListener {
            showConfirmationDialog("Organizar por Categoria", "Isso moverá arquivos e pastas. Deseja continuar?", ::organizeByCategory)
        }
        btnOrganizeByDate.setOnClickListener {
            showConfirmationDialog("Organizar por Data", "Isso moverá arquivos para pastas de Ano/Mês. Deseja continuar?", ::organizeByDate)
        }
        btnCleanFiles.setOnClickListener {
            showConfirmationDialog("Limpar Arquivos", "Isso excluirá arquivos vazios e temporários. Deseja continuar?", ::cleanFiles)
        }
        btnRemoveEmptyFolders.setOnClickListener {
            showConfirmationDialog("Remover Pastas Vazias", "Isso excluirá pastas vazias. Deseja continuar?", ::removeEmptyFolders)
        }
    }

    private fun observeProcessingState() {
        lifecycleScope.launch {
            isProcessing.collectLatest { processing ->
                updateButtonStates()
                if (!processing) {
                    updateOperationProgress(0)
                }
            }
        }
    }

    private fun updateButtonStates() {
        val isFolderSelected = workDirectoryUri != null
        val processing = isProcessing.value
        btnSelectDownloads.isEnabled = !processing
        btnOrganizeCategory.isEnabled = isFolderSelected && !processing
        btnOrganizeByDate.isEnabled = isFolderSelected && !processing
        btnCleanFiles.isEnabled = isFolderSelected && !processing
        btnRemoveEmptyFolders.isEnabled = isFolderSelected && !processing
    }

    // --- Chamadas para os Especialistas ---

    private fun organizeByCategory() {
        val uri = workDirectoryUri ?: return
        val organizer = CategoryOrganizer(applicationContext)
        lifecycleScope.launch {
            _isProcessing.value = true
            try {
                val result = organizer.organize(uri, ::updateStatus, ::updateOperationProgress)
                updateStatus("\n--- Resumo: ${result.movedFolders} pastas e ${result.movedFiles} arquivos movidos.")
            } catch (e: Exception) { updateStatus("ERRO: ${e.message}") }
            finally { _isProcessing.value = false }
        }
    }

    private fun organizeByDate() {
        val uri = workDirectoryUri ?: return
        val organizer = DateOrganizer(applicationContext)
        lifecycleScope.launch {
            _isProcessing.value = true
            try {
                val movedCount = organizer.organize(uri, ::updateStatus, ::updateOperationProgress)
                updateStatus("\n--- Resumo: $movedCount arquivos movidos por data.")
            } catch (e: Exception) { updateStatus("ERRO: ${e.message}") }
            finally { _isProcessing.value = false }
        }
    }

    private fun cleanFiles() {
        val uri = workDirectoryUri ?: return
        val cleaner = TempFileCleaner(applicationContext)
        lifecycleScope.launch {
            _isProcessing.value = true
            try {
                val result = cleaner.clean(uri, ::updateStatus, ::updateOperationProgress)
                val freedSpace = convertBytes(result.spaceFreed) // Formatação fica na UI
                updateStatus("\n--- Resumo: ${result.filesRemoved} arquivos removidos ($freedSpace liberados).")
            } catch (e: Exception) { updateStatus("ERRO: ${e.message}") }
            finally { _isProcessing.value = false }
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
        runOnUiThread { tvStatus.append("\n$message") }
    }

    private fun updateOperationProgress(percentage: Int) {
        runOnUiThread {
            progressBar.progress = percentage
            progressStatusText.text = "Progresso - ($percentage%)"
        }
    }

    // Função auxiliar que só a UI precisa
    private fun convertBytes(num: Long): String {
        if (num < 1024) return "$num bytes"
        val units = listOf("bytes", "KB", "MB", "GB", "TB")
        val i = (log2(num.toDouble()) / log2(1024.0)).toInt()
        val size = num / 1024.0.pow(i.toDouble())
        return String.format(Locale.getDefault(), "%.1f %s", size, units[i])
    }

    private fun setupOpenDocumentTreeLauncher() {
        openDocumentTreeLauncher = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            uri?.let {
                contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                workDirectoryUri = it
                saveLastUsedUri(it)
                updateStatus("Pasta selecionada: ${it.path}")
                updateButtonStates()
            } ?: run {
                updateStatus("Seleção de pasta cancelada.")
            }
        }
    }

    private fun saveLastUsedUri(uri: Uri?) {
        val prefs: SharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit { putString(PREF_LAST_URI, uri?.toString()) }
    }

    private fun loadLastUsedUri() {
        val prefs: SharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val uriString = prefs.getString(PREF_LAST_URI, null)
        if (uriString.isNullOrEmpty()) return
        try {
            val uri = uriString.toUri()
            if (contentResolver.persistedUriPermissions.any { it.uri == uri }) {
                val documentFile = DocumentFile.fromTreeUri(this, uri)
                if (documentFile != null && documentFile.exists()) {
                    workDirectoryUri = uri
                    updateStatus("Última pasta usada carregada: ${documentFile.name ?: uri.path}")
                }
            }
        } catch (e: Exception) {
            saveLastUsedUri(null)
        }
    }

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

    private fun selectFolder() {
        updateStatus("Por favor, selecione a pasta que deseja organizar")
        openDocumentTreeLauncher.launch(null)
    }
}