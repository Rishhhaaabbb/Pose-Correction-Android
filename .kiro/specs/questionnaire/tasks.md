# Implementation Plan: Onboarding Questionnaire

## Overview

Implement the 14-screen onboarding questionnaire in Kotlin/Jetpack Compose following the Clean Architecture layers already established in the project (data → domain → app). Tasks are ordered so each step compiles and is wired to the previous layer before the next begins.

## Tasks

- [x] 1. Extend domain layer with `OnboardingResponse` model and `OnboardingRepository` interface
  - Create `domain/src/main/java/com/domain/model/OnboardingResponse.kt` with all fields from design §1 (userId, ageGroup, gender, goal, experienceLevel, yogaStyle, flexibilityLevel, focusAreas, heightCm, weightKg, dailyPracticeMinutes, weeklyFrequency, injuries, medicalConditions, cameraPermissionGranted, isCompleted, updatedAtEpochMs)
  - Create `domain/src/main/java/com/domain/repository/OnboardingRepository.kt` with the four methods: `saveAnswers`, `getAnswers`, `observeAnswers`, `isCompleted`
  - Extend `UserProfile` in `domain/src/main/java/com/domain/model/UserProfile.kt` with the 11 new nullable questionnaire fields (ageGroup, gender, experienceLevel, yogaStyle, flexibilityLevel, focusAreas, dailyPracticeMinutes, weeklyFrequency, injuries, medicalConditions, cameraPermissionGranted)
  - _Requirements: 8.6, 9.2_

- [x] 2. Extend the data layer — Room entity, DAO, migration, and updated ProfileEntity
  - [x] 2.1 Create `OnboardingResponseEntity` in `data/src/main/java/com/data/local/entity/OnboardingResponseEntity.kt` matching the schema from design §3 (primary key `user_id`, all questionnaire columns, `is_completed INTEGER NOT NULL DEFAULT 0`, `updated_at INTEGER NOT NULL`)
    - _Requirements: 8.1_
  - [x] 2.2 Create `OnboardingResponseDao` in `data/src/main/java/com/data/local/dao/OnboardingResponseDao.kt` with `upsert`, `getByUserId`, `observeByUserId`, and `isCompleted` queries
    - _Requirements: 9.1_
  - [x] 2.3 Add `MIGRATION_5_6` to `DatabaseMigrations.kt`: CREATE TABLE `onboarding_responses` with all columns; ALTER TABLE `profiles` to add the 11 nullable questionnaire columns
    - _Requirements: 8.3_
  - [x] 2.4 Update `ProfileEntity.kt` to add the 11 new nullable `@ColumnInfo` fields matching the migration
    - _Requirements: 8.5_
  - [x] 2.5 Bump `PoseDatabase` to version 6: add `OnboardingResponseEntity::class` to `@Database` entities, add `onboardingResponseDao()` abstract function
    - _Requirements: 8.2_
  - [ ]* 2.6 Write migration integration test in `data/src/test/` using `MigrationTestHelper` to verify `MIGRATION_5_6` creates the new table and all new columns on `profiles`
    - _Requirements: 8.3_

- [x] 3. Extend mapper chain and domain model for the new fields
  - Update `ModelMappers.kt`: extend `ProfileEntity.toDomain()`, `UserProfile.toEntity()`, and `UserProfile.toFirestoreDto()` / `FirestoreProfileDto.toEntity()` to map the 11 new nullable fields
  - Update `FirestoreProfileDto` in `FirestoreDtos.kt` to add the 11 new nullable fields and include them in `toMap()`
  - Add `OnboardingResponse.toEntity()`, `OnboardingResponseEntity.toDomain()`, `OnboardingResponse.toFirestoreDto()`, and `FirestoreOnboardingDto.toEntity()` mappers to `ModelMappers.kt`
  - _Requirements: 8.5, 8.6, 10.1_

- [x] 4. Add `FirestoreOnboardingDto` and extend `FirebaseRemoteClient`
  - Append `FirestoreOnboardingDto` data class with `@Serializable` annotation and `toMap()` to `FirestoreDtos.kt`
  - Add `upsertOnboardingResponse(dto: FirestoreOnboardingDto)` to `FirebaseRemoteClient.kt`, targeting collection `onboarding_responses`, document ID = `dto.user_id`, using `SetOptions.merge()`
  - Add `fetchOnboardingResponse(userId: String): FirestoreOnboardingDto?` to `FirebaseRemoteClient.kt`, mapping Firestore snapshot fields the same way as the existing `fetchProfile` function
  - _Requirements: 10.1, 10.2_

- [x] 5. Implement `OnboardingRepositoryImpl` and extend `FirebaseSyncRunner`
  - [x] 5.1 Create `OnboardingRepositoryImpl` in `data/src/main/java/com/data/repository/OnboardingRepositoryImpl.kt` implementing `OnboardingRepository`; `saveAnswers` upserts locally then attempts Firestore upsert; on Firestore failure it enqueues `SyncQueueEntity(entityType="onboarding_responses", operation="UPSERT", ...)`; `isCompleted` checks local first, falls back to Firestore fetch if no local record
    - _Requirements: 9.3, 10.3, 10.4, 10.6_
  - [x] 5.2 Extend `FirebaseSyncRunner.syncPending()` to handle `entityType = "onboarding_responses"` by decoding as `FirestoreOnboardingDto` and calling `firebaseRemoteClient.upsertOnboardingResponse(dto)`
    - _Requirements: 10.5_
  - [ ]* 5.3 Write unit tests for `OnboardingRepositoryImpl` using MockK: verify Firestore failure enqueues sync item; verify `isCompleted` returns false when both local and remote records are absent
    - _Requirements: 9.3, 10.4, 10.6_

- [x] 6. Register everything in `DataModule`
  - Add `provideOnboardingResponseDao(db: PoseDatabase): OnboardingResponseDao = db.onboardingResponseDao()` to `DataProvidesModule`
  - Add `MIGRATION_5_6` to the `Room.databaseBuilder` `.addMigrations(...)` call
  - Add `bindOnboardingRepository(impl: OnboardingRepositoryImpl): OnboardingRepository` to `DataBindsModule`
  - _Requirements: 8.4, 9.4_

- [x] 7. Checkpoint — Ensure all data-layer unit tests pass
  - Ensure all tests pass, ask the user if questions arise.

- [x] 8. Implement `QuestionnaireViewModel`
  - [x] 8.1 Create `QuestionnaireViewModel.kt` in `app/src/main/java/com/posemonitor2/questionnaire/` with `@HiltViewModel`; define `QuestionnaireUiState` and `AnswerState` data classes as specified in design §11; inject `OnboardingRepository`, `ProfileRepository`, `AuthRepository`
    - _Requirements: 2.1, 3.3_
  - [x] 8.2 Implement answer-setting functions: `setSingleChoice(page, value)` dispatching to the correct `AnswerState` field; `toggleMultiSelectOption(page, option)` with None-mutual-exclusion logic (selecting "None" clears others; selecting non-None clears "None")
    - _Requirements: 3.1, 3.3, 4.6, 4.7, 4.9, 4.10_
  - [x] 8.3 Implement unit conversion pure functions: `ftInToCm(feet, inches): Int`, `cmToFtIn(cm): Pair<Int,Int>`, `lbToKg(lb): Double`, `kgToLb(kg): Double`; also implement `setHeight(raw, isMetric)` and `setWeight(raw, isMetric)` which parse input, convert, and update `AnswerState`
    - _Requirements: 5.2, 5.3, 5.5, 5.6_
  - [x] 8.4 Implement `isNextEnabled(page): Boolean` that returns `false` when the current page has no answer (null for single-choice, empty set for multi-select, null/invalid for numeric inputs, null for camera screen)
    - _Requirements: 2.7, 2.8, 5.7_
  - [x] 8.5 Implement `loadExistingAnswers(userId)` which calls `onboardingRepository.getAnswers(userId)` and populates `AnswerState` from the result
    - _Requirements: 11.3_
  - [x] 8.6 Implement `submitAnswers(userId)`: sets `isLoading=true`, calls `onboardingRepository.saveAnswers()`, then calls `profileRepository.upsertProfile()` with updated height/weight/goal, sets `isSaved=true` on success or `saveError` on failure
    - _Requirements: 7.3, 7.4, 7.6_
  - [ ]* 8.7 Write property-based tests for unit conversion functions using kotest-property
    - **Property 8: Height unit conversion round-trip**
    - **Validates: Requirements 5.2, 5.3**
    - **Property 9: Weight unit conversion round-trip**
    - **Validates: Requirements 5.4, 5.6**
  - [ ]* 8.8 Write property-based tests for `isNextEnabled` and answer-setting logic
    - **Property 3: Next is disabled without a required answer**
    - **Validates: Requirements 2.7, 2.8, 5.7**
    - **Property 4: Single-choice selection is mutually exclusive**
    - **Validates: Requirements 3.1, 3.3**
  - [ ]* 8.9 Write property-based tests for multi-select toggle and None logic
    - **Property 5: Multi-select toggle round-trip**
    - **Validates: Requirements 4.1**
    - **Property 6: None option deselects all others**
    - **Validates: Requirements 4.6, 4.9**
    - **Property 7: Non-None selection deselects None**
    - **Validates: Requirements 4.7, 4.10**
  - [ ]* 8.10 Write property-based test for comma-separated serialization round-trip
    - **Property 14: Comma-separated multi-select serialization round-trip**
    - **Validates: Requirements 4.3**

- [x] 9. Implement `QuestionnaireActivity` shell and navigation plumbing
  - Create `QuestionnaireActivity.kt` in `app/src/main/java/com/posemonitor2/questionnaire/` annotated `@AndroidEntryPoint`; read `EXTRA_MODE` from intent; define `firstRunIntent` and `editIntent` companion factory functions; set up camera permission launcher via `ActivityResultContracts.RequestPermission`
  - Declare `QuestionnaireActivity` in `AndroidManifest.xml` with `android:exported="false"` and `android:windowSoftInputMode="adjustResize"`
  - _Requirements: 2.1, 6.2, 14.3_

- [ ] 10. Implement question screen composables
  - [x] 10.1 Create `QuestionnaireFlow.kt` composable that hosts a `HorizontalPager` with 15 pages (14 questions + summary); show `LinearProgressIndicator(progress = currentPage / 14f)` at the top; wire `BackHandler` to call `viewModel.goBack()` (first-run mode: consume on page 0; edit mode: finish on page 0)
    - _Requirements: 2.1, 2.2, 2.3, 2.4, 2.5_
  - [x] 10.2 Create `SingleChoiceQuestionScreen` composable: renders the question title and each option as a selectable `Card` with orange border/tint when selected; "Next" button calls `viewModel.setSingleChoice(page, value)` then `pagerState.animateScrollToPage(page+1)`, enabled only when `viewModel.isNextEnabled(page)`
    - _Requirements: 3.1, 3.2, 3.3_
  - [ ] 10.3 Wire all 8 single-choice screens (Age Group, Gender, Primary Goal, Yoga Experience, Yoga Style, Flexibility Level, Daily Practice Time, Weekly Frequency) as instances of `SingleChoiceQuestionScreen` with their specified option lists from Requirements 3.4
    - _Requirements: 3.4_
  - [x] 10.4 Create `MultiSelectQuestionScreen` composable: renders options as toggleable `Card` items with checkbox icon; tapping toggles via `viewModel.toggleMultiSelectOption(page, option)`; "Next" enabled when at least 1 option selected
    - _Requirements: 4.1, 4.2_
  - [ ] 10.5 Wire Screen 7 (Focus Areas), Screen 12 (Injuries), and Screen 13 (Medical Conditions) as instances of `MultiSelectQuestionScreen` with their specified option lists from Requirements 4.4–4.10
    - _Requirements: 4.4, 4.5, 4.8_
  - [x] 10.6 Create `HeightInputScreen` composable: `OutlinedTextField` with `KeyboardType.Number`; `SegmentedButton` or `Row` toggle between `cm` and `ft/in`; on toggle call `viewModel.convertHeightUnit(newUnit)`; "Next" button enabled only when `isNextEnabled(7)`
    - _Requirements: 5.1, 5.2, 5.7_
  - [x] 10.7 Create `WeightInputScreen` composable: same pattern as height but for `kg`/`lb` toggle; bind to `viewModel.setWeight`
    - _Requirements: 5.4, 5.5, 5.7_
  - [ ] 10.8 Create `CameraPermissionScreen` composable with two option cards ("Enable Pose Correction" and "Continue Without Pose Correction"); "Enable" triggers the camera permission launcher passed down from `QuestionnaireActivity`; the page advances once `cameraPermissionGranted` is non-null
    - _Requirements: 6.1, 6.2, 6.3, 6.4, 6.5, 6.6_
  - [ ] 10.9 Create `QuestionnaireSummaryScreen` composable (page 14): displays a `Card` with the user's goal, experience level, focus areas, and daily practice time; "Start Your Journey" button calls `viewModel.submitAnswers(userId)` and is disabled while `isLoading = true`
    - _Requirements: 7.1, 7.2, 7.3_
  - [ ]* 10.10 Write property-based test for progress indicator fraction
    - **Property 1: Progress indicator value matches page index**
    - **Validates: Requirements 2.2**
  - [ ]* 10.11 Write property-based test for Back navigation decrement
    - **Property 2: Back navigation decrements page**
    - **Validates: Requirements 2.3**
  - [ ]* 10.12 Write property-based test for summary screen rendering
    - **Property 11: Summary screen contains all four required fields**
    - **Validates: Requirements 7.1**

- [ ] 11. Checkpoint — Ensure all ViewModel and composable unit tests pass
  - Ensure all tests pass, ask the user if questions arise.

- [ ] 12. Apply theme and styling to the questionnaire screens
  - Apply `#0E1F20` as `Scaffold`/`Surface` background colour throughout `QuestionnaireActivity`
  - Apply `#173436` as the `Card` container colour for all question option cards and the summary card
  - Apply `#E8622E` to the progress bar tint, selected option border, and "Next"/"Start Your Journey" button
  - Add `imePadding()` to the `Column` containing height and weight input screens
  - _Requirements: 14.1, 14.2, 14.3_

- [ ] 13. Wire `submitAnswers` result to navigation
  - In `QuestionnaireActivity`, observe `viewModel.uiState.isSaved`; when `true` in first-run mode navigate to `UserFlowActivity` with `FLAG_ACTIVITY_NEW_TASK or FLAG_ACTIVITY_CLEAR_TASK` and call `finish()`; in edit mode just call `finish()`
  - Observe `viewModel.uiState.saveError`; when non-null show a `Snackbar` with a Retry action that calls `viewModel.submitAnswers(userId)` again
  - _Requirements: 7.5, 7.6_

- [x] 14. Modify `LoginActivity` to route through questionnaire
  - Inject `OnboardingRepository` and `AuthRepository` into `LoginActivity` (or read from `authViewModel.getCurrentUserId()`)
  - Change the `onAuthenticated` lambda to launch a coroutine: call `onboardingRepository.isCompleted(userId)`; wrap in `try/catch` so any failure defaults to `false`; start either `QuestionnaireActivity.firstRunIntent` or `UserFlowActivity` intent with the `FLAG_ACTIVITY_NEW_TASK or FLAG_ACTIVITY_CLEAR_TASK` flags
  - Show a loading indicator while the check runs (set a local `isCheckingOnboarding` state variable)
  - _Requirements: 1.1, 1.2, 1.3, 1.4, 1.5_

- [ ] 15. Add "Edit Questionnaire" entry point and questionnaire summary to `ProfileScreen`
  - [ ] 15.1 Extend `ProfileViewModel` to observe `OnboardingRepository.observeAnswers(userId)` and expose a `questionnaireSummary: QuestionnaireSummary?` in `ProfileUiState` (containing goal, experienceLevel, focusAreas as display strings)
    - _Requirements: 12.1, 12.2_
  - [ ] 15.2 Add a "Your Yoga Profile" `Card` to `ProfileScreen` above the "App Settings" card; show goal, experience level, and focus areas from `questionnaireSummary`; display "Not set" when the value is null
    - _Requirements: 12.1, 12.2, 12.3_
  - [ ] 15.3 Add an "Edit Questionnaire" `SettingsRow`-style row inside the "App Settings" card (after the existing "Theme" row); tapping it launches `QuestionnaireActivity.editIntent(context)` via `startActivity`
    - _Requirements: 11.1, 11.2_
  - [ ]* 15.4 Write property-based test for profile summary rendering
    - **Property 11 (profile): Summary screen contains all required fields**
    - **Validates: Requirements 12.1**

- [ ] 16. Implement edit mode pre-population in `QuestionnaireViewModel`
  - In `QuestionnaireActivity.onCreate`, when `mode == MODE_EDIT` call `viewModel.loadExistingAnswers(userId)` inside a `LaunchedEffect`
  - Ensure each screen composable reads its initial selected value from `viewModel.uiState.answers` so pre-populated answers are visible on page load
  - _Requirements: 11.2, 11.3_

- [ ] 17. Write mapper property-based tests
  - [ ]* 17.1 Write property-based test for `OnboardingResponse` serialization round-trip
    - **Property 13: OnboardingResponse serialization round-trip**
    - **Validates: Requirements 10.1**
  - [ ]* 17.2 Write property-based test that verifies `submitAnswers` propagates height/weight/goal to `ProfileRepository`
    - **Property 12: Saving answers updates ProfileEntity height, weight, and goal**
    - **Validates: Requirements 7.4**

- [ ] 18. Final checkpoint — Ensure all tests pass and the complete feature is wired end-to-end
  - Verify `LoginActivity` routes to `QuestionnaireActivity` when `is_completed = false` and to `UserFlowActivity` when `is_completed = true`
  - Verify `QuestionnaireActivity` in first-run mode finishes to `UserFlowActivity` after successful submit
  - Verify `QuestionnaireActivity` in edit mode finishes back to `ProfileScreen` after submit
  - Verify `FirebaseSyncRunner.syncPending()` does not throw on `onboarding_responses` entityType
  - Ensure all tests pass, ask the user if questions arise.

## Notes

- Tasks marked with `*` are optional and can be skipped for a faster MVP; the questionnaire will still function correctly without them.
- Property tests require adding `io.kotest:kotest-property:5.9.1` to `testImplementation` in `app/build.gradle.kts` and `data/build.gradle.kts`.
- `QuestionnaireActivity` must be declared in `AndroidManifest.xml` before the migration tasks are tested on device.
- The existing `CAMERA` permission declaration in `AndroidManifest.xml` is already present — no manifest change needed for Screen 14.
- Room's `exportSchema = false` is already set in `PoseDatabase`; no schema export file management is required.
- All questionnaire-related Kotlin files go in the new package `com.posemonitor2.questionnaire` inside the `app` module.

## Task Dependency Graph

```json
{
  "waves": [
    { "wave": 1, "tasks": ["1"] },
    { "wave": 2, "tasks": ["2"] },
    { "wave": 3, "tasks": ["3"] },
    { "wave": 4, "tasks": ["4"] },
    { "wave": 5, "tasks": ["5"] },
    { "wave": 6, "tasks": ["6"] },
    { "wave": 7, "tasks": ["7"] },
    { "wave": 8, "tasks": ["8"] },
    { "wave": 9, "tasks": ["9"] },
    { "wave": 10, "tasks": ["10"] },
    { "wave": 11, "tasks": ["11"] },
    { "wave": 12, "tasks": ["12"] },
    { "wave": 13, "tasks": ["13"] },
    { "wave": 14, "tasks": ["14"] },
    { "wave": 15, "tasks": ["15"] },
    { "wave": 16, "tasks": ["16"] },
    { "wave": 17, "tasks": ["17"] },
    { "wave": 18, "tasks": ["18"] }
  ]
}
```
