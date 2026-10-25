package com.cellclaw.upload

import java.io.File

/**
 * Pure JVM fake URI provider for testing only.
 * Excluded from production builds.
 */
class FakeResumeUriProvider(
    private val baseUri: String = "content://com.cellclaw.fileprovider/resumes"
) : ResumeUriProvider {
    override fun getSharableUri(file: File): String {
        return "$baseUri/${file.name}"
    }
}
