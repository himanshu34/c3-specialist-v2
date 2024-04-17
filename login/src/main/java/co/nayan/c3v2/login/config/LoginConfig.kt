package co.nayan.c3v2.login.config

import androidx.appcompat.app.AppCompatActivity

interface LoginConfig {
    /**
     * Provide the activity class to be opened on successful login
     */
    fun mainActivityClass(): Class<out AppCompatActivity>
}