# Requirements Document

## Introduction

This feature adds a 14-screen user onboarding questionnaire to the existing `com.posemonitor2` Android app. After a user authenticates via `LoginActivity`, the system checks whether they have completed the questionnaire. If not, the system presents a full-screen `QuestionnaireActivity` (no bottom nav) that collects personalisation data across 14 sequential question screens. On completion, answers are persisted to Room and synced to Firestore, and the user is taken to `UserFlowActivity`. Existing users without answers see the questionnaire on next login. Users may re-edit answers at any time from the Profile screen.

---

## Glossary

- **Questionnaire**: The 14-screen question flow implemented in `QuestionnaireActivity`.
- **QuestionnaireActivity**: The new full-screen `ComponentActivity` that hosts the questionnaire pager.
- **OnboardingResponseEntity**: The new Room entity (table `onboarding_responses`) that stores questionnaire answers per user.
- **OnboardingResponseDao**: The Room DAO for `onboarding_responses`.
- **OnboardingRepository**: The new domain-layer repository interface for reading and writing questionnaire answers.
- **OnboardingRepositoryImpl**: The data-layer implementation of `OnboardingRepository`.
- **QuestionnaireViewModel**: The `HiltViewModel` that drives `QuestionnaireActivity` state.
- **FirestoreOnboardingDto**: The Kotlinx-serializable DTO for the Firestore `onboarding_responses` collection.
- **FirebaseSyncRunner**: The existing singleton that processes the `sync_queue` table; must be extended to handle `onboarding_responses`.
- **ProfileEntity**: The existing Room entity in the `profiles` table; must gain 11 new nullable questionnaire columns (migration 5→6).
- **UserProfile**: The existing domain model that mirrors `ProfileEntity`; must be extended to match.
- **LoginActivity**: The existing authentication entry point; must be modified to route to `QuestionnaireActivity` when the questionnaire is incomplete.
- **UserFlowActivity**: The existing main app activity with bottom navigation; reached after questionnaire completion.
- **ProfileScreen**: The existing Compose screen; must show "Edit Questionnaire" row and a questionnaire summary section.
- **Edit mode**: Launching `QuestionnaireActivity` from `ProfileScreen` with pre-filled answers; dismissible.
- **First-run mode**: Launching `QuestionnaireActivity` after login when `is_completed = false`; non-dismissible.
- **Primary Orange**: `#E8622E` — accent colour used throughout the app.
- **Dark Teal Background**: `#0E1F20` — questionnaire background.
- **Card Teal**: `#173436` — questionnaire card background.

---

## Requirements

### Requirement 1: Questionnaire Completion Check at Login

**User Story:** As a new or returning user, I want the app to automatically determine whether I still need to complete the questionnaire, so that I am only interrupted by it when necessary.

#### Acceptance Criteria

1. WHEN `LoginActivity.onAuthenticated` is called with a valid user ID, THE `LoginActivity` SHALL query `OnboardingRepository` to check whether `onboarding_responses.is_completed = true` for that user ID before navigating.
2. WHEN `is_completed = false` or no record exists for the user, THE `LoginActivity` SHALL launch `QuestionnaireActivity` in first-run mode and call `finish()`.
3. WHEN `is_completed = true`, THE `LoginActivity` SHALL launch `UserFlowActivity` directly and call `finish()`.
4. WHILE the completion check is in progress, THE `LoginActivity` SHALL display a loading indicator and prevent duplicate navigation.
5. IF the completion check fails with a database or network error, THEN THE `LoginActivity` SHALL fall back to launching `QuestionnaireActivity` in first-run mode to ensure questionnaire data is not silently skipped.

---

### Requirement 2: Questionnaire Activity — Navigation Shell

**User Story:** As a user going through the questionnaire, I want a smooth, guided single-page flow with clear progress indication so I always know where I am and can go back.

#### Acceptance Criteria

1. THE `QuestionnaireActivity` SHALL host a `HorizontalPager` with exactly 14 question pages plus 1 summary page (15 total pages), using slide animation between pages.
2. THE `QuestionnaireActivity` SHALL display a `LinearProgressIndicator` at the top of the screen showing `currentPage / 14` as a fraction between 0.0 and 1.0.
3. WHEN a user taps the Back button on any page after the first, THE `QuestionnaireActivity` SHALL navigate to the previous page without exiting the activity.
4. WHEN a user taps the Back button on the first page in first-run mode, THE `QuestionnaireActivity` SHALL consume the back event and remain on the first page (non-dismissible).
5. WHEN a user taps the Back button in edit mode, THE `QuestionnaireActivity` SHALL allow dismissal and finish the activity without saving changes if the user has not yet reached the summary page.
6. THE `QuestionnaireActivity` SHALL use the dark teal background `#0E1F20` and card background `#173436` throughout all screens.
7. THE `QuestionnaireActivity` SHALL display a "Next" button that is disabled until the current screen's required answer is provided.
8. WHEN the user is on any multi-select screen, THE `QuestionnaireActivity` SHALL enable the "Next" button only after at least 1 option is selected.

---

### Requirement 3: Single-Choice Question Screens (Screens 1–6, 10–11)

**User Story:** As a user, I want to answer single-choice questions using radio-button-style option cards so I can quickly select one answer and proceed.

#### Acceptance Criteria

1. WHEN a single-choice question screen is displayed, THE `Questionnaire` SHALL render each option as a selectable card; selecting a card deselects any previously selected card.
2. WHEN a card is selected, THE `Questionnaire` SHALL visually distinguish it using the Primary Orange accent border and background tint.
3. WHEN the user taps "Next" on a single-choice screen with a valid selection, THE `QuestionnaireViewModel` SHALL record the selected value in the in-memory answer state.
4. THE `Questionnaire` SHALL implement the following screens as single-choice:
   - Screen 1 (Age Group): options `Under 18`, `18–24`, `25–34`, `35–44`, `45–54`, `55+`; store key `age_group`.
   - Screen 2 (Gender): options `Male`, `Female`, `Prefer not to say`; store key `gender`.
   - Screen 3 (Primary Goal): options `Improve Flexibility`, `Improve Posture`, `Reduce Stress`, `Lose Weight`, `Build Strength`, `Increase Mobility`, `General Fitness`; store key `goal`.
   - Screen 4 (Yoga Experience): options `Complete Beginner`, `Tried a Few Times`, `Regular Practitioner`, `Advanced`; store key `experience_level`.
   - Screen 5 (Preferred Yoga Style): options `Hatha Yoga`, `Vinyasa Yoga`, `Yin Yoga`, `Power Yoga`, `Chair Yoga`, `Meditation & Breathing`; store key `yoga_style`.
   - Screen 6 (Flexibility Level): options `Very Stiff`, `Slightly Stiff`, `Average`, `Flexible`, `Very Flexible`; store key `flexibility_level`.
   - Screen 10 (Daily Practice Time): options `10 Minutes`, `20 Minutes`, `30 Minutes`, `45 Minutes`, `60+ Minutes`; store integer values `10`, `20`, `30`, `45`, `60` respectively; store key `daily_practice_minutes`.
   - Screen 11 (Weekly Frequency): options `1–2 Days`, `3–4 Days`, `5–6 Days`, `Daily`; store key `weekly_frequency`.

---

### Requirement 4: Multi-Select Question Screens (Screens 7, 12, 13)

**User Story:** As a user, I want to select multiple relevant options on applicable questions so my profile accurately reflects my needs.

#### Acceptance Criteria

1. WHEN a multi-select question screen is displayed, THE `Questionnaire` SHALL render each option as a toggleable checkbox card; the user may select zero or more options before tapping Next.
2. WHEN at least one option is selected, THE `QuestionnaireActivity` SHALL enable the "Next" button.
3. WHEN "Submit" is called, THE `QuestionnaireViewModel` SHALL serialise the set of selected option values as a comma-separated string for storage.
4. THE `Questionnaire` SHALL implement Screen 7 (Focus Areas) with options `Neck`, `Shoulders`, `Back`, `Core`, `Hips`, `Legs`, `Balance`, `Full Body`; store key `focus_areas`.
5. THE `Questionnaire` SHALL implement Screen 12 (Injuries) with options `None`, `Neck`, `Spine`, `Shoulder`, `Wrist`, `Hip`, `Knee`, `Ankle`; store key `injuries`.
6. WHEN `None` is selected on Screen 12, THE `QuestionnaireViewModel` SHALL deselect all other injury options and store the value `none`.
7. WHEN any non-`None` injury option is selected after `None` was chosen, THE `QuestionnaireViewModel` SHALL deselect `None` automatically.
8. THE `Questionnaire` SHALL implement Screen 13 (Medical Conditions) with options `None`, `High Blood Pressure`, `Heart Condition`, `Asthma`, `Diabetes`, `Arthritis`, `Pregnancy`, `Other`; store key `medical_conditions`.
9. WHEN `None` is selected on Screen 13, THE `QuestionnaireViewModel` SHALL deselect all other medical condition options.
10. WHEN any non-`None` medical condition is selected after `None` was chosen, THE `QuestionnaireViewModel` SHALL deselect `None` automatically.

---

### Requirement 5: Numeric Input Screens — Height and Weight (Screens 8–9)

**User Story:** As a user, I want to enter my height and weight in my preferred unit system so the app stores accurate body metrics.

#### Acceptance Criteria

1. THE `Questionnaire` Screen 8 (Height) SHALL present a numeric text field with a unit toggle between `cm` and `ft/in`.
2. WHEN the unit toggle is switched, THE `Questionnaire` SHALL convert the currently entered value to the newly selected unit and update the displayed value.
3. WHEN the user taps "Next" on Screen 8, THE `QuestionnaireViewModel` SHALL convert the entered height to centimetres as an `Int` and store it as `height_cm`.
4. THE `Questionnaire` Screen 9 (Weight) SHALL present a numeric text field with a unit toggle between `kg` and `lb`.
5. WHEN the unit toggle is switched on Screen 9, THE `Questionnaire` SHALL convert the currently entered value to the newly selected unit.
6. WHEN the user taps "Next" on Screen 9, THE `QuestionnaireViewModel` SHALL convert the entered weight to kilograms as a `Double` and store it as `weight_kg`.
7. IF the user submits an empty or non-numeric value on Screen 8 or Screen 9, THEN THE `Questionnaire` SHALL keep the "Next" button disabled and display an inline validation message.

---

### Requirement 6: Camera Permission Screen (Screen 14)

**User Story:** As a user, I want to decide whether to grant camera access for AI pose correction so I can make an informed choice before using the feature.

#### Acceptance Criteria

1. THE `Questionnaire` Screen 14 SHALL present two options: `Enable Pose Correction` and `Continue Without Pose Correction`.
2. WHEN `Enable Pose Correction` is tapped, THE `QuestionnaireActivity` SHALL use `ActivityResultContracts.RequestPermission` to request `android.permission.CAMERA`.
3. WHEN the camera permission is granted by the system, THE `QuestionnaireViewModel` SHALL set `camera_permission_granted = true`.
4. WHEN the camera permission is denied by the system, THE `QuestionnaireViewModel` SHALL set `camera_permission_granted = false` and the activity SHALL proceed to the summary screen.
5. WHEN `Continue Without Pose Correction` is tapped, THE `QuestionnaireViewModel` SHALL set `camera_permission_granted = false` without requesting the system permission.
6. WHEN either option results in a definitive value for `camera_permission_granted`, THE `QuestionnaireActivity` SHALL enable navigation to the summary screen.

---

### Requirement 7: Summary Screen and Submission

**User Story:** As a user, I want to review a brief summary of my answers before submitting so I can confirm everything is correct before starting.

#### Acceptance Criteria

1. AFTER Screen 14, THE `QuestionnaireActivity` SHALL display a summary screen showing the user's goal, experience level, focus areas, and daily practice time.
2. THE summary screen SHALL display a "Start Your Journey" button.
3. WHEN the user taps "Start Your Journey", THE `QuestionnaireViewModel` SHALL call `OnboardingRepository.saveAnswers()` to persist the complete answer set to Room with `is_completed = true`.
4. WHEN `saveAnswers()` succeeds, THE `QuestionnaireActivity` SHALL also update `ProfileEntity.height_cm`, `ProfileEntity.weight_kg`, and `ProfileEntity.fitness_goal` via `ProfileRepository.upsertProfile()`.
5. WHEN `saveAnswers()` succeeds and all profile fields are updated, THE `QuestionnaireActivity` SHALL navigate to `UserFlowActivity` (in first-run mode) or finish the activity (in edit mode).
6. IF `saveAnswers()` fails, THEN THE `QuestionnaireActivity` SHALL display a snackbar error and allow the user to retry without re-entering answers.

---

### Requirement 8: Persistent Storage — Room Entity and Migration

**User Story:** As a developer, I want questionnaire answers stored in Room with a proper schema migration so data integrity is maintained across app updates.

#### Acceptance Criteria

1. THE `OnboardingResponseEntity` SHALL be defined as a Room entity for table `onboarding_responses` with primary key `user_id` and columns: `age_group TEXT`, `gender TEXT`, `goal TEXT`, `experience_level TEXT`, `yoga_style TEXT`, `flexibility_level TEXT`, `focus_areas TEXT`, `height_cm INTEGER`, `weight_kg REAL`, `daily_practice_minutes INTEGER`, `weekly_frequency TEXT`, `injuries TEXT`, `medical_conditions TEXT`, `camera_permission_granted INTEGER` (Boolean), `is_completed INTEGER` (Boolean), `updated_at INTEGER`.
2. THE `PoseDatabase` version SHALL be incremented from 5 to 6, and `OnboardingResponseEntity` SHALL be registered in the `@Database` annotation.
3. THE `DatabaseMigrations` object SHALL define `MIGRATION_5_6` which: (a) creates the `onboarding_responses` table with all columns from AC 8.1, and (b) adds 11 nullable questionnaire columns to the existing `profiles` table: `age_group TEXT`, `gender TEXT`, `experience_level TEXT`, `yoga_style TEXT`, `flexibility_level TEXT`, `focus_areas TEXT`, `daily_practice_minutes INTEGER`, `weekly_frequency TEXT`, `injuries TEXT`, `medical_conditions TEXT`, `camera_permission_granted INTEGER`.
4. THE `DataModule` SHALL register `MIGRATION_5_6` in the `Room.databaseBuilder` call.
5. THE `ProfileEntity` SHALL be updated with the 11 new nullable fields matching the migration columns.
6. THE `UserProfile` domain model SHALL be updated with the 11 new nullable fields matching `ProfileEntity`.

---

### Requirement 9: Data Access — DAO and Repository

**User Story:** As a developer, I want a clean DAO and repository interface for questionnaire data so the presentation layer can read and write answers without direct database coupling.

#### Acceptance Criteria

1. THE `OnboardingResponseDao` SHALL expose: `upsert(entity: OnboardingResponseEntity)`, `getByUserId(userId: String): OnboardingResponseEntity?`, `observeByUserId(userId: String): Flow<OnboardingResponseEntity?>`, and `isCompleted(userId: String): Boolean`.
2. THE `OnboardingRepository` interface (domain module) SHALL expose: `suspend fun saveAnswers(answers: OnboardingResponse): Result<Unit>`, `suspend fun getAnswers(userId: String): OnboardingResponse?`, `fun observeAnswers(userId: String): Flow<OnboardingResponse?>`, and `suspend fun isCompleted(userId: String): Boolean`.
3. THE `OnboardingRepositoryImpl` SHALL implement `OnboardingRepository` by delegating local reads/writes to `OnboardingResponseDao`.
4. THE `DataModule` SHALL bind `OnboardingRepositoryImpl` to `OnboardingRepository` and provide `OnboardingResponseDao` from `PoseDatabase`.

---

### Requirement 10: Firestore Sync — Onboarding Responses

**User Story:** As a user with multiple devices, I want my questionnaire answers synced to the cloud so that I do not need to repeat the questionnaire after reinstalling or switching devices.

#### Acceptance Criteria

1. THE `FirestoreOnboardingDto` SHALL be a `@Serializable` data class with all fields from `OnboardingResponseEntity` plus a `toMap()` function.
2. THE `FirebaseRemoteClient` SHALL expose `suspend fun upsertOnboardingResponse(dto: FirestoreOnboardingDto)` and `suspend fun fetchOnboardingResponse(userId: String): FirestoreOnboardingDto?` targeting the Firestore collection `onboarding_responses`.
3. WHEN `OnboardingRepositoryImpl.saveAnswers()` is called, THE `OnboardingRepositoryImpl` SHALL attempt a direct Firestore upsert of the `FirestoreOnboardingDto`.
4. IF the direct Firestore upsert fails, THEN THE `OnboardingRepositoryImpl` SHALL enqueue a `SyncQueueEntity` with `entityType = "onboarding_responses"` and `operation = "UPSERT"`.
5. THE `FirebaseSyncRunner.syncPending()` SHALL handle `entityType = "onboarding_responses"` by decoding the payload as `FirestoreOnboardingDto` and calling `firebaseRemoteClient.upsertOnboardingResponse(dto)`.
6. WHEN `OnboardingRepositoryImpl.isCompleted()` is called and no local record exists, THE `OnboardingRepositoryImpl` SHALL call `firebaseRemoteClient.fetchOnboardingResponse(userId)` and, if a remote record with `is_completed = true` is found, save it locally and return `true`.

---

### Requirement 11: Edit Mode — Re-editing from Profile Screen

**User Story:** As a returning user, I want to update my questionnaire answers from the Profile screen so my personalisation data stays current as my fitness goals change.

#### Acceptance Criteria

1. THE `ProfileScreen` SHALL display an "Edit Questionnaire" row inside the App Settings card, rendered using the same `SettingsRow` style as existing rows.
2. WHEN the user taps "Edit Questionnaire", THE `ProfileScreen` SHALL launch `QuestionnaireActivity` with an extra `EXTRA_MODE = "edit"` and the existing `OnboardingResponse` answers pre-populated.
3. WHEN `QuestionnaireActivity` is launched in edit mode with existing answers, THE `QuestionnaireViewModel` SHALL pre-populate all 14 question screens with the previously saved values.
4. WHEN the user completes and submits in edit mode, THE `QuestionnaireActivity` SHALL save the updated answers and finish, returning to `ProfileScreen`.
5. WHEN the user presses the system back button before reaching the summary screen in edit mode, THE `QuestionnaireActivity` SHALL finish without saving changes.

---

### Requirement 12: Profile Screen Questionnaire Summary

**User Story:** As a user, I want to see a summary of my questionnaire answers on the Profile screen so I can quickly review my personalisation settings.

#### Acceptance Criteria

1. THE `ProfileScreen` SHALL display a "Your Yoga Profile" card showing the following fields drawn from `QuestionnaireViewModel`/`ProfileViewModel` state: `goal` (labelled "Primary Goal"), `experience_level` (labelled "Experience"), and `focus_areas` (labelled "Focus Areas").
2. WHEN `OnboardingResponse` data is not yet available (null), THE `ProfileScreen` SHALL display placeholder text "Not set" for each questionnaire field.
3. THE "Your Yoga Profile" card SHALL be placed above the existing "App Settings" card in the `ProfileScreen` scroll column.

---

### Requirement 13: Backward Compatibility

**User Story:** As an existing user upgrading from an older version, I want the app to correctly detect that my questionnaire has not been filled in and prompt me, so that I experience the same onboarding as new users.

#### Acceptance Criteria

1. WHEN an existing user (who has a `profiles` row but no `onboarding_responses` row) logs in after the app update, THE System SHALL treat their questionnaire status as incomplete and navigate to `QuestionnaireActivity` in first-run mode.
2. WHEN an existing user's `onboarding_responses` record is found in Firestore with `is_completed = true`, THE System SHALL sync it to Room on login and skip the questionnaire.
3. THE `MIGRATION_5_6` SQL migration SHALL add all new columns as nullable to the `profiles` table so existing profile rows are preserved without data loss.

---

### Requirement 14: UI/UX Consistency

**User Story:** As a user, I want the questionnaire to feel like a natural part of the app with the same visual language so it does not seem like an alien experience.

#### Acceptance Criteria

1. THE `QuestionnaireActivity` SHALL use `#0E1F20` as the screen background colour and `#173436` as the card/option background colour, matching the existing `OnboardingActivity` carousel palette.
2. THE `QuestionnaireActivity` SHALL use the Primary Orange `#E8622E` for selected option borders, progress bar fill, and the "Next"/"Start Your Journey" button background.
3. WHEN the keyboard appears on height or weight input screens, THE `QuestionnaireActivity` layout SHALL adjust upward so the input field remains visible (`imePadding()`).
4. THE `QuestionnaireActivity` SHALL use the `HorizontalPager` component with `animateScrollToPage` for transitions between screens, consistent with the existing `OnboardingActivity` pager approach.
