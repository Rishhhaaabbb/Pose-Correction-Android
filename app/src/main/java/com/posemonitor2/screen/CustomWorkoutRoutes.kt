package com.posemonitor2.screen

const val NEW_WORKOUT_ID = "new"
const val CUSTOM_WORKOUT_EDITOR_ROUTE_PATTERN = "custom_workout_editor/{workoutId}"

fun customWorkoutEditorRoute(workoutId: String): String = "custom_workout_editor/$workoutId"
