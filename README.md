# Family Connect — KidsGuard-like Parental Control App

Parent device se child device ka real-time camera, location, notifications, aur SOS alerts monitor karein. WebRTC streaming + Firebase backend.

---

## Table of Contents

- [Features](#features)
- [How It Works (End to End)](#how-it-works-end-to-end)
- [Architecture](#architecture)
- [Firebase Setup](#firebase-setup)
- [GitHub & Release](#github--release)
- [Build Instructions](#build-instructions)
- [Pairing Flow](#pairing-flow)
- [Role-Specific Settings](#role-specific-settings)
- [Firebase RTDB Structure](#firebase-rtdb-structure)
- [Dependencies](#dependencies)
- [Troubleshooting](#troubleshooting)

---

## Features

| Feature | Description |
|---------|-------------|
| **QR Code Pairing** | Parent QR generate karein → child scan karein → pair ho jayein |
| **Live Camera Streaming** | Real-time WebRTC video stream from child to parent |
| **Audio-Only Mode** | Sirf audio stream (camera off) |
| **GPS Location Tracking** | Child device ka real-time location parent dashboard pe |
| **Battery & Network Status** | Child ki battery% + WiFi/Mobile status |
| **SOS Alerts** | Child panic button → parent ko instant red alert |
| **Notification Monitoring** | Child ke notifications parent ko forward |
| **App Usage Tracking** | Child ke apps ka usage track |
| **Stream Quality Control** | SD (480p) / HD (720p) select karein |
| **Auto-Timeout** | Stream automatically band ho jaaye X minutes baad |
| **Dark Theme** | Material 3 light/dark theme |
| **Deep Link Pairing** | `familyconnect://pair?code=XXX` se auto-pairing |
| **Role-Based UI** | Parent aur child ke liye alag-alag screens + settings |
| **Hindi Locale** | Hindi language support |

---

## How It Works (End to End)

### 1. Installation

```
Dono phones par same APK install karein:
https://github.com/AKASH991833/MonitorApp/releases/download/v2.0.0/FamilyConnect-v2.0.0.apk
```

Ya parent ka QR code scan karein Google Camera se → apne aap download link khulega.

### 2. First Launch — Role Selection

| User | Screen | Action |
|------|--------|--------|
| **Parent** | Role Selection → `"Parent"` | Name enter karein → **"Generate Code"** dabayein |
| **Child** | Role Selection → `"Child"` | Parent ka code enter karein → **"Pair"** dabayein |

### 3. Parent Flow (Full Detail)

```
Splash → Role Selection → Parent Pairing → Dashboard
```

**Step-by-step:**

1. **Splash Screen** (1.5s) → Firebase initialize hota hai
2. **Onboarding** → Swipe karein (first time)
3. **Role Selection** → "Parent" select karein
4. **Parent Pairing Screen**:
   - Apna naam daalein
   - "Generate Code" dabayein → 6-digit code banta hai (10 min expiry)
   - QR code generate hota hai (contains GitHub download URL + code)
   - "Copy Code" button → clipboard par copy
   - "Share App Link" button → WhatsApp/Telegram se link + code bheje
   - Timer dikhta hai (code kitne der mein expire hoga)
5. **Dashboard** (jab child pair kare tab):
   - Child ka card dikhta hai:
     - Naam, Online/Offline status
     - Battery% (red if ≤20%)
     - Network type (WiFi/Mobile/Offline)
     - GPS coordinates (lat, lng)
     - Last seen timestamp
   - **Live View** button → child ka camera stream karein
   - **History** → past sessions ka record
   - **Add Child (+)** → doosra child pair karein
   - **SOS Banner** (red background) → child ne SOS bheja to dikhta hai
   - **Settings** → gear icon top-right

### 4. Child Flow (Full Detail)

```
Splash → Role Selection → Child Pairing → Idle Screen
```

**Step-by-step:**

1. **Splash Screen** (1.5s)
2. **Role Selection** → "Child" select karein
3. **Child Pairing Screen**:
   - Apna naam daalein
   - Pairing code daalein (ya QR scan karein):
     - **"Scan QR Code"** → in-app camera khulta hai → parent ka QR scan karein → code auto-fill
     - Ya manual type karein (6 alphanumeric characters)
   - **"Pair"** dabayein → Firebase validate karta hai
   - Pair ho gaya → child local DB mein save hota hai + parent ko Firebase notify
4. **Idle Screen**:
   - **Status**: "Waiting for commands from parent..."
   - **SOS Button** (red, center) → dabate hi parent ko alert
   - **App info**: version + device status
   - Background services chalte hain:
     - `ChildStatusService` (GPS + battery + network → Firebase har 15-30s)
     - `NotificationMonitorService` (notifications forward)
   - Parent se command aati hai (e.g., `start_stream`) → WebRTC stream start

### 5. Live Stream Flow

```
Parent: "Live View" click
  ↓
Parent Dashboard VM → repository.sendWakeCommand(childId)
  ↓
Firebase commands/$childId → push { command: "start_stream", sessionId: "..." }
  ↓
ChildIdleViewModel → ValueEventListener → start_stream detected
  ↓
Child: WebRTC PeerConnection create → offer create
  ↓
Offer → Firebase signaling/$sessionId/offer
  ↓
Parent: ForegroundMonitorService listens for offer
  ↓
Parent: Answer create → Firebase signaling/$sessionId/answer
  ↓
ICE candidates exchange hota hai (both sides)
  ↓
Peer-to-peer connection established
  ↓
Parent: Child ka camera + audio stream dekhta/sunta hai
```

### 6. SOS Flow

```
Child: Idle Screen par SOS button dabayega
  ↓
ChildIdleViewModel → firebaseSource.getSosAlertsRef() → push alert
  ↓
Firebase: sos_alerts/$parentId → new child added
  ↓
ParentDashboardViewModel → ChildEventListener → alert detected
  ↓
Parent Dashboard: Red banner dikhta hai "SOS from [Child Name]"
  ↓
Parent: Dismiss kar sakta hai
```

---

## Architecture

```
MVVM (Model-View-ViewModel) + Jetpack Compose + Firebase
```

```
app/src/main/java/com/familyconnect/app/
├── MainActivity.kt              # Entry point, deep link handling, permissions
├── MainApplication.kt           # Application class
│
├── data/
│   ├── local/
│   │   ├── AppDatabase.kt       # Room database (paired children, sessions)
│   │   ├── PairedChildEntity.kt # Room entity for paired children
│   │   ├── SessionEntity.kt     # Room entity for session history
│   │   ├── PairingDao.kt        # DAO for children
│   │   └── SessionDao.kt        # DAO for sessions
│   │
│   ├── model/
│   │   ├── PairingCode.kt       # Pairing code data class (6-char generator)
│   │   ├── PairedChild.kt       # Child model
│   │   ├── SessionRecord.kt     # Session history model
│   │   └── UserRole.kt          # Enum: PARENT, CHILD
│   │
│   ├── remote/
│   │   └── FirebaseSource.kt    # All Firebase RTDB operations
│   │     - Pairing codes CRUD
│   │     - Device status read/write
│   │     - FCM commands
│   │     - Signaling (WebRTC offer/answer/ICE)
│   │     - SOS alerts
│   │     - Parent-child sync
│   │
│   └── repository/
│       ├── AppRepository.kt     # Business logic layer
│       └── PairingCodeManager.kt # Code generation + validation
│
├── service/
│   ├── ForegroundMonitorService.kt   # WebRTC stream receiver (parent side)
│   ├── ChildStatusService.kt         # GPS + battery sender (child side)
│   ├── FamilyConnectMessagingService.kt  # FCM handler
│   └── NotificationMonitorService.kt # Notification listener (child side)
│
├── ui/
│   ├── splash/SplashScreen.kt        # Splash + role-based routing
│   ├── onboarding/OnboardingScreen.kt # First-time intro
│   ├── roleselection/                # Parent/Child selection
│   ├── pairing/
│   │   ├── ParentPairingScreen.kt    # Code generation + QR + share
│   │   ├── ChildPairingScreen.kt     # Code entry + QR scan
│   │   ├── QrScannerScreen.kt       # ML Kit barcode scanner
│   │   └── PairingViewModel.kt       # Pairing logic
│   ├── parent/
│   │   ├── dashboard/
│   │   │   ├── ParentDashboardScreen.kt    # Child cards, SOS banner
│   │   │   └── ParentDashboardViewModel.kt # Listeners for status/SOS
│   │   ├── liveview/
│   │   │   ├── LiveViewScreen.kt          # WebRTC video display
│   │   │   ├── LiveViewViewModel.kt       # Stream control
│   │   │   └── WebRTCVideoView.kt         # SurfaceViewRenderer
│   │   └── history/
│   │       ├── HistoryScreen.kt           # Session list
│   │       └── HistoryViewModel.kt        # History data
│   ├── child/
│   │   ├── ChildIdleScreen.kt        # SOS button + waiting state
│   │   └── ChildIdleViewModel.kt     # Command listener
│   ├── settings/
│   │   ├── SettingsScreen.kt         # Role-based settings UI
│   │   └── SettingsViewModel.kt      # Settings state
│   ├── navigation/
│   │   ├── NavGraph.kt               # All routes + deep link handling
│   │   └── NavRoutes.kt              # Route constants
│   └── theme/
│       ├── FamilyConnectTheme.kt     # Material 3 theming
│       └── Type.kt                   # Typography
│
├── webrtc/
│   └── WebRTCClient.kt               # PeerConnection factory + signaling
│
├── security/
│   └── EncryptionUtils.kt            # AES encryption helper
│
└── util/
    └── Constants.kt                   # Shared prefs keys, ref paths
```

---

## Firebase Setup

### Prerequisites
- Google/Firebase account
- Android package name: `com.familyconnect.app`

### Step 1: Create Firebase Project
1. [Firebase Console](https://console.firebase.google.com/) → **Add project**
2. Name: `MonitorApp` (ya kuch bhi)
3. Analytics: disable (optional)
4. **Create project**

### Step 2: Register Android App
1. Android icon click karein
2. Package name: `com.familyconnect.app`
3. App nickname: `Family Connect`
4. `google-services.json` download karein
5. `app/google-services.json` ko replace karein

### Step 3: Enable Authentication
1. **Authentication** → **Sign-in method**
2. **Anonymous** enable karein (yeh important hai — app anonymous sign-in use karta hai)

### Step 4: Realtime Database
1. **Realtime Database** → **Create Database**
2. Location: `asia-southeast1` (ya nearest)
3. **Start in test mode** → **Enable**

### Step 5: Security Rules
Firebase Console → Realtime Database → Rules → yeh rules daalein:

```json
{
  "rules": {
    "pairingCodes": {
      ".read": "auth != null",
      ".write": "auth != null",
      "$code": {
        ".validate": "newData.child('code').val() === $code"
      }
    },
    "devices": {
      ".read": "auth != null",
      ".write": "auth != null",
      "$childId": {
        ".read": "auth != null",
        ".write": "auth.uid === $childId"
      }
    },
    "commands": {
      "$childId": {
        ".read": "auth != null",
        ".write": "auth != null"
      }
    },
    "signaling": {
      "$sessionId": {
        ".read": "auth != null",
        ".write": "auth != null"
      }
    },
    "sessions": {
      "$sessionId": {
        ".read": "auth != null",
        ".write": "auth != null"
      }
    },
    "parentChildren": {
      "$parentId": {
        ".read": "auth.uid === $parentId",
        ".write": "auth != null"
      }
    },
    "sos_alerts": {
      "$parentId": {
        ".read": "auth.uid === $parentId",
        ".write": "auth != null"
      }
    }
  }
}
```

### Step 6: (Optional) Cloud Messaging
1. **Cloud Messaging** → APK ke through register ho jayega
2. Kuch aur setup zaroori nahi

---

## GitHub & Release

### Repository
```
https://github.com/AKASH991833/MonitorApp
```

### Latest APK Download
```
https://github.com/AKASH991833/MonitorApp/releases/download/v2.0.0/FamilyConnect-v2.0.0.apk
```

### Naya Release Kaise Banayein
```bash
# Code push
git add -A
git commit -m "description"
git push origin main

# Tag + release
git tag v2.0.1
git push origin v2.0.1

# GitHub par release create karein → APK upload karein
```

---

## Build Instructions

### Prerequisites
- Android Studio Ladybug (2024.x) or later
- JDK 17 (bundled with Android Studio)
- Android SDK 34
- Gradle 8.x

### Clone & Build
```bash
git clone https://github.com/AKASH991833/MonitorApp.git
cd MonitorApp
./gradlew assembleDebug
```

APK location: `app/build/outputs/apk/debug/app-debug.apk`

### Generate Signed APK (Production)
Android Studio → **Build** → **Generate Signed Bundle/APK**

---

## Pairing Flow (Technical)

### Parent Side (`ParentPairingScreen` + `PairingViewModel`)
```kotlin
generatePairingCode(parentName) → {
  1. Generate 6-char random code (ABC123)
  2. Save to Firebase: pairingCodes/ABC123 { parentId, parentName, expiresAt }
  3. Save locally via SharedPreferences
  4. Return code
}
```

### Child Side (`ChildPairingScreen` + `PairingViewModel`)
```kotlin
validatePairingCode(code, childName) → {
  1. Check Firebase: pairingCodes/$code exists?
  2. Check expiry (10 minutes)
  3. If valid → mark isUsed=true
  4. Save child to local Room DB
  5. Register FCM token: devices/$childId { fcmToken, parentId }
  6. Notify parent: parentChildren/$parentId/$childId { childName, pairedAt }
}
```

### Parent Dashboard Sync
```kotlin
ParentDashboardViewModel listens to:
  - parentChildren/$parentId/ → new child appears → save to Room DB → show card
  - devices/$childId/status → battery, network, lastSeen
  - devices/$childId/location → lat, lng, accuracy
  - sos_alerts/$parentId/ → SOS from child
```

---

## Role-Specific Settings

| Setting | Parent | Child |
|---------|--------|-------|
| Stream Quality (SD/HD) | ✅ | ❌ |
| Auto-Timeout (5/10/15/30m) | ✅ | ❌ |
| Ambient Audio Mode | ✅ | ❌ |
| App Usage Tracking | ✅ | ❌ |
| Share Location | ❌ | ✅ |
| SOS Alerts | ❌ | ✅ |
| Auto-Start Monitoring | ❌ | ✅ |
| Dark Theme | ✅ | ✅ |
| Privacy Policy | ✅ | ✅ |
| Switch Role / Logout | ✅ | ✅ |

---

## Firebase RTDB Structure

```
/
├── pairingCodes/
│   └── ABC123/
│       ├── code: "ABC123"
│       ├── parentId: "firebase-uid-1"
│       ├── parentName: "Papa"
│       ├── createdAt: 1712345678000
│       ├── expiresAt: 1712346278000
│       └── isUsed: true
│
├── devices/
│   └── CHILD_UID/
│       ├── fcmToken: "fcm-token-xyz"
│       ├── parentId: "PARENT_UID"
│       ├── isOnline: true
│       ├── lastSeen: 1712345678000
│       ├── status/
│       │   ├── battery: 85
│       │   ├── isCharging: false
│       │   └── network: "wifi"
│       └── location/
│           ├── lat: 19.123456
│           ├── lng: 72.987654
│           └── accuracy: 12.5
│
├── parentChildren/
│   └── PARENT_UID/
│       └── CHILD_UID/
│           ├── childName: "Beta"
│           └── pairedAt: 1712345678000
│
├── commands/
│   └── CHILD_UID/
│       └── -Nxyz123/
│           ├── command: "start_stream"
│           ├── sessionId: "uuid-here"
│           └── timestamp: 1712345678000
│
├── signaling/
│   └── SESSION_ID/
│       ├── offer: { sdp: "...", type: "offer" }
│       ├── answer: { sdp: "...", type: "answer" }
│       ├── candidates: [...]
│       └── childCandidates: [...]
│
├── sos_alerts/
│   └── PARENT_UID/
│       └── -Nabc123/
│           ├── childId: "CHILD_UID"
│           ├── childName: "Beta"
│           ├── timestamp: 1712345678000
│           └── message: "Help me!"
│
└── sessions/
    └── SESSION_ID/
        ├── sessionId: "uuid"
        ├── childId: "CHILD_UID"
        ├── parentId: "PARENT_UID"
        ├── startTime: 1712345678000
        ├── endTime: 1712345679000
        └── status: "completed"
```

---

## Dependencies

| Library | Version | Purpose |
|---------|---------|---------|
| Kotlin | 2.0.21 | Language |
| Jetpack Compose BOM | 2024.12.01 | UI framework |
| Material 3 | 1.3.x | Design system |
| Navigation Compose | 2.8.x | Screen navigation |
| Firebase Auth | 23.x | Anonymous auth |
| Firebase RTDB | 21.x | Real-time data |
| Firebase FCM | 24.x | Push commands |
| Firebase ML Kit Barcode | 18.x | QR scanning |
| CameraX | 1.4.x | Camera for QR |
| WebRTC | 1.0.32006 | Video streaming |
| Room | 2.6.x | Local database |
| Timber | 5.0.x | Logging |
| ZXing | 3.5.x | QR code generation |

---

## Troubleshooting

### "Pairing code not found" Error
- Firebase rules check karein → `pairingCodes` path `.read` permission `auth != null` hona chahiye
- Code expiry check karein (10 minutes)
- Dono devices par Firebase same instance use kar raha hai?

### Parent Dashboard Empty
- Wait karein — child pair karne ke baad notification aane mein 2-3 seconds lagte hain
- Firebase `parentChildren/$parentId` path check karein
- Force refresh: dashboard se back jaakar dobara aayein

### QR Scan Not Working
- Camera permission allow karein
- Good lighting — QR code clear hona chahiye
- Google Camera scan karega to Chrome khulega (APK download) — yeh expected behaviour hai
- In-app scanner istemal karein to code auto-fill hoga

### Live Stream Not Connecting
- Dono devices same network par hain ya STUN/TURN configured hai
- Firebase signaling path `signaling/$sessionId` readable/writable hai?
- Check `ForegroundMonitorService` logs

---

## License

Copyright (c) 2026 Family Connect. All rights reserved.
