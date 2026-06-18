<div align="center">

<img src="app/src/main/ic_launcher-playstore.png" width="128" height="128" style="border-radius: 50%;" />

# Archiv

Archiv is an Android app for managing & and scanning Documents files,
with fast search speed, tagging, importing and sharing directly.

</div>

---

## Download

[<img src="https://f-droid.org/badge/get-it-on.png" alt="Get it on F-Droid" height="80">](https://f-droid.org/packages/com.armanmaurya.archiv/)
[<img src="https://github.com/machiav3lli/oandbackupx/blob/034b226cea5c1b30eb4f6a6f313e4dadcbb0ece4/badge_github.png" alt="Get it on GitHub" height="80">](https://github.com/armanmaurya/Archiv/releases/latest/)

## Features

- **High-Quality Scanning**: Capture documents with precision and ML-powered corner detection.
- **Document Management**: Organize your scanned documents with a powerful tagging system and fast search.
- **PDF Generation**: Convert your scans into high-quality PDF files.
- **Privacy First**: All processing and storage happen locally on your device.
- **Multi-language Support**: Available in English and Hindi.
- **Modern UI**: Built with Jetpack Compose for a smooth and responsive experience.

## Screenshots

<div align="center">
  <img width="23%" src="fastlane/metadata/android/en-US/images/phoneScreenshots/1.png" alt="Settings Screen">
  <img width="23%" src="fastlane/metadata/android/en-US/images/phoneScreenshots/2.png" alt="Documents Screen">
  <img width="23%" src="fastlane/metadata/android/en-US/images/phoneScreenshots/3.png" alt="Scanner Screen">
  <img width="23%" src="fastlane/metadata/android/en-US/images/phoneScreenshots/4.png" alt="Editor Screen">
</div>

## Tech Stack

- **Language**: [Kotlin](https://kotlinlang.org/)
- **UI Framework**: [Jetpack Compose](https://developer.android.com/jetpack/compose)
- **Database**: [Room](https://developer.android.com/training/data-storage/room)
- **Camera**: [CameraX](https://developer.android.com/training/camerax)
- **Computer Vision**: [OpenCV for Android](https://opencv.org/android/)
- **Machine Learning**: [LiteRT (TensorFlow Lite)](https://ai.google.dev/edge/litert)
- **PDF Creation**: [PDFBox-Android](https://github.com/TomRoush/PdfBox-Android)

## Building From Source

To build Archiv from source, ensure you have the latest version of Android Studio installed.

1. **Clone the repository**:
   ```bash
   git clone https://github.com/armanmaurya/archiv.git
   ```
2. **Open the project** in Android Studio.
3. **Wait for Gradle sync** to complete.
4. **Run the app** on a physical device or emulator.

## Contributing

Contributions are what make the open-source community such an amazing place to learn, inspire, and create. Any contributions you make are greatly appreciated.

Please see [CONTRIBUTING.md](CONTRIBUTING.md) for details on our code of conduct and the process for submitting pull requests.

## Contributors

<a href="https://github.com/armanmaurya/Archiv/graphs/contributors">
  <img src="https://contrib.rocks/image?repo=armanmaurya/Archiv" alt="Archiv Contributors"/>
</a>

## Open Source Libraries

Archiv uses the following open-source libraries:

- [Android Jetpack Libraries](https://developer.android.com/jetpack) (Compose, Room, CameraX, Lifecycle, Navigation, etc.)
- [LiteRT](https://ai.google.dev/edge/litert) - A cross-platform runtime for on-device AI.

## Related Projects

- [DocumentCorNet](https://github.com/armanmaurya/DocumentCorNet) — the repository providing the corner-detection and enhancement model used by Archiv in its scanning pipeline.

## License

This project is licensed under the GNU GPL v3 License - see the [LICENSE](LICENSE) file for details.
