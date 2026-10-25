package app.gamenative.utils

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import app.gamenative.BuildConfig
import app.gamenative.service.SteamService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File

object UpdateInstaller {
    suspend fun downloadAndInstall(
        context: Context,
        downloadUrl: String,
        versionName: String,
        onProgress: (Float) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            if (!versionName.matches(Regex("[A-Za-z0-9._-]+"))) {
                Timber.e("Rejecting update with invalid version name")
                return@withContext false
            }
            val expectedFileName = "gamenative-v$versionName.apk"
            val uri = Uri.parse(downloadUrl)
            val fileName = uri.lastPathSegment
            if (
                uri.scheme != "https" ||
                uri.host !in setOf(
                    "downloads.gamenative.app",
                    "pub-9fcd5294bd0d4b85a9d73615bf98f3b5.r2.dev",
                ) ||
                uri.userInfo != null ||
                uri.port !in listOf(-1, 443) ||
                fileName == null ||
                fileName != expectedFileName ||
                !fileName.matches(Regex("[A-Za-z0-9._-]+"))
            ) {
                Timber.e("Rejecting update URL outside the expected download source")
                return@withContext false
            }

            val apkFileName = "gamenative-v$versionName.apk"
            val destFile = File(context.cacheDir, apkFileName)

            Timber.i("Downloading update: $fileName")
            Timber.i("Saving to: ${destFile.absolutePath}")

            // Use the existing fetchFileWithFallback method which handles fallback URLs
            SteamService.fetchFileWithFallback(
                fileName = fileName,
                dest = destFile,
                context = context,
                onProgress = onProgress
            )

            // Verify the file exists and has content
            if (!destFile.exists()) {
                Timber.e("Downloaded file does not exist: ${destFile.absolutePath}")
                return@withContext false
            }

            val fileSize = destFile.length()
            if (fileSize == 0L) {
                Timber.e("Downloaded file is empty: ${destFile.absolutePath}")
                return@withContext false
            }

            if (!matchesInstalledSigningIdentity(context, destFile)) {
                destFile.delete()
                Timber.e("Downloaded APK package or signing identity does not match this installation")
                return@withContext false
            }

            Timber.i("Download complete: ${destFile.absolutePath}, size: $fileSize bytes")

            // Install the APK
            val installStarted = withContext(Dispatchers.Main) {
                installApk(context, destFile)
            }

            return@withContext installStarted
        } catch (e: Exception) {
            Timber.e(e, "Error downloading/installing update")
            return@withContext false
        }
    }

    private fun matchesInstalledSigningIdentity(context: Context, apkFile: File): Boolean {
        val packageManager = context.packageManager
        val installed = packageInfo(packageManager, context.packageName, 0) ?: return false
        val downloaded = packageManager.getPackageArchiveInfo(
            apkFile.absolutePath,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                PackageManager.GET_SIGNING_CERTIFICATES
            } else {
                @Suppress("DEPRECATION")
                PackageManager.GET_SIGNATURES
            },
        ) ?: return false
        if (downloaded.packageName != context.packageName) return false

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val installedSigningInfo = installed.signingInfo ?: return false
            val downloadedSigningInfo = downloaded.signingInfo ?: return false
            if (installedSigningInfo.hasMultipleSigners() || downloadedSigningInfo.hasMultipleSigners()) {
                return installedSigningInfo.hasMultipleSigners() &&
                    downloadedSigningInfo.hasMultipleSigners() &&
                    signingCertificates(installed).sameCertificates(signingCertificates(downloaded))
            }

            val installedCurrentSigner = installedSigningInfo.apkContentsSigners.singleOrNull()?.toByteArray()
                ?: return false
            return downloadedSigningInfo.signingCertificateHistory
                ?.any { it.toByteArray().contentEquals(installedCurrentSigner) } == true
        }
        return signingCertificates(installed).sameCertificates(signingCertificates(downloaded))
    }

    private fun packageInfo(packageManager: PackageManager, packageName: String, flags: Int): PackageInfo? =
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getPackageInfo(
                    packageName,
                    PackageManager.PackageInfoFlags.of(flags.toLong()),
                )
            } else {
                @Suppress("DEPRECATION")
                packageManager.getPackageInfo(packageName, flags)
            }
        } catch (_: PackageManager.NameNotFoundException) {
            null
        }

    private fun signingCertificates(packageInfo: PackageInfo): List<ByteArray> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo.signingInfo?.let { signingInfo ->
                val signatures = if (signingInfo.hasMultipleSigners()) {
                    signingInfo.apkContentsSigners
                } else {
                    signingInfo.signingCertificateHistory
                }
                signatures?.map { it.toByteArray() }.orEmpty()
            }.orEmpty()
        } else {
            @Suppress("DEPRECATION")
            packageInfo.signatures?.map { it.toByteArray() }.orEmpty()
        }

    private fun List<ByteArray>.sameCertificates(other: List<ByteArray>): Boolean =
        size == other.size && all { certificate ->
            other.any { otherCertificate -> certificate.contentEquals(otherCertificate) }
        }

    private fun installApk(context: Context, apkFile: File): Boolean {
        try {
            // Verify file exists before attempting installation
            if (!apkFile.exists()) {
                Timber.e("APK file does not exist: ${apkFile.absolutePath}")
                return false
            }

            Timber.i("Installing APK from: ${apkFile.absolutePath}, size: ${apkFile.length()} bytes")

            val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                // Use FileProvider for Android 7.0+
                try {
                    FileProvider.getUriForFile(
                        context,
                        "${BuildConfig.APPLICATION_ID}.fileprovider",
                        apkFile
                    )
                } catch (e: Exception) {
                    Timber.e(e, "Error getting FileProvider URI")
                    return false
                }
            } else {
                // Use file:// URI for older versions
                Uri.fromFile(apkFile)
            }

            Timber.i("FileProvider URI: $uri")
            Timber.i("File absolute path: ${apkFile.absolutePath}")
            Timber.i("File exists: ${apkFile.exists()}, size: ${apkFile.length()}")

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            // Grant URI permissions to the package installer
            val resInfoList = context.packageManager.queryIntentActivities(intent, 0)
            for (resolveInfo in resInfoList) {
                val packageName = resolveInfo.activityInfo.packageName
                context.grantUriPermission(
                    packageName,
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }

            context.startActivity(intent)
            Timber.i("Install intent launched successfully")
            return true
        } catch (e: Exception) {
            Timber.e(e, "Error launching install intent")
            return false
        }
    }
}
