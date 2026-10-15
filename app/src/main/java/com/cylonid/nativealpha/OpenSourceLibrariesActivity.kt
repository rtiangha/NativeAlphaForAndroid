package com.cylonid.nativealpha

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.stringResource
import com.mikepenz.aboutlibraries.ui.compose.android.produceLibraries
import com.mikepenz.aboutlibraries.ui.compose.m3.LibrariesContainer

/**
 * Lists the open source libraries used by the app, using the AboutLibraries Compose UI.
 * The data comes from res/raw/aboutlibraries.json, which the AboutLibraries Gradle plugin generates at build time.
 */
class OpenSourceLibrariesActivity : AppCompatActivity() {

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContentView(ComposeView(this).apply {
            setContent {
                MaterialTheme(
                    colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()
                ) {
                    val libraries by produceLibraries(R.raw.aboutlibraries)

                    Scaffold(
                        topBar = {
                            TopAppBar(
                                title = { Text(stringResource(R.string.open_source_libs)) },
                                navigationIcon = {
                                    IconButton(onClick = { finish() }) {
                                        Icon(
                                            Icons.AutoMirrored.Filled.ArrowBack,
                                            contentDescription = null
                                        )
                                    }
                                }
                            )
                        }
                    ) { padding ->
                        LibrariesContainer(
                            libraries,
                            Modifier
                                .fillMaxSize()
                                .padding(padding)
                        )
                    }
                }
            }
        })
    }
}
