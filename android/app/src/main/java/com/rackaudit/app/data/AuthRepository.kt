package com.rackaudit.app.data

import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await

enum class SignInResult { OK, BAD_CREDENTIALS, NETWORK, NOT_SALES, OTHER }

class AuthRepository(
    private val auth: FirebaseAuth,
    private val db: FirebaseFirestore,
) {
    val isSignedIn: Boolean get() = auth.currentUser != null
    val currentUid: String? get() = auth.currentUser?.uid

    suspend fun signIn(email: String, password: String): SignInResult {
        try {
            val uid = auth.signInWithEmailAndPassword(email.trim(), password).await().user?.uid
                ?: return SignInResult.OTHER
            val profile = db.collection("users").document(uid).get().await()
            val role = profile.getString("role")
            val active = profile.getBoolean("active") == true
            if (role != "sales" || !active) {
                auth.signOut() // supervisors use the web dashboard; deactivated users are out
                return SignInResult.NOT_SALES
            }
            return SignInResult.OK
        } catch (e: CancellationException) {
            throw e
        } catch (e: FirebaseAuthInvalidCredentialsException) {
            return SignInResult.BAD_CREDENTIALS
        } catch (e: FirebaseAuthInvalidUserException) {
            return SignInResult.BAD_CREDENTIALS // same message: don't reveal which accounts exist
        } catch (e: FirebaseNetworkException) {
            return SignInResult.NETWORK
        } catch (e: Exception) {
            auth.signOut()
            return SignInResult.OTHER
        }
    }

    fun signOut() = auth.signOut()
}
