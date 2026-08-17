package app.gov.uidai.registration

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.gov.uidai.registration.model.CBFingerprint
import app.gov.uidai.registration.model.FingerPosition
import app.gov.uidai.registration.model.SharedUiState
import app.gov.uidai.registration.model.User
import app.gov.uidai.registration.repository.FileRepository
import app.gov.uidai.registration.usecase.CaptureQueueManager
import app.gov.uidai.registration.usecase.UIDManager
import app.gov.uidai.registration.usecase.UserUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import `in`.gov.uidai.embedding.FingerEmbedder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class SharedViewModel @Inject constructor(
    private val userUseCase: UserUseCase,
    private val fileRepository: FileRepository,
    private val uidManager: UIDManager,
    private val fingerEmbedder: FingerEmbedder,
    private val captureQueueManager: CaptureQueueManager
) : ViewModel() {

    private var initJob: Job? = null

    private val _uiState = MutableStateFlow(SharedUiState())
    val uiState = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            captureQueueManager.syncPendingCaptures()
        }
    }

    fun initialize(context: Context) {
        initJob?.cancel()
        initJob = viewModelScope.launch(Dispatchers.Default) {
            try {
                initializeEmbedder(context)
                if (fingerEmbedder.isInitialized) {
                    loadAssets(context)
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(message = "Something went wrong during initialization")
                }
            }
        }
    }

    private suspend fun initializeEmbedder(context: Context) {
        _uiState.update {
            it.copy(isLoadingEmbedder = true)
        }
        try {
            fingerEmbedder.initialize(context)
        } catch (e: Exception) {
            _uiState.update {
                it.copy(message = "Failed to Initialize embedder: $e")
            }
        } finally {
            _uiState.update {
                it.copy(isLoadingEmbedder = false)
            }
        }
    }

    private suspend fun loadAssets(context: Context) {
        _uiState.update {
            it.copy(isLoadingAssets = true)
        }
        try {
            val assetManager = context.assets
            val root = "ContactBasedGallery"
            val rootDir = assetManager.list(root) ?: emptyArray()

            for (folder in rootDir) {
                val children = assetManager.list("$root/$folder") ?: continue
                if (children.isEmpty()) continue
                val uidHash = checkRegistrationAndGetUidHash(folder)
                if (uidHash == null) {
                    Log.i("Assets", "Folder: $folder, Already Registered")
                    continue
                }

                Log.i("Assets", "Folder: $folder, Registering...")
                val fingerprints = mutableListOf<CBFingerprint>()
                for (child in children) {
                    val path = "$root/$folder/$child"
                    Log.i("Assets", path)

                    if (child.endsWith(".jp2", ignoreCase = true)) {
                        val jp2ByteArray = fileRepository.readAsset(path)
                        val (embedding, fingerQuality) = fingerEmbedder.embed(jp2ByteArray)
                        val regex = Regex("(LEFT|RIGHT)[A-Z_]+(?=\\.)")
                        val position = regex.find(child)?.value ?: "LEFT_THUMB"
                        val fingerPosition = FingerPosition.valueOf(position)
                        fingerprints.add(
                            CBFingerprint(
                                fingerPosition = fingerPosition,
                                embedding = embedding,
                                fingerQuality = fingerQuality
                            )
                        )
                    }
                }
                registerUser(uidHash, fingerprints)
            }
        } catch (e: Exception) {
            _uiState.update {
                it.copy(message = "Error loading assets: $e")
            }
        } finally {
            _uiState.update {
                it.copy(isLoadingAssets = false)
            }
        }
    }

    private suspend fun checkRegistrationAndGetUidHash(uid: String): String? =
        withContext(Dispatchers.IO) {
            val uidHash = uidManager.hashUID(uid)
            val isRegistered = userUseCase.isUserRegistered(uidHash)
            if (isRegistered) null else uidHash
        }


    private suspend fun registerUser(
        uidHash: String,
        fingerprints: List<CBFingerprint>
    ) =
        withContext(Dispatchers.IO) {
            userUseCase.register(
                uidHash = uidHash,
                user = User(
                    name = "CB ${uidHash.take(10)}",
                    phoneNumber = "1234567890"
                ),
                fingerprints = fingerprints
            )
        }

    fun clearError() {
        _uiState.update {
            it.copy(
                message = null
            )
        }
    }
}