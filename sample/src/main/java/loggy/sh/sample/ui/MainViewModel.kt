package loggy.sh.sample.ui

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import timber.log.Timber

sealed class ViewIntention {
    object GoToFirst : ViewIntention()
    object GoToSecond : ViewIntention()
    object GoToThird : ViewIntention()
}

class MainViewModel : ViewModel() {

    val intentions = MutableLiveData<ViewIntention>()

    fun interpret(intention: ViewIntention) {
        intentions.value = intention
        Timber.d("$intention")
    }
}