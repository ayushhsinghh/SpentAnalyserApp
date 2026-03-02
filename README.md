# 🏦 Spend Analyser (Powered by Gemma AI)

An intelligent, privacy-first Android application that automates your financial tracking. Spending Analyser securely reads incoming bank SMS messages and uses an on-device Large Language Model (Google Gemma) to extract transactional data without ever sending your sensitive financial information to the cloud.

## ✨ Key Features

- **100% Offline AI Parsing**: Uses a locally downloaded Gemma model (via MediaPipe/TensorFlow Lite) to read transaction SMS messages. Your data never leaves your device.
- **Advanced Idempotent Sync**: Built with Room database indexing (`uniqueHash`) to prevent processing duplicate SMS messages, even on system reboots or manual syncs.
- **Native SQLite Optimization**: Leverages native Android `ContentResolver` queries (`selection` and `selectionArgs`) to efficiently filter high volumes of SMS messages by bank sender IDs and keywords without heavy Kotlin in-memory processing.
- **WorkManager Ingestion**: Seamlessly processes messages in the background. The extraction worker intelligently obeys system constraints (Ram Usage, device idle, charging) when parsing intensive AI workloads.
- **Feature-Rich Dashboard**:
  - **Cash Flow Health Bar**: Instantly visualize the split between credits vs debits.
  - **Vico Line Charts**: Dynamic monthly spending trends.
  - **Deep Insights**: Daily Burn Rate calculators and Top Destination visit counters.
  - **Month/Year Filter**: Query the native Room DB dynamically by bounded timestamps.
- **Broadcast Receiver Triggers**: Instantly detect incoming Bank SMS messages and enqueue targeted background WorkManager extractions for real-time tracking.

## 🛠 Tech Stack

- **UI**: Jetpack Compose (Material 3)
- **Architecture**: MVVM with Kotlin Coroutines & Flow
- **Local Data**: Room Database, Jetpack DataStore (`PreferencesManager`)
- **Background Processing**: WorkManager, BroadcastReceivers
- **AI Processing**: Google MediaPipe LLM Inference (Gemma 1B/3B)
- **Charts**: Vico Compose Charts
- **Logging**: Timber

## 🚀 Getting Started

1. **Clone the Repository**
2. **Setup AI Model**: Ensure there is enough space to download the Gemma `-cpu` `.task` model (usually ~1.5GB to ~2.5GB). The app will handle the download mechanism automatically to `/data/local/tmp/llm/` with a temporary file safeguard during network dropouts.
3. **Permissions**: The app requests `RECEIVE_SMS` and `READ_SMS` at runtime to begin monitoring your transactions.


## 📜 License

MIT License. See `LICENSE` for more information.
