package com.mehuljain.airshare.printer

import java.io.File

/** Result of a successful conversion. */
data class ConvertedDocument(val file: File, val format: String)

/** Metadata associated with a transfer job for conversion and EXIF tagging. */
data class JobMetadata(
    val id: Int = 0,
    val name: String = "",
    val userName: String = "",
    val createdAt: Long = System.currentTimeMillis(),
)

/**
 * Post-receive document conversion hook used by the job store.
 * Called after a document has been fully stored. Return null when no conversion applies
 * (the original is kept); return a ConvertedDocument to replace the stored file (the store
 * deletes [source] afterwards). Throw on failure (the store keeps the original and logs).
 *
 * @param source stored document
 * @param format its MIME type
 * @param metadata metadata of the job (name, user, timestamp)
 * @param target the file to write the converted output to (does not exist yet; same dir as source)
 */
fun interface DocumentConverter {
    fun convert(
        source: File,
        format: String,
        metadata: JobMetadata,
        target: (extension: String) -> File,
    ): ConvertedDocument?

    fun convert(
        source: File,
        format: String,
        target: (extension: String) -> File,
    ): ConvertedDocument? = convert(source, format, JobMetadata(), target)
}
