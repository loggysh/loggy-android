package loggy.sh.sample.ui

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import loggy.sh.sample.R
import loggy.sh.sample.databinding.MainThirdFragmentBinding
import timber.log.Timber

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

            nonFatal.setOnClickListener {
                Timber.e(IllegalArgumentException("Failed in Third Fragment"))
            }

            fatal.setOnClickListener {
                throw IllegalArgumentException("fatal")
            }
        }
    }
}