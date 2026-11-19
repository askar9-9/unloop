package app.still.update

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

data class Release(val version: String, val apkUrl: String, val sumsUrl: String?, val apkName: String)

/** Updates come from GitHub Releases of the public repository; the APK must carry the same signing key to install. */
object Updater {
    private const val LATEST = "https://api.github.com/repos/askar9-9/unloop/releases/latest"

    fun currentVersion(context: Context): String =
        context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()

    suspend fun latest(): Release = withContext(Dispatchers.IO) {
        val json = JSONObject(get(LATEST).bufferedReader().use { it.readText() })
        val assets = json.getJSONArray("assets")
        var apk: Pair<String, String>? = null
        var sums: String? = null
        for (i in 0 until assets.length()) {
            val asset = assets.getJSONObject(i)
            val name = asset.getString("name")
            val url = asset.getString("browser_download_url")
            if (name.endsWith(".apk")) apk = name to url
            if (name.startsWith("SHA256SUMS")) sums = url
        }
        val (apkName, apkUrl) = apk ?: throw IOException("В релизе нет APK")
        Release(json.getString("tag_name").removePrefix("v"), apkUrl, sums, apkName)
    }

    fun isNewer(remote: String, current: String): Boolean {
        fun parts(v: String) = v.split('.', '-').map { it.toIntOrNull() ?: 0 }
        val a = parts(remote)
        val b = parts(current)
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    suspend fun download(context: Context, release: Release, progress: (Float) -> Unit): File = withContext(Dispatchers.IO) {
        val file = File(context.cacheDir, "update.apk")
        val connection = open(release.apkUrl)
        val length = connection.contentLengthLong
        val digest = MessageDigest.getInstance("SHA-256")
        connection.inputStream.use { input ->
            file.outputStream().use { output ->
                val buffer = ByteArray(64 * 1024)
                var done = 0L
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    output.write(buffer, 0, read)
                    digest.update(buffer, 0, read)
                    done += read
                    if (length > 0) progress(done.toFloat() / length)
                }
            }
        }
        release.sumsUrl?.let { url ->
            val expected = get(url).bufferedReader().use { it.readText() }.lines()
                .firstOrNull { it.trim().endsWith(release.apkName) }?.substringBefore(' ')?.lowercase()
            val actual = digest.digest().joinToString("") { "%02x".format(it) }
            if (expected != null && expected != actual) { file.delete(); throw IOException("Контрольная сумма APK не совпала") }
        }
        file
    }

    /** Hands the APK to the system installer; Android asks the user to confirm and checks the signature. */
    fun install(context: Context, apk: File) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            // Lets later updates skip the prompt once Unloop itself is the installer of record.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        }
        val id = installer.createSession(params)
        installer.openSession(id).use { session ->
            apk.inputStream().use { input ->
                session.openWrite("unloop.apk", 0, apk.length()).use { output ->
                    input.copyTo(output)
                    session.fsync(output)
                }
            }
            val status = PendingIntent.getBroadcast(
                context, id, Intent(context, InstallReceiver::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
            )
            session.commit(status.intentSender)
        }
    }

    private fun open(url: String): HttpURLConnection = (URL(url).openConnection() as HttpURLConnection).apply {
        connectTimeout = 15_000
        readTimeout = 30_000
        setRequestProperty("Accept", "application/vnd.github+json")
        setRequestProperty("User-Agent", "Unloop-Android")
        if (responseCode !in 200..299) throw IOException("GitHub ответил $responseCode")
    }

    private fun get(url: String) = open(url).inputStream
}
