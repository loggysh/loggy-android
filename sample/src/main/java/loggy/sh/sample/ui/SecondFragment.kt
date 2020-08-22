package loggy.sh.sample.ui

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import loggy.sh.sample.R
import loggy.sh.sample.databinding.MainSecondFragmentBinding

class SecondFragment : Fragment(R.layout.main_second_fragment) {

    private val viewModel: MainViewModel by activityViewModels()
    private lateinit var binding: MainSecondFragmentBinding

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding = MainSecondFragmentBinding.bind(view)

        with(binding) {
            buttonGoToFirst.setOnClickListener {
                viewModel.interpret(ViewIntention.GoToFirst)
            }

            buttonGoToThird.setOnClickListener {
                viewModel.interpret(ViewIntention.GoToThird)
            }
        }
    }
}