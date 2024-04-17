package co.nayan.c3v2.core.utils

import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar

fun AppCompatActivity.setupActionBar(toolbar: Toolbar, homeAsUpEnabled: Boolean = true) {
    setSupportActionBar(toolbar)
    supportActionBar?.setDisplayHomeAsUpEnabled(homeAsUpEnabled)
    toolbar.setNavigationOnClickListener { finish() }
}