package com.phq.swl.pioms.presentation.screens.add_face

import android.net.Uri
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import com.phq.swl.pioms.domain.AppException
import com.phq.swl.pioms.domain.ImageVectorUseCase
import com.phq.swl.pioms.domain.PersonUseCase
import com.phq.swl.pioms.presentation.components.setProgressDialogText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.android.annotation.KoinViewModel

@KoinViewModel
class AddFaceScreenViewModel(
    private val personUseCase: PersonUseCase,
    private val imageVectorUseCase: ImageVectorUseCase,
) : ViewModel() {
    val personNameState: MutableState<String> = mutableStateOf("")
    val selectedImageURIs: MutableState<List<Uri>> = mutableStateOf(emptyList())

    val isProcessingImages: MutableState<Boolean> = mutableStateOf(false)
    val numImagesProcessed: MutableState<Int> = mutableIntStateOf(0)

    fun addImages() {
        isProcessingImages.value = true
        CoroutineScope(Dispatchers.Default).launch {
            val id =
                personUseCase.addPerson(
                    personNameState.value,
                    selectedImageURIs.value.size.toLong(),
                )
            selectedImageURIs.value.forEach {
                imageVectorUseCase
                    .addImage(id, personNameState.value, it)
                    .onFailure {
                        val errorMessage = (it as AppException).errorCode.message
                        setProgressDialogText(errorMessage)
                    }.onSuccess {
                        numImagesProcessed.value += 1
                        setProgressDialogText("Processed ${numImagesProcessed.value} image(s)")
                    }
            }
            isProcessingImages.value = false
        }
    }
}
