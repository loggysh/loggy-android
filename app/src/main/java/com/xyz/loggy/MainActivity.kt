package com.xyz.loggy

import android.os.Bundle
import android.view.LayoutInflater
import androidx.appcompat.app.AppCompatActivity
import com.xyz.loggy.databinding.ActivityMainBinding
import timber.log.Timber
import kotlin.concurrent.fixedRateTimer

class MainActivity : AppCompatActivity() {

    lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(LayoutInflater.from(this))
        setContentView(binding.root)

        fixedRateTimer("hello", false, 0, 1000) {
            sendMessage()
        }
    }

    private fun sendMessage() {
        Timber.d("Hello world!!")
    }
}