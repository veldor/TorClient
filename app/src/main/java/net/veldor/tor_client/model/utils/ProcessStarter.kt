package net.veldor.tor_client.model.utils

import android.system.ErrnoException
import android.util.Log
import net.veldor.tor_client.model.exceptions.TorAlreadyRanException
import net.veldor.tor_client.model.managers.LaunchLogManager
import java.io.*

class ProcessStarter {

    fun startProcess(startCommand: String): CommandResult {

        val stdout = mutableListOf<String>()
        val stderr = mutableListOf<String>()
        var exitCode: Int

        try {

            Log.d("surprise", "startProcess 17:  start process with command $startCommand")
            val process = Runtime.getRuntime().exec(startCommand)

            BufferedReader(InputStreamReader(process.inputStream)).use { bufferedReader ->
                var line = bufferedReader.readLine()
                while (line != null) {
                    Log.d("surprise", "ProcessStarter.kt 25: line is $line")
                    LaunchLogManager.instance.addToLog(line)
                    stdout.add(line)
                    if (line.contains("Address already in use")) {
                        throw TorAlreadyRanException(line)
                    }
                    line = bufferedReader.readLine()
                }
            }

            BufferedReader(InputStreamReader(process.errorStream)).use { bufferedReader ->
                var line = bufferedReader.readLine()
                while (line != null) {
                    Log.d("surprise", "startProcess 29:  $line")
                    stderr.add(line)
                    line = bufferedReader.readLine()
                }
            }

            try {
                OutputStreamWriter(process.outputStream, "UTF-8").use { writer ->
                    writer.write("exit\n")
                    writer.flush()
                }
            } catch (e: IOException) {
                //noinspection StatementWithEmptyBody
                if (e.message?.contains("EPIPE") == true || e.message?.contains("Stream closed") == true) {
                    // Method most horrid to catch broken pipe, in which case we do nothing. The command is not a shell, the
                    // shell closed stdin, the script already contained the exit command, etc. these cases we want the output
                    // instead of returning null
                } else {
                    // other issues we don't know how to handle, leads to returning null
                    Log.d("surprise", "ProcessStarter.kt 56: it is really problem!")
                    e.printStackTrace()
                    //throw e
                }
            }

            exitCode = process.waitFor()
            Log.d("surprise", "startProcess 59:  $exitCode")
            process.destroy()
        } catch (e: TorAlreadyRanException) {
            throw e
        } catch (e: InterruptedException) {
            exitCode = ShellExitCode.WATCHDOG_EXIT
        } catch (e: IOException) {
            e.printStackTrace()
            exitCode = ShellExitCode.SHELL_WRONG_UID
        }

        return CommandResult(stdout, stderr, exitCode)
    }
}
