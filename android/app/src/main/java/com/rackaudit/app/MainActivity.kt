package com.rackaudit.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.rackaudit.app.ui.detail.DetailScreen
import com.rackaudit.app.ui.detail.DetailViewModel
import com.rackaudit.app.ui.list.ListScreen
import com.rackaudit.app.ui.list.ListViewModel
import com.rackaudit.app.ui.login.LoginScreen
import com.rackaudit.app.ui.login.LoginViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val graph = (application as RackApp).graph
        setContent { MaterialTheme { RackAuditApp(graph) } }
    }
}

@Composable
fun RackAuditApp(graph: AppGraph) {
    val login: LoginViewModel = viewModel(factory = viewModelFactory { initializer { LoginViewModel(graph.authRepository) } })
    val loginState by login.state.collectAsStateWithLifecycle()
    var openId by rememberSaveable { mutableStateOf<String?>(null) }

    when {
        !loginState.signedIn -> LoginScreen(login)
        openId == null -> {
            // Keyed by uid: a different rep signing in on the same phone must never see the previous rep's list.
            val uid = graph.authRepository.currentUid.orEmpty()
            val list: ListViewModel = viewModel(
                key = "list-$uid",
                factory = viewModelFactory { initializer { ListViewModel(graph.assignments) } },
            )
            ListScreen(list, onOpen = { openId = it }, onLogout = { openId = null; login.signOut() })
        }
        else -> {
            val id = openId!!
            val cacheDir = LocalContext.current.cacheDir
            val detail: DetailViewModel = viewModel(
                key = "detail-${graph.authRepository.currentUid}-$id",
                factory = viewModelFactory { initializer { DetailViewModel(id, graph.assignments, cacheDir) } },
            )
            BackHandler { openId = null }
            DetailScreen(detail.model, onBack = { openId = null })
        }
    }
}
