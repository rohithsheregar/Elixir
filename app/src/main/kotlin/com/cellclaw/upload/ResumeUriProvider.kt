package com.cellclaw.upload

import android.content.Context
import androidx.core.content.FileProvider
import java.io.File

interface ResumeUriProvider {
    fun getSharableUri(file: File): String
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
