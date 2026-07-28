# Family Connect

A real-time audio/video monitoring application that allows parents to view and listen to their child's environment securely.

## Overview

Family Connect enables secure, real-time streaming between a parent and child device. The child device runs a foreground service that captures camera and microphone input, streams it via WebRTC to the parent device, which displays the live feed. The app uses Firebase for authentication, messaging, and real-time data sync.

### Key Features

- Real-time audio and video streaming via WebRTC
- Foreground monitoring service with camera/microphone/location access
- Firebase Cloud Messaging (FCM) for remote commands
- Firebase Authentication for user management
- Firebase Realtime Database for device pairing and status
- Parent and Child role-based interfaces
- Emergency stop functionality
- Adaptive streaming quality (Low/SD/HD)
- Dark theme support with Material 3 Design

## Architecture

The app follows **MVVM** (Model-View-ViewModel) architecture with **Jetpack Compose** for UI.

```
app/
├── data/
│   ├── local/          # Room database, DataStore
│   ├── model/          # Data models
│   ├── remote/         # Firebase API, Retrofit services
│   └── repository/     # Repository pattern
├── navigation/         # Compose navigation
├── security/           # Encryption, auth utilities
├── service/            # Foreground service, FCM service
├── ui/
│   ├── child/          # Child role screens
│   ├── navigation/     # Navigation graphs
│   ├── onboarding/     # Onboarding screens
│   ├── pairing/        # Pairing flow screens
│   ├── parent/         # Parent role screens
│   │   ├── dashboard/  # Parent dashboard
│   │   ├── history/    # Viewing history
│   │   └── liveview/   # Live stream viewer
│   ├── role/           # Role-related UI
│   ├── roleselection/  # Role selection screen
│   ├── settings/       # Settings screens
│   ├── splash/         # Splash screen
│   └── theme/          # Material 3 theming
├── util/               # Utility classes
└── webrtc/             # WebRTC client implementation
```

## Setup Instructions

### 1. Firebase Project Creation

1. Go to the [Firebase Console](https://console.firebase.google.com/)
2. Click **Add project** and follow the setup wizard
3. Enter your project name (e.g., "Family Connect")
4. Disable Google Analytics if not needed
5. Click **Create project**

### 2. Register Android App in Firebase

1. In the Firebase Console, click the **Android** icon to add an Android app
2. Enter package name: `com.familyconnect.app`
3. Enter app nickname (optional)
4. Download the `google-services.json` file
5. Replace the placeholder at `app/google-services.json` with the downloaded file

### 3. Enable Firebase Authentication

1. In Firebase Console, go to **Authentication** > **Sign-in method**
2. Enable **Email/Password** sign-in
3. (Optional) Enable **Google** or **Phone** sign-in as needed

### 4. Set Up Realtime Database

1. In Firebase Console, go to **Realtime Database**
2. Click **Create Database**
3. Choose a location
4. Start in **test mode** (update rules later):

```json
{
  "rules": {
    "devices": {
      "$uid": {
        ".read": "$uid === auth.uid || auth.uid === root.child('pairings').child($uid).child('parentId').val()",
        ".write": "$uid === auth.uid",
        "fcmToken": { ".validate": "newData.isString()" },
        "online": { ".validate": "newData.isBoolean()" },
        "lastPing": { ".validate": "newData.isNumber()" }
      }
    },
    "pairings": {
      "$pairingId": {
        ".read": "auth.uid === data.child('childId').val() || auth.uid === data.child('parentId').val()",
        ".write": "auth.uid === data.child('childId').val() || auth.uid === data.child('parentId').val()"
      }
    },
    "sessions": {
      "$sessionId": {
        ".read": "auth.uid !== null",
        ".write": "auth.uid !== null"
      }
    }
  }
}
```

### 5. Enable Firebase Cloud Messaging (FCM)

1. In Firebase Console, go to **Cloud Messaging**
2. Note the **Server Key** for sending messages from your backend
3. FCM is already configured via the `google-services.json` file

### 6. Enable Crashlytics (Optional)

1. In Firebase Console, go to **Crashlytics**
2. Click **Enable Crashlytics**
3. Add the Crashlytics SDK dependency (see Dependencies section)

### 7. Google Maps API Key Setup

If using location features:

1. Go to [Google Cloud Console](https://console.cloud.google.com/)
2. Select your project
3. Go to **APIs & Services** > **Credentials**
4. Click **Create Credentials** > **API Key**
5. Restrict the key to **Android Maps SDK**
6. Add your app's SHA-1 fingerprint
7. Add the key to `local.properties`:

```properties
google.maps.key=YOUR_API_KEY
```

### 8. WebRTC Setup (STUN/TURN Servers)

The app includes default Google STUN servers:

- `stun:stun.l.google.com:19302`
- `stun:stun1.l.google.com:19302`

For production, configure TURN servers in `gradle.properties` or a config file:

```properties
turn.server.url=turn:your-turn-server.com:3478
turn.server.username=username
turn.server.credential=password
```

To set up your own TURN server, use [coturn](https://github.com/coturn/coturn):

```bash
# Install coturn
sudo apt-get install coturn

# Configure /etc/turnserver.conf
listening-port=3478
fingerprint
lt-cred-mech
user=username:password
realm=your-domain.com
```

## Build Instructions

### Prerequisites

- Android Studio Hedgehog (2023.1.1) or later
- JDK 17
- Android SDK 34
- Gradle 8.x

### Clone & Build

```bash
# Clone the repository
git clone https://github.com/yourusername/family-connect.git
cd family-connect

# Build debug APK
./gradlew assembleDebug

# Build release APK
./gradlew assembleRelease

# Install on connected device
./gradlew installDebug

# Run unit tests
./gradlew testDebugUnitTest

# Run instrumented tests
./gradlew connectedDebugAndroidTest

# Lint checks
./gradlew lintDebug
```

### Generate Signed Bundle/APK

1. In Android Studio: **Build** > **Generate Signed Bundle/APK**
2. Create or select a keystore
3. Enter key aliases and passwords
4. Select **release** build variant
5. The signed APK/AAB will be in `app/release/`

## Testing

### Unit Tests

Unit tests use JUnit 4, Mockito, and Turbine for Flow testing:

```bash
./gradlew testDebugUnitTest
```

### Instrumentation Tests

```bash
./gradlew connectedDebugAndroidTest
```

### Manual Testing

1. **Parent-Child Pairing**: Install on two devices, select different roles, and complete the pairing flow
2. **Live Streaming**: From parent dashboard, tap "View Stream" to initiate WebRTC connection
3. **Emergency Stop**: From child notification or parent app, trigger emergency stop
4. **FCM Commands**: Use Firebase Console Notifications to send data messages:
   ```json
   {
     "command": "wake",
     "sessionId": "test-session-123",
     "parentId": "parent-uid"
   }
   ```

## Dependencies

### Core
- **Kotlin** 1.9.x - Programming language
- **AndroidX Core KTX** 1.12.x - AndroidX core extensions

### UI
- **Jetpack Compose BOM** 2024.x - Declarative UI framework
- **Material 3** - Material Design 3 components
- **Navigation Compose** 2.7.x - Navigation for Compose
- **Activity Compose** 1.8.x - Activity integration with Compose
- **Lifecycle Runtime Compose** 2.7.x - Lifecycle-aware Compose

### Firebase
- **Firebase Authentication** 22.x - User authentication
- **Firebase Realtime Database** 20.x - Real-time data sync
- **Firebase Cloud Messaging** 23.x - Push notifications
- **Firebase Crashlytics** 18.x (optional) - Crash reporting
- **Firebase Analytics** 21.x (optional) - App analytics

### WebRTC
- **org.webrtc:google-webrtc** 1.0.32006 - WebRTC implementation

### Networking
- **Retrofit** 2.9.x - HTTP client
- **OkHttp** 4.12.x - HTTP client and WebSocket
- **Moshi** 1.15.x - JSON serialization

### Async & DI
- **Kotlin Coroutines** 1.7.x - Async programming
- **Hilt** 2.50 - Dependency injection (optional)

### Logging
- **Timber** 5.0.x - Logging

### Testing
- **JUnit** 4.13.x - Unit testing
- **Mockito** 4.x / **MockK** 1.13.x - Mocking
- **Turbine** 1.0.x - Flow testing
- **Espresso** 3.5.x - UI testing
- **Compose UI Test** 1.6.x - Compose testing

### Data
- **Room** 2.6.x - Local database
- **DataStore** 1.0.x - Preferences storage
- **Coil** 2.5.x - Image loading

### Permissions
- **Accompanist Permissions** 0.34.x - Compose permissions

## License

Copyright (c) 2024 Family Connect

All rights reserved. This software is proprietary and confidential.
