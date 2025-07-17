package com.example.organizadordearquivos

// Enum para definir os tipos de mensagem visualmente
enum class StatusType {
    INFO,
    SUCCESS,
    ERROR
}

// A estrutura de dados para cada linha do nosso log
data class StatusMessage(
    val text: String,
    val type: StatusType,
    val timestamp: Long = System.currentTimeMillis() // Guarda o momento da mensagem
)