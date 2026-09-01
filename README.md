# Package Installer 📦

A modern, high-performance Android application built with **Kotlin**, **Jetpack Compose**, and **Material 3** for installing **APK**, **XAPK**, **APKS**, and **Split APK** bundles from local storage.

---

## ✨ Features

- **Multi-Format Package Support**:
  - 📦 **Standard APK**: Official package installation with `PackageInstaller.Session` and `FileProvider`.
  - 🗂️ **XAPK Bundles**: Automated extraction and validation of APK splits with direct / guided placement of `Android/obb/<package-name>/` expansion files.
  - 🧩 **APKS Archives**: Bundletool archives with automatic selection and filtering of matching device CPU ABIs, screen densities, and active locales.
  - 📑 **Split APK Sets**: Atomic installation of base APK + config splits together in a single `PackageInstaller.Session`.
- **Recursive Storage & Hidden Directory Scanner**:
  - Recursively scans accessible storage directories.
  - **Explicit Hidden Directory Traversal**: Dot-prefixed folders (e.g., `.backup`, `.downloads`, `.hidden`, `.apps`, `.myfiles`) are deeply traversed and included by the package scanner.
  - **Show Hidden Directories Toggle**: In the File Browser UI, users can switch visibility of dot-folders on/off.
- **Deep Package Inspector**:
  - App icon, application name, package identifier, version code, and version name.
  - Device ABI compatibility indicators and architecture lists (`arm64-v8a`, `armeabi-v7a`, `x86_64`).
  - Min SDK & Target SDK validation against current device OS.
  - Split components and OBB files breakdown.
  - Requested app permissions with danger indicators.
  - Certificate & Signature fingerprints (SHA-256).
- **Interactive Live Installation Progress**:
  - Real-time animated progress and percentage tracking.
  - State indicators for extracting, deploying OBB, creating session, staging splits, and awaiting system confirmation.
- **Intelligent Error Resolver**:
  - Detailed error cards and human-friendly diagnostic solutions for errors such as:
    - `INSTALL_FAILED_INVALID_APK`
    - `INSTALL_FAILED_NO_MATCHING_ABIS`
    - `INSTALL_FAILED_MISSING_SPLIT`
    - `INSTALL_FAILED_INSUFFICIENT_STORAGE`
    - `INSTALL_FAILED_UNKNOWN_SOURCES_DISABLED`
    - `INSTALL_FAILED_VERSION_DOWNGRADE`
    - `INSTALL_FAILED_SIGNATURE_MISMATCH`
    - `INSTALL_FAILED_OBB_PERMISSION_DENIED`
- **Security & Scoped Storage Compliance**:
  - Non-root, strictly adhering to Android security standards.
  - No execution of untrusted native binaries.
  - Scoped storage compliance across Android 10, 11, 12, 13, and 14+.

---

## 🏗️ Architecture

Clean Architecture principles with MVVM and unidirectional data flow:

```text
com.packageinstaller.app/
├── data/
│   ├── installer/
│   │   ├── PackageInstallerManagerImpl.kt
│   │   └── PackageInstallStatusReceiver.kt
│   ├── parser/
│   │   ├── ApkParser.kt
│   │   ├── XapkParser.kt
│   │   ├── ApksParser.kt
│   │   ├── SplitSetParser.kt
│   │   └── CompositePackageParser.kt
│   ├── scanner/
│   │   ├── StorageScannerImpl.kt
│   │   └── FileBrowserManagerImpl.kt
│   ├── obb/
│   │   └── ObbManagerImpl.kt
│   └── repository/
│       └── SettingsRepositoryImpl.kt
├── domain/
│   ├── model/
│   │   ├── PackageModel.kt
│   │   ├── InstallStatus.kt
│   │   └── StorageItem.kt
│   ├── repository/
│   │   └── PackageRepositories.kt
│   └── usecase/
│       ├── ScanStorageUseCase.kt
│       ├── ParsePackageUseCase.kt
│       ├── InstallPackageUseCase.kt
│       └── GetDeviceCompatUseCase.kt
├── ui/
│   ├── components/
│   │   ├── AppHeader.kt
│   │   ├── PackageCard.kt
│   │   ├── StatusBadge.kt
│   │   ├── FileListItem.kt
│   │   ├── BreadcrumbBar.kt
│   │   ├── PermissionWarningBanner.kt
│   │   └── EmptyState.kt
│   ├── screens/
│   │   ├── home/
│   │   ├── scanner/
│   │   ├── browser/
│   │   ├── inspector/
│   │   ├── install/
│   │   └── settings/
│   ├── navigation/
│   │   ├── Screen.kt
│   │   └── NavGraph.kt
│   └── theme/
│       ├── Color.kt
│       ├── Theme.kt
│       └── Type.kt
├── utils/
│   ├── DeviceUtils.kt
│   ├── FileUtils.kt
│   └── SecurityValidator.kt
├── MainActivity.kt
└── PackageInstallerApplication.kt
```

---

## 🚀 Building & Running

### Prerequisites
- Android Studio Iguana / Jellyfish or newer
- JDK 17
- Android SDK 34 (Android 14)

### Build Commands
```bash
# Run Unit Tests
./gradlew test

# Build Debug APK
./gradlew assembleDebug

# Build Release APK
./gradlew assembleRelease
```

---

## 🐙 GitHub Setup & Automated Releases

### 1. Initialize Git Repository & Push
```bash
git init
git add .
git commit -m "feat: initial release of Package Installer"
git branch -M main
git remote add origin https://github.com/<your-username>/package-installer.git
git push -u origin main
```

### 2. CI/CD Workflows (`.github/workflows/`)
- **CI Workflow (`ci.yml`)**: Runs unit tests and builds the debug APK on every push and pull request to `main`.
- **Release Workflow (`release.yml`)**: Automatically triggers when you push a version tag (e.g. `v1.0.0`):
  1. Validates test suite.
  2. Builds and signs the release APK using GitHub Secrets.
  3. Renames the artifact to `package-installer-v1.0.0.apk`.
  4. Generates a GitHub Release with changelog and attaches the APK.

### 3. Creating a Release Tag
```bash
git tag v1.0.0
git push origin v1.0.0
```

### 4. GitHub Secrets for Release Signing (Optional)
To sign release APKs in GitHub Actions, configure the following secrets in your repository settings (**Settings > Secrets and variables > Actions**):
- `ANDROID_KEYSTORE_BASE64`: Base64-encoded `.jks` or `.keystore` file (`base64 -w 0 release.jks`).
- `KEYSTORE_PASSWORD`: Keystore password.
- `KEY_ALIAS`: Signing key alias.
- `KEY_PASSWORD`: Key alias password.

---

## 📄 License
This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.
