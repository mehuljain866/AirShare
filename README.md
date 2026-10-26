# AirShare — Wireless Photo & Document Transfer via Print

> ### 📲 [Download Latest AirShare APK](https://github.com/mehuljain866/AirShare/releases/latest)
> Tap the link above, then tap **AirShare.apk** to download and install directly on your Android phone.

---

**AirShare** turns an Android device into a virtual AirPrint / IPP Everywhere receiver so you can wirelessly transfer photos and documents from any device — iPhone, iPad, Mac, Windows PC, or Android — directly to your device using the standard print menu. No accounts, no cables, no cloud.

## Features

- 📸 **Photos arrive as JPEG images** directly in `Pictures/AirShare` with full **EXIF metadata preserved**
- 📄 **Documents saved as PDF** in `Documents/AirShare`
- 🔍 **Embedded photo extraction**: iOS/macOS AirPrint photos sent as single-image PDFs have their raw JPEG streams extracted directly without re-encoding
- 🌐 Works over **local Wi-Fi only**, fully offline — no internet required
- ⚡ **Driverless**: Native discovery via Bonjour / mDNS (`_ipp._tcp`, `_universal`)

## How it works

AirShare advertises itself on the local network as an IPP Everywhere / AirPrint device. When you share via Print:

1. The sending device sends the file over standard IPP
2. AirShare accepts the incoming format (PDF, JPEG, PNG, Apple URF raster, or PWG raster)
3. Photos and single-page AirPrint raster jobs are converted to high-quality JPEG photos with complete EXIF tags
4. The photos are registered in Android MediaStore (`Pictures/AirShare`) so they immediately show up in your Gallery and Google Photos

### Sending to AirShare

| Device | Steps |
|---|---|
| **iPhone / iPad** | Share → Print → Select **AirShare** |
| **macOS** | Print dialog or System Settings → Printers → **AirShare** |
| **Windows 10/11** | Print → Add Printer → Select **AirShare** |
| **Android** | Share / Print → All printers → **AirShare** |

## Modes & Configuration

| Mode | Formats Accepted | AirPrint Discovery |
|---|---|---|
| **AirPrint & Photos** (Default) | PDF, JPEG, PNG, Apple URF, PWG Raster | Visible to iOS, iPadOS, macOS, Windows |
| **PDF only** | PDF documents only | Hidden from iOS AirPrint |

- **Save photos as JPEG**: When enabled (default), photos sent via AirPrint are saved as JPEG in `Pictures/AirShare`.
- **Extract photos from PDF**: When enabled (default), single-image PDFs have the original JPEG photo extracted.

## Building the APK locally

```sh
cd android
./gradlew assembleDebug     # Output in app/build/outputs/apk/debug/app-debug.apk
```
