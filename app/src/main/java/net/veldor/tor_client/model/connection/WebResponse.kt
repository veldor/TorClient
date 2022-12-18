package net.veldor.tor_client.model.connection

import java.io.InputStream

class WebResponse(
    val statusCode: Int,
    val inputStream: InputStream?,
    val contentType: String?,
    val headers: HashMap<String, ArrayList<String>>,
    val contentLength: Int = 0,
    val longContentLength: Long = 0
)