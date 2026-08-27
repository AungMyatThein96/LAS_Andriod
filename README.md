# Formation Evaluation — Android

This is an Android WebView wrapper around the supplied `Index.html` LAS Log Viewer / Formation Evaluation app.

## Build
- Android Gradle Plugin: 8.6.1
- Compile/target SDK: 35
- Minimum SDK: 23
- Java: 17

Run:

```bash
gradle assembleDebug
```

The APK is produced at:

`app/build/outputs/apk/debug/app-debug.apk`

A GitHub Actions workflow is included at `.github/workflows/build-apk.yml`. Push this project to GitHub, then run the workflow; the resulting APK is uploaded as a workflow artifact.

## Notes
The original upload contained only the HTML application and placeholder/empty Android build files, so the Android wrapper and build configuration were completed here. The file chooser is wired to Android's document picker so the HTML app can import local LAS files.
