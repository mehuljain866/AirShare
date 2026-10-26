package com.mehuljain.airshare.printer

/** User-editable printer/receiver settings. */
data class PrinterConfig(
    val name: String,
    /** Non-privileged TCP port (never 631; the app runs without root). */
    val port: Int = DEFAULT_PORT,
    /** Stable UUID string (without "urn:uuid:" prefix). */
    val uuid: String,
    val location: String = "",
    val makeAndModel: String = "AirShare Photo & Document Receiver",
    /**
     * true (default): AirPrint / high-compatibility mode - accepts image/urf, image/pwg-raster,
     * image/jpeg, image/png, and application/pdf. Advertises AirPrint (URF, _universal subtype).
     * false: PDF-only mode - only application/pdf documents are accepted.
     */
    val compatibilityMode: Boolean = true,
    /**
     * When true (default), single-page AirPrint / raster jobs (e.g. photos) are saved directly
     * as JPEG images in Pictures/AirShare instead of being wrapped into a PDF.
     */
    val savePhotosAsImages: Boolean = true,
    /**
     * When true (default), single-image PDFs are inspected and the original embedded JPEG photo
     * is extracted directly to Pictures/AirShare, preserving original EXIF metadata.
     */
    val extractPdfPhotos: Boolean = true,
    /**
     * When false (default), incoming transfers prompt the user with an AirDrop-style UI card
     * showing sender name, file details, and Accept/Decline buttons.
     * When true, incoming transfers are accepted automatically without asking.
     */
    val autoAccept: Boolean = false,
) {
    companion object {
        const val DEFAULT_PORT = 8631
        const val RESOURCE_PATH = "/ipp/print"
    }
}
