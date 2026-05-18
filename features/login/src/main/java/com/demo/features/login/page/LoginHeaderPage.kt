package com.demo.features.login.page

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.demo.features.login.databinding.LoginPageHeaderBinding
import com.demo.foundations.assemblekit.Page

/**
 * The "no ViewModel" case — a static header showing title + subtitle.
 *
 * Included to demonstrate that not every Page needs Mavericks. State-less
 * Pages stay as cheap as a method call: inflate, bind, done.
 */
internal class LoginHeaderPage : Page() {

    override fun onCreateView(inflater: LayoutInflater, parent: ViewGroup): View =
        LoginPageHeaderBinding.inflate(inflater, parent, false).root
}
