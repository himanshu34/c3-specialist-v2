package co.nayan.c3specialist_v2.config

import androidx.appcompat.app.AppCompatActivity
import co.nayan.c3specialist_v2.splash.SplashActivity
import co.nayan.c3v2.login.config.LoginConfig
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LoginConfigImpl @Inject constructor() : LoginConfig {

    override fun mainActivityClass(): Class<out AppCompatActivity> {
        return SplashActivity::class.java
    }
}