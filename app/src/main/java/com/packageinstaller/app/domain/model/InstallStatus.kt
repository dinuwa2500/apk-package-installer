package com.packageinstaller.app.domain.model

import android.content.Intent

/**
 * Stages of the installation process
 */
enum class InstallStage(val title: String) {
    PREPARING("Preparing Installation"),
    VALIDATING("Validating Package & Security"),
    EXTRACTING("Extracting Package Contents"),
    DEPLOYING_OBB("Deploying Expansion (OBB) Files"),
    CREATING_SESSION("Creating Package Installer Session"),
    WRITING_SPLITS("Staging APK Splits"),
    COMMITTING_SESSION("Committing Installation Session"),
    WAITING_CONFIRMATION("Awaiting User Confirmation"),
    COMPLETED("Installation Succeeded"),
    FAILED("Installation Failed")
}

/**
 * Standard Android installation failure reasons mapped to clear error codes
 */
enum class InstallErrorCode(val codeString: String, val userMessage: String, val suggestedAction: String) {
    INSTALL_FAILED_INVALID_APK(
        "INSTALL_FAILED_INVALID_APK",
        "The package archive is damaged or does not contain a valid Android APK structure.",
        "Re-download the file and try again."
    ),
    INSTALL_FAILED_NO_MATCHING_ABIS(
        "INSTALL_FAILED_NO_MATCHING_ABIS",
        "This application does not contain native libraries matching your device's CPU architecture.",
        "Check for a 64-bit (arm64-v8a) or 32-bit (armeabi-v7a) build for your phone."
    ),
    INSTALL_FAILED_MISSING_SPLIT(
        "INSTALL_FAILED_MISSING_SPLIT",
        "A required split APK component is missing from the package bundle.",
        "Ensure all split APK parts are included in the bundle."
    ),
    INSTALL_FAILED_INSUFFICIENT_STORAGE(
        "INSTALL_FAILED_INSUFFICIENT_STORAGE",
        "There is not enough free storage space available on the device.",
        "Free up internal storage space and try again."
    ),
    INSTALL_FAILED_UNKNOWN_SOURCES_DISABLED(
        "INSTALL_FAILED_UNKNOWN_SOURCES_DISABLED",
        "Installation of packages from unknown sources is disabled for this app.",
        "Grant the 'Install unknown apps' permission in system settings."
    ),
    INSTALL_FAILED_OLDER_SDK(
        "INSTALL_FAILED_OLDER_SDK",
        "The package requires a newer version of Android than currently installed.",
        "Update your Android OS or find a compatible older version of the app."
    ),
    INSTALL_FAILED_CONFLICTING_PROVIDER(
        "INSTALL_FAILED_CONFLICTING_PROVIDER",
        "The package contains a Content Provider authority that conflicts with another installed app.",
        "Uninstall the conflicting application first."
    ),
    INSTALL_FAILED_VERSION_DOWNGRADE(
        "INSTALL_FAILED_VERSION_DOWNGRADE",
        "A newer version of this application is already installed on the device.",
        "Uninstall the current version before installing an older build."
    ),
    INSTALL_FAILED_SIGNATURE_MISMATCH(
        "INSTALL_FAILED_SIGNATURE_MISMATCH",
        "The package signature does not match the signature of the currently installed application.",
        "Uninstall the existing app first (ensure data is backed up)."
    ),
    INSTALL_FAILED_OBB_PERMISSION_DENIED(
        "INSTALL_FAILED_OBB_PERMISSION_DENIED",
        "Unable to write OBB expansion files to Android/obb due to storage restrictions.",
        "Grant All Files Access or select the OBB directory with Storage Access Framework."
    ),
    INSTALL_FAILED_USER_ACTION_REQUIRED(
        "INSTALL_FAILED_USER_ACTION_REQUIRED",
        "User confirmation or permission is required by the Android system.",
        "Tap the prompt to allow installation."
    ),
    INSTALL_FAILED_ABORTED(
        "INSTALL_FAILED_ABORTED",
        "The installation was cancelled or aborted.",
        "You can retry installation at any time."
    ),
    INSTALL_FAILED_UNKNOWN(
        "INSTALL_FAILED_UNKNOWN",
        "An unexpected error occurred during installation.",
        "Check system logs or verify file integrity."
    );

    companion object {
        fun fromStatusAndMessage(status: Int, message: String?): InstallErrorCode {
            val msg = message?.uppercase() ?: ""
            return when {
                msg.contains("INVALID_APK") || msg.contains("PARSE_FAILED") -> INSTALL_FAILED_INVALID_APK
                msg.contains("NO_MATCHING_ABIS") || msg.contains("CPU_ABI") -> INSTALL_FAILED_NO_MATCHING_ABIS
                msg.contains("MISSING_SPLIT") -> INSTALL_FAILED_MISSING_SPLIT
                msg.contains("INSUFFICIENT_STORAGE") || msg.contains("NO_SPACE") -> INSTALL_FAILED_INSUFFICIENT_STORAGE
                msg.contains("VERSION_DOWNGRADE") -> INSTALL_FAILED_VERSION_DOWNGRADE
                msg.contains("UPDATE_INCOMPATIBLE") || msg.contains("SIGNATURE_MISMATCH") -> INSTALL_FAILED_SIGNATURE_MISMATCH
                msg.contains("CONFLICTING_PROVIDER") -> INSTALL_FAILED_CONFLICTING_PROVIDER
                msg.contains("OLDER_SDK") || msg.contains("MIN_SDK") -> INSTALL_FAILED_OLDER_SDK
                msg.contains("PERMISSION_DENIED") -> INSTALL_FAILED_UNKNOWN_SOURCES_DISABLED
                msg.contains("ABORTED") -> INSTALL_FAILED_ABORTED
                else -> INSTALL_FAILED_UNKNOWN
            }
        }
    }
}

/**
 * Live Progress state of an ongoing installation
 */
data class InstallProgress(
    val stage: InstallStage = InstallStage.PREPARING,
    val progressFraction: Float = 0f, // 0.0 to 1.0
    val currentItemName: String = "",
    val itemsCompleted: Int = 0,
    val totalItems: Int = 0,
    val bytesProcessed: Long = 0L,
    val totalBytes: Long = 0L,
    val statusDetail: String = ""
)

/**
 * Result state when installation completes or fails
 */
sealed class InstallResult {
    data class Success(
        val packageName: String,
        val appName: String,
        val versionName: String,
        val isNewInstall: Boolean = true
    ) : InstallResult()

    data class UserActionRequired(
        val confirmationIntent: Intent,
        val sessionId: Int
    ) : InstallResult()

    data class Failure(
        val errorCode: InstallErrorCode,
        val technicalMessage: String = "",
        val packageName: String = "",
        val appName: String = ""
    ) : InstallResult()
}
