package net.veldor.tor_client.model.managers

import android.content.Context
import android.os.Build
import android.util.Log
import net.veldor.tor_client.model.exceptions.TorAuthCookieNotFoundException
import net.veldor.tor_client.model.exceptions.TorInitInProgressException
import net.veldor.tor_client.model.tor_utils.OnionProxyManager
import net.veldor.tor_client.model.tor_utils.OnionProxyManagerEventHandler
import net.veldor.tor_client.model.tor_utils.TorControlConnection
import net.veldor.tor_client.model.utils.ProcessStarter
import java.io.File
import java.net.ConnectException
import java.net.Socket

class TorManager private constructor() {

    private var mControlConnection: TorControlConnection? = null
    private var mBootstrapped: Boolean? = false
    private var mControlSocket: Socket? = null
    private var connectionInProgress = false

    fun connect(context: Context): Int {
        if (!connectionInProgress) {
            connectionInProgress = true
            val appDataDir: String = context.applicationInfo.dataDir
            StorageManager().cleanLogFileNoRootMethod(
                appDataDir, "logs/Tor.log",
                "Tor version: test"
            )
            val nativeLibPath: String =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.GINGERBREAD) {
                    context.applicationInfo.nativeLibraryDir
                } else {
                    context.applicationInfo.dataDir + "/lib"
                }
            val torPath = "$nativeLibPath/libtor.so"
            val file = File(torPath)
            Log.d("surprise", "TorManager.kt 39: ${file.isFile} ${file.exists()}")
            val torCmdString = (torPath + " -f "
                    + appDataDir + "/app_data/tor/tor.conf -pidfile " + appDataDir + "/tor.pid")
            val shellResult = ProcessStarter().startProcess(torCmdString)
            connectionInProgress = false
            return shellResult.exitCode
        } else {
            throw TorInitInProgressException()
        }
    }

    fun isTorRunning(): Boolean {
        return try {
            val s = Socket("127.0.0.1", 9050)
            true
        } catch (e: ConnectException) {
            false
        }
    }

    fun establishTorControlConnection(context: Context): Boolean {
        val appDataDir: String = context.applicationInfo.dataDir
        val controlPort = 5500
        val authCookie = File("$appDataDir/tor_data/control_auth_cookie")
        if (authCookie.isFile && authCookie.exists()) {
            mControlSocket = Socket("127.0.0.1", controlPort)
            mControlConnection = TorControlConnection(mControlSocket)
            mControlConnection!!.authenticate(StorageManager().read(authCookie))
            // Tell Tor to exit when the control connection is closed
            mControlConnection!!.takeOwnership()
            mControlConnection!!.resetConf(listOf(OnionProxyManager.OWNER))
            mControlConnection!!.setEventHandler(OnionProxyManagerEventHandler())
            mControlConnection!!.setEvents(listOf(*OnionProxyManager.EVENTS))
            return true

        }
        throw TorAuthCookieNotFoundException()
    }


    fun stop(context: Context): Boolean {
        if (mControlConnection != null) {
            LaunchLogManager.instance.addToLog("Have TOR control connection")
            mControlConnection!!.setConf("DisableNetwork", "1")
            mControlConnection!!.shutdownTor("TERM")
            mControlSocket?.close()
            mControlConnection = null
            mBootstrapped = false
            LaunchLogManager.instance.addToLog("TOR stopped via existent control connection")
            Log.d("surprise", "stop 140:  tor stopped")
        } else {
            try {
                // try to create new control connection
                if (establishTorControlConnection(context)) {
                    Log.d("surprise", "stop 157:  new control connection created")
                    mControlConnection!!.setConf("DisableNetwork", "1")
                    mControlConnection!!.shutdownTor("TERM")
                    mControlSocket?.close()
                    mControlConnection = null
                    mBootstrapped = false
                    LaunchLogManager.instance.addToLog("TOR stopped via new control connection")
                    Log.d("surprise", "stop 140:  tor stopped")
                } else {
                    // try to stop with pid
                    val pid = getTorPid(context)
                    if (pid != null && pid != "") {
                        try {
                            Log.d("surprise", "stop 103:  try to stop with PID")
                            val cmd = "taskkill /F /PID $pid"
                            Runtime.getRuntime().exec(cmd)
                            Log.d("surprise", "stop 108:  sent command to kill tor task with PID")
                        } catch (t: Throwable) {
                            Log.d("surprise", "stop 108:  error when stop with PID")
                            t.printStackTrace()
                        }
                    }
                }
            } catch (e: TorAuthCookieNotFoundException) {
                val pid = getTorPid(context)
                if (pid != null && pid != "") {
                    try {
                        val cmd = "taskkill /F /PID $pid"
                        Runtime.getRuntime().exec(cmd)
                        Log.d("surprise", "stop 108:  sent command to kill tor task with PID")
                    } catch (t: Throwable) {
                        Log.d("surprise", "stop 108:  error when stop with PID")
                        t.printStackTrace()
                    }
                }
            }
        }
        var countForStop = 10
        while (countForStop > 0) {
            if (!isTorRunning()) {
                return true
            }
            --countForStop
            Thread.sleep(1000)
        }
        LaunchLogManager.instance.addToLog("Can't control connection for TOR")
        Log.d("surprise", "stop 143:  no control connection for stop TOR")
        return false
    }

    fun getConnectionTime(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            120000
        } else {
            240000
        }
    }

    private fun getTorPid(context: Context): String? {
        val appDataDir: String = context.applicationInfo.dataDir
        val pidFile = "$appDataDir/tor.pid"
        val pid = StorageManager().readTextFileAsString(pidFile)
        if (pid.isNotEmpty()) {
            return pid.trim()
        }
        return null
    }

    fun torBootstrapped() {
        mBootstrapped = true
    }

    fun isTorBootstrapped(): Boolean {
        return mBootstrapped == true
    }

    companion object {
        const val MIRROR_LINK =
            "http://flibustaongezhld6dibs2dps6vm4nvqg2kp7vgowbu76tzopgnhazqd.onion/"
        const val OPDS_LINK =
            "http://flibustaongezhld6dibs2dps6vm4nvqg2kp7vgowbu76tzopgnhazqd.onion/opds"
        const val OPDS_START_VALUE = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                " <feed xmlns=\"http://www.w3.org/2005/Atom\" xmlns:dc=\"http://purl.org/dc/terms/\" xmlns:os=\"http://a9.com/-/spec/opensearch/1.1/\" xmlns:opds=\"http://opds-spec.org/2010/catalog\"> <id>tag:root</id>\n" +
                " <title>Flibusta catalog</title>"
        val instance: TorManager = TorManager()
    }
}