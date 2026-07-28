package com.familyconnect.app.ui.roleselection

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.familyconnect.app.data.model.UserRole
import com.familyconnect.app.data.local.AppDatabase
import com.familyconnect.app.data.remote.FirebaseSource
import com.familyconnect.app.data.repository.AppRepository
import kotlinx.coroutines.launch
import timber.log.Timber

class RoleSelectionViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: AppRepository
    private val firebaseSource = FirebaseSource()

    init {
        val context = application.applicationContext
        val database = AppDatabase.getInstance(context)
        val prefs = context.getSharedPreferences("family_connect_prefs", Application.MODE_PRIVATE)
        repository = AppRepository(database, firebaseSource, prefs)
    }

    fun selectRole(role: UserRole) {
        viewModelScope.launch {
            try {
                firebaseSource.ensureSignedIn()
            } catch (e: Exception) {
                Timber.e(e, "Failed to sign in anonymously")
            }
            repository.setUserRole(role)
        }
    }
}
