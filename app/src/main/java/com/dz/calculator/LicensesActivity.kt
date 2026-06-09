package com.dz.calculator

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.TypedValue
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.dz.calculator.databinding.ActivityLicensesBinding
import com.dz.calculator.licenses.License
import com.dz.calculator.licenses.LicenseActionListener
import com.dz.calculator.licenses.LicenseAdapter
import com.dz.calculator.settings.Config
import com.dz.calculator.settings.Preferences
import com.dz.calculator.utils.InteractionAndroid
import kotlin.properties.Delegates.notNull

class LicensesActivity : AppCompatActivity() {

    private var binding: ActivityLicensesBinding by notNull()
    private var preferences: Preferences by notNull()
    private var adapter: LicenseAdapter by notNull()

    @SuppressLint("DiscouragedApi")
    override fun onCreate(savedInstanceState: Bundle?) {
        preferences = Preferences(this)

        when (preferences.getTheme()) {
            -1 -> {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
            }
            1 -> {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            }
            2 -> {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            }
        }

        if (!Config.isDynamicColor) {
            setTheme(resources.getIdentifier(Config.color, "style", packageName))
        } else {
            setTheme(R.style.dynamicColors)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                com.google.android.material.color.DynamicColors.applyToActivityIfAvailable(this)
            }
        }

        super.onCreate(savedInstanceState)
        binding = ActivityLicensesBinding.inflate(layoutInflater).also { setContentView(it.root) }


        adapter = LicenseAdapter(this, object : LicenseActionListener {
            override fun onSelectLicense(license: License) {
                InteractionAndroid.openUrl(license.url, this@LicensesActivity)
            }
        })

        val layoutManager = LinearLayoutManager(this)
        binding.recyclerView.layoutManager = layoutManager
        binding.recyclerView.adapter = adapter


        binding.topAppBar.setNavigationOnClickListener {
            finish()
        }
    }
}