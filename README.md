# PoseMonitor

## Setup Instructions

### Firebase Configuration
To use the Firebase features in this project, you need to provide your own `google-services.json` file in the `app/` directory.

If you are pulling this code for the first time, you will find a placeholder `app/google-services.json`. You must replace the following values with your actual Firebase project details:

1.  **project_number**: Found in Firebase Project Settings.
2.  **project_id**: Your Firebase project ID.
3.  **storage_bucket**: Your Firebase Storage bucket URL.
4.  **mobilesdk_app_id**: Your Android App ID in Firebase.
5.  **client_id**: Your OAuth 2.0 client IDs (Web and Android).
6.  **current_key**: Your Firebase API Key.
7.  **certificate_hash**: The SHA-1 hash of your signing certificate.

Alternatively, the easiest way is to download the `google-services.json` directly from the [Firebase Console](https://console.firebase.google.com/) and replace the placeholder file.

### Environment Variables
Ensure you have a `local.properties` file in the root directory with the following keys if you plan to use Google Sign-In or AI features:
```properties
GOOGLE_WEB_CLIENT_ID_DEBUG=your_web_client_id
GOOGLE_WEB_CLIENT_ID_RELEASE=your_web_client_id
GROQ_API_KEY=your_groq_api_key
```

### Git
The `google-services.json` file is added to `.gitignore` to prevent sensitive credentials from being committed to the repository.
