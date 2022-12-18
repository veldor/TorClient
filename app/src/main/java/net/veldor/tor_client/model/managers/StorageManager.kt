package net.veldor.tor_client.model.managers

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.util.Log
import net.veldor.tor_client.model.utils.ChmodCommand
import java.io.*

class StorageManager {
    fun createLogsDir(appDataDir: String) {
        val logDir = File("$appDataDir/logs")
        if (!logDir.isDirectory) {
            if (logDir.mkdir()) {
                ChmodCommand.dirChmod(logDir.absolutePath, false)
            } else {
                throw IllegalStateException("Installer Create log dir failed")
            }
        }
    }

    @SuppressLint("SetWorldReadable")
    fun readTextFile(filePath: String): List<String> {
        val lines: MutableList<String> = ArrayList()
        val f = File(filePath)
        if (f.isFile) {
            FileInputStream(filePath).use { fStream ->
                BufferedReader(InputStreamReader(fStream)).use { br ->
                    var tmp: String?
                    while (br.readLine().also { tmp = it } != null) {
                        lines.add(tmp!!.trim { it <= ' ' })
                    }
                }
            }
        }
        return lines
    }

    fun cleanLogFileNoRootMethod(appDataDir: String, logFilePath: String, text: String) {
        try {
            val f = File("$appDataDir/logs")
            if (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.GINGERBREAD) {
                    f.mkdirs() && f.setReadable(true) && f.setWritable(true)
                } else {
                    f.mkdirs()
                }
            ) Log.i(
                "surprise", "log dir created"
            )
            val writer = PrintWriter("$appDataDir/$logFilePath", "UTF-8")
            writer.println(text)
            writer.close()
        } catch (e: IOException) {
            Log.e("surprise", "Unable to create dnsCrypt log file " + e.message)
        }
    }

    fun editConfigurationFile(context: Context) {
        val appDataDir: String = context.applicationInfo.dataDir
        val currentConfiguration =
            readTextFile("${context.applicationInfo.dataDir}/app_data/tor/tor.conf")
        val clearText = ArrayList<String>(arrayListOf())
        currentConfiguration.forEach {
            clearText.add(it.replace("\$path", appDataDir))
        }
        writeToTextFile(
            context, "$appDataDir/app_data/tor/tor.conf", clearText
        )
    }

    fun writeToTextFile(context: Context?, filePath: String, lines: List<String?>) {
        val f = File(filePath)
        if (f.isFile) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.GINGERBREAD) {
                f.canRead() && f.canWrite() || f.setReadable(true, false) && f.setWritable(true)
            }
        }
        PrintWriter(filePath).use { writer ->
            for (line in lines) {
                writer.println(line)
            }
        }
    }

    fun read(f: File): ByteArray {
        val b = ByteArray(f.length().toInt())
        val `in` = FileInputStream(f)
        return try {
            var offset = 0
            while (offset < b.size) {
                val read = `in`.read(b, offset, b.size - offset)
                if (read == -1) throw EOFException()
                offset += read
            }
            b
        } finally {
            `in`.close()
        }
    }

    fun readTextFileAsString(filePath: String): String {
        val stringBuilder = StringBuilder()
        try {
            val f = File(filePath)
            if (f.isFile) {
                FileInputStream(filePath).use { fStream ->
                    BufferedReader(InputStreamReader(fStream)).use { br ->
                        var tmp: String?
                        while (br.readLine().also { tmp = it } != null) {
                            stringBuilder.append(tmp)
                            stringBuilder.append("\n")
                        }
                    }
                }
            }
        } catch (t: Throwable) {
            t.printStackTrace()
        }
        return stringBuilder.toString()
    }
}