package com.xyz.loggy.ui

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

sealed class ViewIntention {
    object GoToFirst : ViewIntention()
    object GoToSecond : ViewIntention()
    object GoToThird : ViewIntention()
}

class MainViewModel : ViewModel() {

    val intentions = MutableLiveData<ViewIntention>()

    fun interpret(intention: ViewIntention) {
        intentions.value = intention
    }
}