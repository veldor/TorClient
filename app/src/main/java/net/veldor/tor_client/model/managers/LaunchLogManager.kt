package net.veldor.tor_client.model.managers

import java.text.SimpleDateFormat
import java.util.*

class LaunchLogManager private constructor() {

    private val log = ArrayList<String>()

    fun addToLog(message: String) {
        val preparedMessage = String.format(
            Locale.ENGLISH,
            "%s: %s",
            SimpleDateFormat("hh:mm:ss", Locale("Ru-ru")).format(Date()),
            message
        )
        log.add(preparedMessage)
    }

    fun getFullLog(): ArrayList<String> {
        return log
    }

    companion object {
        val instance = LaunchLogManager()
    }
}