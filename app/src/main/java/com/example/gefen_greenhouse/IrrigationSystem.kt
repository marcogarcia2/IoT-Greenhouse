package com.example.gefen_greenhouse

import android.util.Log
import androidx.lifecycle.ViewModel
import com.google.firebase.database.*
import kotlinx.coroutines.NonCancellable.children
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.Calendar

// Classe que abstrai o sistema de irrigação
class IrrigationSystem : ViewModel() {

    var todayResults: MutableMap<String, Char> = mutableMapOf()
    var today: String = getCurrentDate()
    private var database: DatabaseReference
    val statusDict = mapOf(
        'N' to R.string.aguardando,
        'S' to R.string.sucesso,
        'F' to R.string.falhou,
        'I' to R.string.indeterminado
    )

    init {
        // Inicializando o Firebase Database
        database = FirebaseDatabase.getInstance().reference.child("Estufa")
    }

    // Organiza os resultados da leitura de hoje, em um dicionário todayResults.
    // --> Destinado a Home do app
    fun monitorTodayResults(onUpdate: () -> Unit) {
        var pendingUpdates = 2 // Contador para rastrear as atualizações pendentes
        todayResults.clear()
        today = getCurrentDate()

        // Atualiza os resultados de hoje do nó correspondente
        database.child(today).addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()) {
                    for (child in snapshot.children) {
                        val time = child.key
                        val status = child.getValue(String::class.java)?.firstOrNull() ?: 'I'
                        if (time != null) {
                            todayResults[time] = status
                        }
                    }
                    Log.d("Firebase", "Dados do nó $today atualizados: $todayResults")
                } else {
                    Log.d("Firebase", "Nenhum dado encontrado no nó $today.")
                }

                // Reduz o contador e chama onUpdate se for o último
                pendingUpdates--
                if (pendingUpdates == 0) {
                    onUpdate()
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("Firebase", "Erro ao monitorar dados do nó $today: ${error.message}")
                pendingUpdates--
                if (pendingUpdates == 0) {
                    onUpdate()
                }
            }
        })

        // Adiciona horários manuais do nó "000h"
        database.child("000h").get().addOnSuccessListener { snapshot ->
            if (snapshot.exists()) {
                for (child in snapshot.children) {
                    // Essas são as horas de funcionamento definidas no BD
                    val time = child.getValue(String::class.java)

                    if (time != null && !todayResults.containsKey(time)) {
                        Log.d("000h", "${child.getValue(String::class.java)}")
                        // É um horário que não tem nenhum valor associado mas existe no BD
                        val timeInSeconds = timeToSeconds(time)
                        if (timeInSeconds != null) {
                            if (getCurrentTimeInSeconds() < timeInSeconds + (5 * 60)) {
                                todayResults[time] = 'N'
                            } else {
                                todayResults[time] = 'I'
                            }
                            Log.d("Firebase", "Valor manual adicionado: $time -> ${todayResults[time]}")
                            todayResults = todayResults.toSortedMap().toMutableMap()
                        }
                    }
                }
            } else {
                Log.d("Firebase", "Nenhum dado encontrado no nó 000h.")
            }

            // Reduz o contador e chama onUpdate se for o último
            pendingUpdates--
            if (pendingUpdates == 0) {
                onUpdate()
            }
        }.addOnFailureListener { exception ->
            Log.e("Firebase", "Erro ao acessar o nó 000h: ${exception.message}")
            pendingUpdates--
            if (pendingUpdates == 0) {
                onUpdate()
            }
        }
    }

    // Retorna a data de hoje pelo sistema
    fun getCurrentDate(): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        return dateFormat.format(Date())
    }

    private fun timeToSeconds(time: String): Int? {
        val parts = time.split(":")
        if (parts.size == 2) {
            val hours = parts[0].toIntOrNull()
            val minutes = parts[1].toIntOrNull()
            if (hours != null && minutes != null) {
                return hours * 3600 + minutes * 60
            }
        }
        return null // Retorna null se o formato não for válido
    }

    fun getCurrentTimeInSeconds(): Int {
        val calendar = Calendar.getInstance()
        val hours = calendar.get(Calendar.HOUR_OF_DAY)
        val minutes = calendar.get(Calendar.MINUTE)
        val seconds = calendar.get(Calendar.SECOND)
        return hours * 3600 + minutes * 60 + seconds
    }

    fun fetchHistory(onUpdate: (Map<String, Map<String, Char>>) -> Unit) {
        val historyResults: MutableMap<String, MutableMap<String, Char>> = mutableMapOf()
        val currentDate = getCurrentDate()

        for (i in 0 until 10) {
            val date = getPreviousDate(currentDate, i)
            database.child(date).get().addOnSuccessListener { snapshot ->
                if (snapshot.exists()) {
                    val dayResults: MutableMap<String, Char> = mutableMapOf()
                    for (child in snapshot.children) {
                        val time = child.key
                        val status = child.getValue(String::class.java)?.firstOrNull() ?: 'I'
                        if (time != null) {
                            dayResults[time] = status
                        }
                    }
                    historyResults[date] = dayResults.toSortedMap().toMutableMap()
                }

                if (historyResults.size == 10 || i == 9) { // Atualiza ao final do loop
                    onUpdate(historyResults.toSortedMap().toMutableMap())
                }
            }.addOnFailureListener {
                Log.e("Firebase", "Erro ao carregar dados de $date")
            }
        }
    }

    // Retorna uma data `n` dias antes de uma data fornecida
    private fun getPreviousDate(date: String, daysBefore: Int): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val calendar = Calendar.getInstance()
        calendar.time = dateFormat.parse(date)!!
        calendar.add(Calendar.DAY_OF_YEAR, -daysBefore)
        return dateFormat.format(calendar.time)
    }


}