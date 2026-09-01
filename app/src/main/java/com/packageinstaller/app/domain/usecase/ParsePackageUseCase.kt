package com.packageinstaller.app.domain.usecase

import android.net.Uri
import com.packageinstaller.app.domain.model.PackageMetadata
import com.packageinstaller.app.domain.repository.PackageParser
import java.io.File

class ParsePackageUseCase(
    private val parser: PackageParser
) {
    suspend fun fromFile(file: File): PackageMetadata? {
        return parser.parseFile(file)
    }

    suspend fun fromFiles(files: List<File>): PackageMetadata? {
        return parser.parseFiles(files)
    }

    suspend fun fromUri(uri: Uri): PackageMetadata? {
        return parser.parseUri(uri)
    }

    suspend fun fromMultipleUris(uris: List<Uri>): PackageMetadata? {
        return parser.parseMultipleUris(uris)
    }

    suspend fun fromSplitDirectory(directory: File): PackageMetadata? {
        return parser.parseSplitDirectory(directory)
    }
}
