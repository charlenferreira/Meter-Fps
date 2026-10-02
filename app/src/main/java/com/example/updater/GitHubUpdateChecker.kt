package com.example.updater

import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

sealed class UpdateState {
    object Idle : UpdateState()
    object Checking : UpdateState()
    data class UpdateAvailable(
        val tagName: String,
        val releaseName: String,
        val releaseNotes: String,
        val releaseHtmlUrl: String,
        val apkDownloadUrl: String?
    ) : UpdateState()
    data class UpToDate(val version: String) : UpdateState()
    data class Error(val message: String) : UpdateState()
}

object GitHubUpdateChecker {
    const val GITHUB_REPO_URL = "https://github.com/charlenferreira/Meter-Fps"
    const val RELEASES_API_URL = "https://api.github.com/repos/charlenferreira/Meter-Fps/releases/latest"

    private val _updateState = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val updateState: StateFlow<UpdateState> = _updateState.asStateFlow()

    suspend fun checkForUpdates() {
        _updateState.value = UpdateState.Checking

        withContext(Dispatchers.IO) {
            var connection: HttpURLConnection? = null
            try {
                val url = URL(RELEASES_API_URL)
                connection = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 10000
                    readTimeout = 10000
                    setRequestProperty("Accept", "application/vnd.github.v3+json")
                    setRequestProperty("User-Agent", "MeterFPS-Android")
                }

                val responseCode = connection.responseCode
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    val reader = BufferedReader(InputStreamReader(connection.inputStream))
                    val response = reader.readText()
                    reader.close()

                    val json = JSONObject(response)
                    val tagName = json.optString("tag_name", "").trim()
                    val releaseName = json.optString("name", tagName)
                    val releaseNotes = json.optString("body", "Melhorias de desempenho e correções.")
                    val releaseHtmlUrl = json.optString("html_url", GITHUB_REPO_URL)

                    // Find direct APK download url from assets if available
                    var apkUrl: String? = null
                    val assets = json.optJSONArray("assets")
                    if (assets != null) {
                        for (i in 0 until assets.length()) {
                            val asset = assets.getJSONObject(i)
                            val assetName = asset.optString("name", "")
                            if (assetName.endsWith(".apk", ignoreCase = true)) {
                                apkUrl = asset.optString("browser_download_url", null)
                                break
                            }
                        }
                    }

                    val currentVersion = BuildConfig.VERSION_NAME
                    if (isNewerVersion(currentVersion, tagName)) {
                        _updateState.value = UpdateState.UpdateAvailable(
                            tagName = tagName,
                            releaseName = releaseName,
                            releaseNotes = releaseNotes,
                            releaseHtmlUrl = releaseHtmlUrl,
                            apkDownloadUrl = apkUrl ?: releaseHtmlUrl
                        )
                    } else {
                        _updateState.value = UpdateState.UpToDate(version = currentVersion)
                    }
                } else if (responseCode == HttpURLConnection.HTTP_NOT_FOUND) {
                    // No releases published yet on the repository
                    _updateState.value = UpdateState.UpToDate(version = BuildConfig.VERSION_NAME)
                } else {
                    _updateState.value = UpdateState.Error("Servidor GitHub retornou código $responseCode")
                }
            } catch (e: Exception) {
                _updateState.value = UpdateState.Error(
                    e.localizedMessage ?: "Não foi possível verificar atualizações no momento."
                )
            } finally {
                connection?.disconnect()
            }
        }
    }

    /**
     * Compares semver string (e.g. "1.0.0" vs "v1.0.1")
     */
    private fun isNewerVersion(current: String, latest: String): Boolean {
        if (latest.isBlank()) return false
        val cleanCurrent = current.removePrefix("v").removePrefix("V").trim()
        val cleanLatest = latest.removePrefix("v").removePrefix("V").trim()

        if (cleanCurrent == cleanLatest) return false

        val currentParts = cleanCurrent.split(".").mapNotNull { it.toIntOrNull() }
        val latestParts = cleanLatest.split(".").mapNotNull { it.toIntOrNull() }

        val length = maxOf(currentParts.size, latestParts.size)
        for (i in 0 until length) {
            val currPart = currentParts.getOrElse(i) { 0 }
            val latePart = latestParts.getOrElse(i) { 0 }
            if (latePart > currPart) return true
            if (latePart < currPart) return false
        }

        return false
    }
}
