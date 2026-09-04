# Wellmeal Connector

## 日本語

Wellmeal Connector は、Android Health Connect から取得した健康データを JSON に変換し、ユーザー自身の OneDrive に保存する Android プロトタイプです。

目的は、ウェアラブルやヘルスアプリのデータを、Microsoft Copilot などの AI サービスから扱いやすい形で利用できるようにすることです。

Wellmeal 独自の中央サーバーに健康データを保存せず、基本的にデータはユーザーの端末と OneDrive 内に保持されます。

### データフロー

```text
Wearable / Health App
        ↓
Android Health Connect
        ↓
Wellmeal Connector
        ↓
JSON Generation
        ↓
Microsoft Graph
        ↓
User's OneDrive
        ↓
Copilot / AI Services
```

例:

```text
Galaxy Watch
→ Samsung Health
→ Health Connect
→ Wellmeal Connector
→ OneDrive
```

### 取得するデータ

現在のプロトタイプでは、前日のデータを端末のローカルタイムゾーン基準で集計します。

- Steps
- Exercise duration
- Average / Minimum / Maximum heart rate
- Sleep duration

取得できない値は `0` ではなく `null` として扱われます。

### Medical Profile

Medical Profile には以下の情報が含まれます。

- Allergies / intolerances
- Medications
- Dietary restrictions

アレルギーと服薬情報は Health Connect Personal Health Record から、食事制限は Wellmeal Connector 内の設定から取得されます。

正常に読み取れた結果が 0 件の場合は有効な空データとして扱います。

一方、Health Connect の権限不足や読み取りエラーの場合は、既存の `profile.json` を空データで上書きしません。

### OneDrive ファイル構成

```text
OneDrive/
└─ Apps/
   └─ Wellmeal Connector/
      ├─ latest.json
      ├─ profile.json
      └─ daily/
         ├─ 2026-09-01.json
         ├─ 2026-09-02.json
         └─ ...
```

`daily/YYYY-MM-DD.json`  
日ごとの健康データを保存します。

`latest.json`  
最新の日次データを保持します。同期のたびに上書きされます。

`profile.json`  
最新の Medical Profile を保持します。

Copilot などのサービスは、基本的に `latest.json` と `profile.json` を読むだけで、現在の健康状態とプロフィール情報を取得できます。

### Sync

Wellmeal Connector は Manual Sync と Automatic Sync をサポートしています。

Automatic Sync は Android WorkManager を利用し、1 日 1～3 回の実行時間を設定できます。

端末がスリープ状態の場合やネットワークが一時的に利用できない場合に備え、短時間の再試行と WorkManager の再試行を組み合わせています。

```text
Initial Attempt
→ 5 sec
→ Retry
→ 10 sec
→ Retry
→ 20 sec
→ Retry
→ WorkManager Retry
```

### Daily Health Email

オプションで Microsoft Graph `Mail.Send` を利用し、Daily Health Report をメールで送信できます。

レポートには Activity、Heart、Sleep、Medical Profile の概要が含まれます。

同じ日付のメールが重複送信されないように制御されています。

### Privacy

基本的な保存先は以下です。

```text
User Device
    ↓
User's Own OneDrive
```

Wellmeal 用の中央ヘルスデータサーバーは必要ありません。

Access token、health JSON、医療情報などの機密データは Logcat に出力しない設計になっています。

### Current Prototype

現在のプロトタイプでは以下を実装しています。

- Health Connect integration
- Activity / Heart / Sleep aggregation
- Medical Profile
- JSON export
- Microsoft authentication
- OneDrive App Folder upload
- Manual / Automatic sync
- Background sync retry
- Sync history
- Notifications
- Daily Health Email
- Copilot-ready `latest.json` / `profile.json`

今後は、長期トレンド分析、Apple Health 対応、Copilot Agent 連携の拡張などを検討しています.

---

## English

Wellmeal Connector is an Android prototype that collects health data from Android Health Connect, converts it into lightweight JSON files, and stores those files in the user's own OneDrive.

The goal is to make wearable and health-app data easily consumable by downstream services such as Microsoft Copilot without requiring a centralized Wellmeal health-data server.

### Data Flow

```text
Wearable / Health App
        ↓
Android Health Connect
        ↓
Wellmeal Connector
        ↓
JSON Generation
        ↓
Microsoft Graph
        ↓
User's OneDrive
        ↓
Copilot / AI Services
```

Example:

```text
Galaxy Watch
→ Samsung Health
→ Health Connect
→ Wellmeal Connector
→ OneDrive
```

### Health Data

The current prototype aggregates the previous day's data using the device's local timezone.

- Steps
- Exercise duration
- Average / Minimum / Maximum heart rate
- Sleep duration

Unavailable values remain `null` instead of being converted to zero.

### Medical Profile

The Medical Profile contains:

- Allergies / intolerances
- Medications
- Dietary restrictions

Allergies and medications come from Health Connect Personal Health Record, while dietary restrictions are managed locally by Wellmeal Connector.

A successful read with zero records is treated as a valid empty profile.

If Health Connect medical permissions are missing or a read fails, the existing OneDrive `profile.json` is preserved instead of being overwritten with incomplete empty data.

### OneDrive Structure

```text
OneDrive/
└─ Apps/
   └─ Wellmeal Connector/
      ├─ latest.json
      ├─ profile.json
      └─ daily/
         ├─ 2026-09-01.json
         ├─ 2026-09-02.json
         └─ ...
```

`daily/YYYY-MM-DD.json`  
Stores the health snapshot for a specific date.

`latest.json`  
Contains the latest daily health snapshot and is overwritten after a successful sync.

`profile.json`  
Contains the latest Medical Profile.

For a basic Copilot integration, downstream services only need:

```text
latest.json
profile.json
```

### Sync

Wellmeal Connector supports both Manual Sync and Automatic Sync.

Automatic Sync uses Android WorkManager and supports 1–3 scheduled sync times per day.

To handle temporary network or DNS failures while a device is waking from idle state, the app uses fast in-worker retries followed by WorkManager retry.

```text
Initial Attempt
→ 5 sec
→ Retry
→ 10 sec
→ Retry
→ 20 sec
→ Retry
→ WorkManager Retry
```

### Daily Health Email

An optional Daily Health Email feature uses Microsoft Graph `Mail.Send`.

The report can include:

- Activity
- Heart
- Sleep
- Medical Profile

Duplicate delivery for the same health-data date is prevented.

### Privacy

The primary storage model is:

```text
User Device
    ↓
User's Own OneDrive
```

No centralized Wellmeal health database is required.

Sensitive health values, access tokens, authorization headers, and profile contents are not written to diagnostic logs.

### Current Prototype

Implemented features include:

- Health Connect integration
- Activity / Heart / Sleep aggregation
- Medical Profile
- JSON export
- Microsoft authentication
- OneDrive App Folder storage
- Manual / Automatic sync
- Background retry handling
- Sync history
- Notifications
- Daily Health Email
- Copilot-ready `latest.json` and `profile.json`

Future work may include long-term trend analysis, Apple Health support, and expanded Copilot Agent integration.
