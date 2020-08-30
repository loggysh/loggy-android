package loggy.sh.sample.ui

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import loggy.sh.sample.R
import loggy.sh.sample.databinding.MainFirstFragmentBinding
import timber.log.Timber

class FirstFragment : Fragment(R.layout.main_first_fragment) {

    private val viewModel: MainViewModel by activityViewModels()
    private lateinit var binding: MainFirstFragmentBinding

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding = MainFirstFragmentBinding.bind(view)

        with(binding) {
            buttonGoToSecond.setOnClickListener {
                viewModel.interpret(ViewIntention.GoToSecond)
            }

            buttonGoToThird.setOnClickListener {
                viewModel.interpret(ViewIntention.GoToThird)
            }

            nonFatal.setOnClickListener {
                Timber.e(IllegalArgumentException("Failed in First Fragment"))
            }

            fatal.setOnClickListener {
                throw IllegalArgumentException("fatal")
            }
        }
    }
}