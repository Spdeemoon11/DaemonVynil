package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.theme.StudioBackground
import com.example.ui.theme.VinylTheme
import com.example.ui.vinyl.VinylPlayerScreen
import com.example.ui.vinyl.VinylPlayerViewModel

/**
 * MainActivity
 *
 * The single edge-to-edge entry point activity for Vinyl.
 *
 * Responsibilities:
 * - Enables modern edge-to-edge system insets handling.
 * - Instantiates the [VinylPlayerViewModel].
 * - Attaches lifecycle event observers to pause sensor and physics loops when backgrounded,
 *   and instantly re-query active Android MediaSessions when returned to the foreground.
 * - Renders the skeuomorphic turntable interface inside the dark monochromatic [VinylTheme].
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Mandatory edge-to-edge layout for full-bleed studio immersion
        enableEdgeToEdge()

        setContent {
            VinylTheme {
                val viewModel: VinylPlayerViewModel = viewModel()
                val lifecycleOwner = LocalLifecycleOwner.current

                // Lifecycle observer managing battery conservation and session freshness
                DisposableEffect(lifecycleOwner) {
                    val observer = LifecycleEventObserver { _, event ->
                        when (event) {
                            Lifecycle.Event.ON_RESUME -> viewModel.onResume()
                            Lifecycle.Event.ON_PAUSE -> viewModel.onPause()
                            else -> Unit
                        }
                    }
                    lifecycleOwner.lifecycle.addObserver(observer)
                    onDispose {
                        lifecycleOwner.lifecycle.removeObserver(observer)
                    }
                }

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = StudioBackground
                ) {
                    VinylPlayerScreen(viewModel = viewModel)
                }
            }
        }
    }
}
