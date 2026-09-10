package app.still.update

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

data class GitHubAsset(
    val name: String,
    val browser_download_url: String,
)

data class GitHubRelease(
    val tag_name: String,
    val prerelease: Boolean,
    val name: String?,
    val body: String?,
    val assets: List<GitHubAsset>,
)

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data class UpdateAvailable(
        val version: String,
        val notes: String,
        val downloadUrl: String,
    ) : UpdateState
    data class Downloading(val progress: Float) : UpdateState
    data class Completed(val apkFile: File) : UpdateState
    data class Error(val message: String) : UpdateState
}

object SemanticVersion {
    fun parse(version: String): Triple<Int, Int, Int> {
        val clean = version.trimStart('v', 'V').substringBefore('-').substringBefore('+')
        val parts = clean.split('.')
        return Triple(
            parts.getOrNull(0)?.toIntOrNull() ?: 0,
            parts.getOrNull(1)?.toIntOrNull() ?: 0,
            parts.getOrNull(2)?.toIntOrNull() ?: 0,
        )
    }

    fun isNewer(remote: String, local: String): Boolean {
        val (remoteMajor, remoteMinor, remotePatch) = parse(remote)
        val (localMajor, localMinor, localPatch) = parse(local)
        if (remoteMajor != localMajor) return remoteMajor > localMajor
        if (remoteMinor != localMinor) return remoteMinor > localMinor
        return remotePatch > localPatch
    }
}

object VersionUpdater {
    private const val ReleasesUrl = "https://api.github.com/repos/matepazy/still-app/releases"

    private val client = OkHttpClient()
    private val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
    private val releasesAdapter = moshi.adapter<List<GitHubRelease>>(
        Types.newParameterizedType(List::class.java, GitHubRelease::class.java),
    )

    fun checkForUpdates(
        currentVersion: String,
        includePrereleases: Boolean,
        onResult: (UpdateState) -> Unit,
    ) {
        val request = Request.Builder()
            .url(ReleasesUrl)
            .header("Accept", "application/vnd.github.v3+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                onResult(UpdateState.Error("Network error: ${e.localizedMessage}"))
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (!it.isSuccessful) {
                        onResult(UpdateState.Error("GitHub API error: ${it.code}"))
                        return
                    }

                    try {
                        val releases = releasesAdapter.fromJson(it.body?.string().orEmpty()).orEmpty()
                        val targetRelease = releases.firstOrNull { release ->
                            includePrereleases || !release.prerelease
                        }
                        if (targetRelease == null) {
                            onResult(UpdateState.Idle)
                            return
                        }

                        val apkAsset = targetRelease.assets.firstOrNull { asset ->
                            asset.name.endsWith(".apk", ignoreCase = true)
                        }
                        if (!SemanticVersion.isNewer(targetRelease.tag_name, currentVersion)) {
                            onResult(UpdateState.Idle)
                        } else if (apkAsset == null) {
                            onResult(UpdateState.Error("Update found but no APK asset is available in the release."))
                        } else {
                            onResult(
                                UpdateState.UpdateAvailable(
                                    version = targetRelease.tag_name,
                                    notes = targetRelease.body ?: "No release notes provided.",
                                    downloadUrl = apkAsset.browser_download_url,
                                ),
                            )
                        }
                    } catch (error: Exception) {
                        onResult(UpdateState.Error("Failed to parse update: ${error.localizedMessage}"))
                    }
                }
            }
        })
    }

    fun downloadApk(
        context: Context,
        downloadUrl: String,
        onProgress: (Float) -> Unit,
        onCompleted: (File) -> Unit,
        onError: (String) -> Unit,
    ) {
        client.newCall(Request.Builder().url(downloadUrl).build()).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                onError("Download failed: ${e.localizedMessage}")
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (!it.isSuccessful) {
                        onError("Server error: ${it.code}")
                        return
                    }
                    val body = it.body
                    if (body == null) {
                        onError("Response body is empty")
                        return
                    }

                    try {
                        val apkFile = File(context.cacheDir, "still_update.apk")
                        if (apkFile.exists()) apkFile.delete()
                        val totalBytes = body.contentLength()
                        var bytesRead = 0L
                        val buffer = ByteArray(8_192)
                        body.byteStream().use { input ->
                            FileOutputStream(apkFile).use { output ->
                                var read = input.read(buffer)
                                while (read != -1) {
                                    output.write(buffer, 0, read)
                                    bytesRead += read
                                    if (totalBytes > 0) onProgress(bytesRead.toFloat() / totalBytes)
                                    read = input.read(buffer)
                                }
                            }
                        }
                        onCompleted(apkFile)
                    } catch (error: Exception) {
                        onError("Failed to save update file: ${error.localizedMessage}")
                    }
                }
            }
        })
    }
}

object ApkInstaller {
    fun canInstallPackages(context: Context): Boolean = context.packageManager.canRequestPackageInstalls()

    fun requestInstallPermission(context: Context) {
        context.startActivity(
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                data = "package:${context.packageName}".toUri()
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            },
        )
    }

    fun installApk(context: Context, apkFile: File) {
        val apkUri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apkFile)
        context.startActivity(
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            },
        )
    }
}
