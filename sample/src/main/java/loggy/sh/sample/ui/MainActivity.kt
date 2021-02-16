package loggy.sh.sample.ui

import android.os.Bundle
import android.text.Html
import android.view.LayoutInflater
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import loggy.sh.Loggy
import loggy.sh.sample.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default)
    private val viewModel: MainViewModel by viewModels()
    lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(LayoutInflater.from(this))
        setContentView(binding.root)

        scope.launch {
            val url = Loggy.loggyDeviceUrl()
            scope.launch(Dispatchers.Main) {
                binding.url.text = url
            }
        }
        viewModel.intentions.observe(this, {
            binding.firstFragmentContainer.isVisible = it == ViewIntention.GoToFirst
            binding.secondFragmentContainer.isVisible = it == ViewIntention.GoToSecond
            binding.thirdFragmentContainer.isVisible = it == ViewIntention.GoToThird
        })
    }

}