package com.rackaudit.app

import android.app.Application
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.rackaudit.app.data.AssignmentRepository
import com.rackaudit.app.data.AuthRepository
import com.rackaudit.app.data.FirebaseAssignmentRepository

/** Hand-rolled DI: two objects don't justify Hilt. */
class AppGraph {
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    val authRepository = AuthRepository(auth, db)
    val assignments: AssignmentRepository = FirebaseAssignmentRepository(auth, db)
}

class RackApp : Application() {
    lateinit var graph: AppGraph
        private set

    override fun onCreate() {
        super.onCreate()
        graph = AppGraph()
    }
}
