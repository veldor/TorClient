package net.veldor.tor_client.model.managers

import android.content.Context

class InstallTorFilesManager {
    fun install(context: Context) {
        // install clear TOR files
        val zipFileManager = ZipFileManager()
        zipFileManager.extractZipFromInputStream(
            context.assets.open("tor.mp3"),
            context.applicationInfo.dataDir
        )
        // create log files dir
        StorageManager().createLogsDir(context.applicationInfo.dataDir)
        StorageManager().editConfigurationFile(context)
    }
}