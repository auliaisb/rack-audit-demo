package com.rackaudit.app.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rackaudit.app.R
import com.rackaudit.app.data.AuthRepository
import com.rackaudit.app.data.SignInResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LoginState(
    val email: String = "",
    val password: String = "",
    val busy: Boolean = false,
    val errorRes: Int? = null,
    val signedIn: Boolean = false,
)

class LoginViewModel(private val auth: AuthRepository) : ViewModel() {
    private val _state = MutableStateFlow(LoginState(signedIn = auth.isSignedIn))
    val state: StateFlow<LoginState> = _state.asStateFlow()

    fun onEmail(v: String) = _state.update { it.copy(email = v, errorRes = null) }
    fun onPassword(v: String) = _state.update { it.copy(password = v, errorRes = null) }

    fun signIn() {
        val s = _state.value
        if (s.busy || s.email.isBlank() || s.password.isEmpty()) return
        _state.update { it.copy(busy = true, errorRes = null) }
        viewModelScope.launch {
            val result = auth.signIn(s.email, s.password)
            _state.update {
                when (result) {
                    SignInResult.OK -> it.copy(busy = false, signedIn = true, password = "")
                    SignInResult.BAD_CREDENTIALS -> it.copy(busy = false, errorRes = R.string.login_error_credentials)
                    SignInResult.NETWORK -> it.copy(busy = false, errorRes = R.string.login_error_network)
                    SignInResult.NOT_SALES -> it.copy(busy = false, errorRes = R.string.login_error_not_sales)
                    SignInResult.OTHER -> it.copy(busy = false, errorRes = R.string.login_error_generic)
                }
            }
        }
    }

    fun signOut() {
        auth.signOut()
        _state.update { LoginState() }
    }
}
