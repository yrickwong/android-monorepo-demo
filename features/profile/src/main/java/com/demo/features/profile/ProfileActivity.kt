package com.demo.features.profile

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.demo.bizlibs.user.UserRepository
import com.demo.features.profile.databinding.ProfileActivityBinding
import com.demo.foundations.analytics.Analytics
import com.demo.foundations.common.Result

/**
 * Step 3 of the demo flow — read-only profile screen.
 */
class ProfileActivity : AppCompatActivity() {

    private lateinit var binding: ProfileActivityBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ProfileActivityBinding.inflate(layoutInflater)
        setContentView(binding.root)

        Analytics.logPageView("profile")

        when (val r = UserRepository.loadCurrentUser()) {
            is Result.Success -> {
                binding.content.text = buildString {
                    appendLine("Profile")
                    appendLine("--------")
                    appendLine("userId   : ${r.data.userId}")
                    appendLine("nickname : ${r.data.nickname}")
                    appendLine("bio      : ${r.data.bio}")
                }
            }
            is Result.Failure -> {
                binding.content.text = "load failed: ${r.throwable.message}"
            }
        }
    }
}
