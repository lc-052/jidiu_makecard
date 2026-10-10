# 第三方依赖与参考

构建版本以 Version Catalog 为准。采用 Apache 2.0 / MIT 等兼容许可；本文件列出运行依赖与主要测试依赖。完整许可文本随应用打包到 assets/licenses。

| 组件 | 使用 | 许可 / 官方来源 |
| --- | --- | --- |
| Kotlin 2.2.20、Coroutines 1.10.2、Serialization 1.9.0 | 语言、异步、JSON | Apache 2.0；[JetBrains Kotlin](https://github.com/JetBrains/kotlin) |
| AndroidX Compose BOM 2026.02.01、Material 3、Activity、Navigation、Lifecycle | 原生 UI | Apache 2.0；[AndroidX](https://android.googlesource.com/platform/frameworks/support/) |
| Room 2.8.5、DataStore 1.2.0、WorkManager 2.11.2 | 本地库、偏好、可恢复任务 | Apache 2.0；AndroidX |
| OkHttp / Okio | HTTPS / SSE | Apache 2.0；[Square OkHttp](https://github.com/square/okhttp) |
| PDFBox-Android 2.0.27.0 | PDF 文本解析 | Apache 2.0；[Tom Roush PDFBox-Android](https://github.com/TomRoush/PdfBox-Android) |
| PDFBox 及 FontBox 来源 | PDF 解析参考 | Apache 2.0；[Apache PDFBox](https://pdfbox.apache.org/) |
| Bouncy Castle（PDFBox 传递依赖） | PDF 加密支持 | MIT 风格许可；[Bouncy Castle](https://www.bouncycastle.org/about/license/) |
| py-fsrs 6.3.0 算法参考 | Kotlin 移植与 840 组对照 | MIT；[Open Spaced Repetition](https://github.com/open-spaced-repetition/py-fsrs)，全文在 licenses/FSRS-MIT.txt |
| genanki 格式参考、SQL | APKG v11 SQLite 与模板 | MIT；[genanki](https://github.com/kerrickstaley/genanki)，全文在 licenses/genanki-MIT.txt |
| JUnit 4.13.2 | 测试 | EPL 1.0；[JUnit4](https://github.com/junit-team/junit4)；不进入 APK |
| Robolectric 4.16 | JVM Android 测试 | MIT；[Robolectric](https://github.com/robolectric/robolectric)；不进入 APK |
| MockWebServer / okhttp-tls | 模拟 HTTP/HTTPS 测试 | Apache 2.0；不进入正式 APK |
| ktfmt 0.54 | 开发格式化工具 | Apache 2.0；[Meta ktfmt](https://github.com/facebook/ktfmt)；不进入 APK |

Anki 官方 Python 后端与 AnkiDroid 客户端仅用于本机兼容性测试，不链接、不打包进本应用。下载的参考运行时及第三方测试 APK 排除 Git。SQLite 通过 Android 平台提供。
