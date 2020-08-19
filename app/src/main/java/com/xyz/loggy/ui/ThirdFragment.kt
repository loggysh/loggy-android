package com.xyz.loggy.ui

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.xyz.loggy.R
import com.xyz.loggy.databinding.MainThirdFragmentBinding

class ThirdFragment : Fragment(R.layout.main_third_fragment) {

    private val viewModel: MainViewModel by activityViewModels()
    private lateinit var binding: MainThirdFragmentBinding

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding = MainThirdFragmentBinding.bind(view)

        with(binding) {
            buttonGoToSecond.setOnClickListener {
                viewModel.interpret(ViewIntention.GoToSecond)
            }

            buttonGoToFirst.setOnClickListener {
                viewModel.interpret(ViewIntention.GoToFirst)
            }
        }
    }
}