package com.cellclaw.upload

import android.content.Context
import androidx.core.content.FileProvider
import java.io.File

interface ResumeUriProvider {
    fun getSharableUri(file: File): String
}

/**
 * Pure JVM fake URI provider for testing.
 */
class FakeResumeUriProvider(
    private val baseUri: String = "content://com.cellclaw.fileprovider/resumes"
) : ResumeUriProvider {
    override fun getSharableUri(file: File): String {
        return "$baseUri/${file.name}"
    }
}

/**
 * Android FileProvider implementation for exposing scoped resume PDFs.
 */
class FileProviderResumeUriProvider(
    private val context: Context,
    private val authority: String = "${context.packageName}.fileprovider"
) : ResumeUriProvider {
    override fun getSharableUri(file: File): String {
        val uri = FileProvider.getUriForFile(context, authority, file)
        return uri.toString()
    }
}
