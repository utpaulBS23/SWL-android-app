package com.phq.swl.pioms.presentation.screens.welcome

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import com.phq.swl.pioms.data.SettingsStore
import org.koin.android.annotation.KoinViewModel

@KoinViewModel
class WelcomeScreenViewModel(
    private val settingsStore: SettingsStore,
) : ViewModel() {
    private val KEY_LOGGED_IN_USER_ID = "logged_in_user_id"

    val userIdState: MutableState<String> =
        mutableStateOf(settingsStore.get(KEY_LOGGED_IN_USER_ID) ?: "")

    fun logout() {
        settingsStore.save(KEY_LOGGED_IN_USER_ID, "")
        userIdState.value = ""
    }
}
