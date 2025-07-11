package com.example.organizadordearquivos

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.settings_activity)

        // Se o savedInstanceState for nulo, significa que a activity está sendo criada pela primeira vez
        if (savedInstanceState == null) {
            supportFragmentManager
                .beginTransaction()
                .replace(R.id.settings, SettingsFragment())
                .commit()
        }

        // ESTA LINHA É FUNDAMENTAL: Ativa a seta de "Voltar" na barra de ferramentas
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    // ESTA FUNÇÃO GARANTE QUE O CLIQUE NA SETA FUNCIONE CORRETAMENTE
    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }
}