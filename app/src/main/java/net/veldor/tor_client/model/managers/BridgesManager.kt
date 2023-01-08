package net.veldor.tor_client.model.managers

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.util.Base64
import android.util.Log
import net.veldor.tor_client.model.bridges.BridgeType
import net.veldor.tor_client.model.bridges.SnowflakeConfigurator
import net.veldor.tor_client.model.exceptions.InvalidParsedCaptchaException
import org.jsoup.Jsoup
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.*
import java.util.concurrent.CancellationException
import javax.net.ssl.HttpsURLConnection

class BridgesManager {
    private var currentBridgesType: BridgeType? = null
    private val bridgesInUse: ArrayList<String> = ArrayList()
    private val torConf: ArrayList<String> = ArrayList()

    fun loadTgBridges(context: Context): Boolean {
        val bridgesAnswer = ConnectionManager().directConnect("https://t.me/s/mosty_tor")
        if (bridgesAnswer.statusCode < 400) {
            val reader = BufferedReader(bridgesAnswer.inputStream!!.reader())
            reader.use { read ->
                val bridges = read.readText()
                val parsed = Jsoup.parse(bridges)
                val codeElements = parsed.getElementsByTag("code")
                if (codeElements.isNotEmpty()) {
                    val bridgesText = codeElements.last().text().replace("obfs4", "\nobfs4")
                    if (bridgesText.isNotEmpty()) {
                        readTorConfiguration(context)
                        bridgesInUse.clear()
                        val bridgeItems = bridgesText.split("\n")
                        bridgeItems.forEach {
                            LaunchLogManager.addToLog("Bridges add $it")
                            bridgesInUse.add(it)
                        }
                        if (bridgesInUse.isNotEmpty()) {
                            val testBridge = bridgesInUse.toString()
                            currentBridgesType =
                                if (testBridge.contains(BridgeType.obfs4.toString())) {
                                    BridgeType.obfs4
                                } else if (testBridge.contains(BridgeType.obfs3.toString())) {
                                    BridgeType.obfs3
                                } else if (testBridge.contains(BridgeType.scramblesuit.toString())) {
                                    BridgeType.scramblesuit
                                } else if (testBridge.contains(BridgeType.meek_lite.toString())) {
                                    BridgeType.meek_lite
                                } else if (testBridge.contains(BridgeType.snowflake.toString())) {
                                    BridgeType.snowflake
                                } else {
                                    BridgeType.vanilla
                                }
                            saveConfigFile(context)
                            return true
                        } else {
                            LaunchLogManager.addToLog("Bridges not found in tg response")
                            return false
                        }
                    }
                }
            }
        }
        return false
    }

    fun readTorConfiguration(context: Context) {
        torConf.clear()
        bridgesInUse.clear()
        val appDataDir: String = context.applicationInfo.dataDir
        val currentConfiguration =
            StorageManager().readTextFile("$appDataDir/app_data/tor/tor.conf")
        if (currentConfiguration.isNotEmpty()) {
            currentConfiguration.forEach {
                var line = it
                if (line.trim().isNotEmpty()) {
                    torConf.add(line)
                }
                if (!line.contains("#") && line.contains("Bridge ")) {
                    if (line.contains(BridgeType.snowflake.toString())) {
                        line = line.replace("utls-imitate.+?( |\\z)".toRegex(), "")
                    }
                    bridgesInUse.add(line.replace("Bridge ", "").trim { it <= ' ' })
                }
                if (bridgesInUse.isNotEmpty()) {
                    val testBridge = bridgesInUse.toString()
                    if (testBridge.contains(BridgeType.obfs4.toString())) {
                        currentBridgesType = BridgeType.obfs4
                    } else if (testBridge.contains(BridgeType.obfs3.toString())) {
                        currentBridgesType = BridgeType.obfs3
                    } else if (testBridge.contains(BridgeType.scramblesuit.toString())) {
                        currentBridgesType = BridgeType.scramblesuit
                    } else if (testBridge.contains(BridgeType.meek_lite.toString())) {
                        currentBridgesType = BridgeType.meek_lite
                    } else if (testBridge.contains(BridgeType.snowflake.toString())) {
                        currentBridgesType = BridgeType.snowflake
                    } else {
                        currentBridgesType = BridgeType.vanilla
                    }
                } else {
                    currentBridgesType = BridgeType.undefined
                }
            }
        }
    }

    private fun saveConfigFile(context: Context) {
        val appDataDir: String = context.applicationInfo.dataDir
        val torConfCleaned = ArrayList<String>()

        for (i in torConf.indices) {
            val line = torConf[i]
            if ((line.contains("#") || (!line.contains("Bridge ") && !line.contains("ClientTransportPlugin ") && !line.contains(
                    "UseBridges "
                ))) && line.isNotEmpty()
            ) {
                torConfCleaned.add(line)
            }
        }

        val currentBridgesTypeToSave: String = if (currentBridgesType == BridgeType.vanilla) {
            ""
        } else {
            currentBridgesType.toString()
        }

        if (bridgesInUse.isNotEmpty() && currentBridgesType!! != BridgeType.undefined) {
            torConfCleaned.add("UseBridges 1")
            if (currentBridgesType!! != BridgeType.vanilla) {
                val clientTransportPlugin: String =
                    if (currentBridgesType!! == BridgeType.snowflake) {
                        SnowflakeConfigurator(context).getConfiguration()
                    } else {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.GINGERBREAD) {
                            ("ClientTransportPlugin " + currentBridgesTypeToSave + " exec " + context.applicationInfo.nativeLibraryDir + "/libobfs4proxy.so")
                        } else {
                            TODO("VERSION.SDK_INT < GINGERBREAD")
                        }
                    }
                torConfCleaned.add(clientTransportPlugin)
            }
            for (currentBridge in bridgesInUse) {
                if (currentBridgesType === BridgeType.vanilla) {
                    if (currentBridge.isNotEmpty() && !currentBridge.contains(BridgeType.obfs4.toString()) && !currentBridge.contains(
                            BridgeType.obfs3.toString()
                        ) && !currentBridge.contains(
                            BridgeType.scramblesuit.toString()
                        ) && !currentBridge.contains(BridgeType.meek_lite.toString()) && !currentBridge.contains(
                            BridgeType.snowflake.toString()
                        )
                    ) {
                        torConfCleaned.add("Bridge $currentBridge")
                    }
                } else {
                    if (currentBridge.isNotEmpty() && currentBridge.contains(currentBridgesType.toString())) {
                        if (currentBridgesType!! == BridgeType.snowflake) {
                            torConfCleaned.add(
                                "Bridge " + currentBridge + " utls-imitate=" + SnowflakeConfigurator(
                                    context
                                ).getUtlsClientID()
                            )
                        } else {
                            torConfCleaned.add("Bridge $currentBridge")
                        }
                    }
                }
            }
        } else {
            torConfCleaned.add("UseBridges 0")
        }

        if (torConfCleaned.size == torConf.size && torConfCleaned.containsAll(torConf)) {
            return
        }

        StorageManager().writeToTextFile(
            context, "$appDataDir/app_data/tor/tor.conf", torConfCleaned
        )
    }

    fun requestOfficialBridgesCaptcha(): Pair<Bitmap?, String?> {
        val altLink = URL("https://bridges.torproject.org/bridges/?transport=obfs4")
        val connection = altLink.openConnection() as HttpURLConnection
        connection.apply {
            requestMethod = "GET"
            connectTimeout = 3000
            readTimeout = 3000
            setRequestProperty(
                "User-Agent",
                "Mozilla/5.0 (Windows NT 6.1; rv:60.0) Gecko/20100101 Firefox/60.0"
            )
            connect()
        }
        return parseCaptchaImage(connection.inputStream)
    }


    @Throws(IOException::class)
    fun parseCaptchaImage(inputStream: InputStream?): Pair<Bitmap?, String?> {
        BufferedReader(InputStreamReader(inputStream)).use { bufferedReader ->
            var codeImage: Bitmap? = null
            val captcha_challenge_field_value: String
            var inputLine: String
            var imageFound = false
            while (bufferedReader.readLine().also { inputLine = it } != null
                && !Thread.currentThread().isInterrupted
            ) {
                if (inputLine.contains("data:image/jpeg;base64") && !imageFound) {
                    val imgCodeBase64 =
                        inputLine.replace("data:image/jpeg;base64,", "").split("\"".toRegex())
                            .toTypedArray()
                    check(imgCodeBase64.size >= 4) { "Tor Project web site error" }
                    val data =
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.FROYO) {
                            Base64.decode(imgCodeBase64[3], Base64.DEFAULT)
                        } else {
                            TODO("VERSION.SDK_INT < FROYO")
                        }
                    codeImage = BitmapFactory.decodeByteArray(data, 0, data.size)
                    imageFound = true
                    checkNotNull(codeImage) {
                        LaunchLogManager.addToLog("Get official captcha: Tor Project website error")
                        "Tor Project web site error"
                    }
                } else if (inputLine.contains("captcha_challenge_field") && inputLine.contains("value")) {
                    val secretCodeArr =
                        inputLine.split("\"".toRegex()).toTypedArray()
                    return if (secretCodeArr.size > 5) {
                        captcha_challenge_field_value = secretCodeArr[5]
                        Pair(
                            codeImage,
                            captcha_challenge_field_value
                        )
                    } else {
                        LaunchLogManager.addToLog("Get official captcha: Tor Project website error")
                        throw IllegalStateException("Tor Project website error")
                    }
                }
            }
        }
        LaunchLogManager.addToLog("Get official captcha: Possible Tor Project website data scheme changed")
        throw CancellationException("Possible Tor Project website data scheme changed")
    }


    fun getOfficialBridges(parsedValue: String, secretCode: String, context: Context): Boolean {
        LaunchLogManager.addToLog("Send encoded captcha to TOR server")
        val altLink = URL("https://bridges.torproject.org/bridges/?transport=obfs4")
        val data = linkedMapOf<String, String>().apply {
            put("captcha_challenge_field", secretCode)
            put("captcha_response_field", parsedValue)
            put("submit", "submit")
        }
        val connection = altLink.openConnection() as HttpsURLConnection

        try {
            val query = mapToQuery(data)

            connection.apply {
                requestMethod = "POST"
                setRequestProperty("User-Agent", TOR_BROWSER_USER_AGENT)
                setRequestProperty(
                    "Content-Length",
                    query.toByteArray().size.toString()
                )
                doOutput = true
                connectTimeout = 5000
                readTimeout = 5000
            }.connect()

            connection.outputStream.bufferedWriter().use {
                it.write(query)
                it.flush()
            }

            val response = connection.responseCode
            if (response == HttpURLConnection.HTTP_OK) {
                parseAnswer(connection.inputStream, context)
                return true
            } else {
                throw IOException("HttpsConnectionManager $altLink response code $response")
            }
        } finally {
            connection.disconnect()
        }
    }


    private fun parseAnswer(inputStream: InputStream?, context: Context): Boolean {
        BufferedReader(InputStreamReader(inputStream)).use { bufferedReader ->
            var codeImage: Bitmap? = null
            val captcha_challenge_field_value: String
            var inputLine: String
            var keyWordBridge = false
            var wrongImageCode = false
            var imageFound = false
            val newBridges: MutableList<String> =
                LinkedList()
            val sb = StringBuilder()
            while (bufferedReader.readLine().also { inputLine = it } != null
                && !Thread.currentThread().isInterrupted
            ) {
                if (inputLine.contains("id=\"bridgelines\"") && !wrongImageCode) {
                    keyWordBridge = true
                } else if (inputLine.contains("<br />")
                    && keyWordBridge
                    && !wrongImageCode
                ) {
                    newBridges.add(inputLine.replace("<br />", "").trim { it <= ' ' })
                } else if (inputLine.contains("</div>")
                    && keyWordBridge
                    && !wrongImageCode
                ) {
                    break
                } else if (inputLine.contains("bridgedb-captcha-container")) {
                    wrongImageCode = true
                } else if (wrongImageCode) {
                    if (inputLine.contains("data:image/jpeg;base64") && !imageFound) {
                        val imgCodeBase64 =
                            inputLine.replace("data:image/jpeg;base64,", "").split("\"".toRegex())
                                .toTypedArray()
                        check(imgCodeBase64.size >= 4) { "Tor Project web site error" }
                        val data = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.FROYO) {
                            Base64.decode(
                                imgCodeBase64[3],
                                Base64.DEFAULT
                            )
                        } else {
                            TODO("VERSION.SDK_INT < FROYO")
                        }
                        codeImage = BitmapFactory.decodeByteArray(data, 0, data.size)
                        imageFound = true
                        checkNotNull(codeImage) { "Tor Project web site error" }
                    } else if (inputLine.contains("captcha_challenge_field") && inputLine.contains(
                            "value"
                        )
                    ) {
                        throw InvalidParsedCaptchaException()
                    }
                }
            }
            if (keyWordBridge && !wrongImageCode) {
                for (bridge in newBridges) {
                    sb.append(bridge).append(10.toChar())
                }
                saveCustomBridges(context, sb.toString())
                return true
            } else check(!(!keyWordBridge && !wrongImageCode)) { "Tor Project web site error!" }
        }

        throw CancellationException("Possible Tor Project website data scheme changed")
    }

    private fun mapToQuery(data: Map<String, String>) = data.entries.joinToString("&") {
        "${URLEncoder.encode(it.key, "UTF-8")}=${URLEncoder.encode(it.value, "UTF-8")}"
    }



    fun saveCustomBridges(context: Context, bridges: String) {
        Log.d("surprise", "saveCustomBridges 330:  saving bridges $bridges")
        readTorConfiguration(context)
        bridgesInUse.clear()
        val bridgesArray = bridges.split("\n")
        bridgesArray.forEach {
            Log.d("surprise", "saveCustomBridges 335:  here bridge $it")
            var line = it
            if (line.isNotEmpty()) {
                if (line.contains(BridgeType.snowflake.toString())) {
                    line = line.replace("utls-imitate.+?( |\\z)".toRegex(), "")
                }
                bridgesInUse.add(line.replace("Bridge ", "").trim { it <= ' ' })
            }
        }
        Log.d("surprise", "saveCustomBridges 344:  $bridgesInUse")
        if (bridgesInUse.isNotEmpty()) {
            val testBridge = bridgesInUse.toString()
            if (testBridge.contains(BridgeType.obfs4.toString())) {
                currentBridgesType = BridgeType.obfs4
            } else if (testBridge.contains(BridgeType.obfs3.toString())) {
                currentBridgesType = BridgeType.obfs3
            } else if (testBridge.contains(BridgeType.scramblesuit.toString())) {
                currentBridgesType = BridgeType.scramblesuit
            } else if (testBridge.contains(BridgeType.meek_lite.toString())) {
                currentBridgesType = BridgeType.meek_lite
            } else if (testBridge.contains(BridgeType.snowflake.toString())) {
                currentBridgesType = BridgeType.snowflake
            } else {
                currentBridgesType = BridgeType.vanilla
            }
            saveConfigFile(context)
        } else {
            currentBridgesType = BridgeType.undefined
        }
    }

    companion object {
        var TOR_BROWSER_USER_AGENT =
            "Mozilla/5.0 (Windows NT 6.1; rv:60.0) Gecko/20100101 Firefox/60.0"
    }
}