package dev.imranr.obtainium.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.imranr.obtainium.data.AppRepository
import dev.imranr.obtainium.data.TrackedApp
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ObtainiumViewModel(app: Application) : AndroidViewModel(app) {
 private val repository = AppRepository(app)
 val apps = repository.apps.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

 fun add(url: String, done: (Result<TrackedApp>) -> Unit) {
  viewModelScope.launch { done(repository.add(url)) }
 }
 fun remove(id: String) { viewModelScope.launch { repository.remove(id) } }
 fun check(id: String, done: (Result<TrackedApp>) -> Unit = {}) {
  viewModelScope.launch { done(repository.check(id)) }
 }
 fun checkAll() { viewModelScope.launch { repository.checkAll() } }
}
