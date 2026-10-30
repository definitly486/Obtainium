package dev.imranr.obtainium.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.imranr.obtainium.data.AppRepository
import dev.imranr.obtainium.data.TrackedApp
import dev.imranr.obtainium.install.ApkInstaller
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ObtainiumViewModel(app: Application) : AndroidViewModel(app) {
 private val repository = AppRepository(app)
 private val installer = ApkInstaller(app)
 val apps = repository.apps.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
 val githubToken = repository.githubToken.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")

 fun saveGitHubToken(token: String) {
  viewModelScope.launch { repository.setGitHubToken(token) }
 }

 init {
  viewModelScope.launch { repository.ensureDefaultApps() }
 }

 fun add(url: String, done: (Result<TrackedApp>) -> Unit) {
  viewModelScope.launch { done(repository.add(url)) }
 }
 fun remove(id: String) { viewModelScope.launch { repository.remove(id) } }
 fun check(id: String, done: (Result<TrackedApp>) -> Unit = {}) {
  viewModelScope.launch { done(repository.check(id)) }
 }
 fun checkAll() { viewModelScope.launch { repository.checkAll() } }
 fun install(app: TrackedApp, done: (Result<Unit>) -> Unit) {
  viewModelScope.launch { done(runCatching { installer.downloadAndInstall(app, repository.githubToken.first()) }) }
 }
}
