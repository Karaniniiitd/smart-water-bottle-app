package com.tamim.hydrationtracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.tamim.hydrationtracker.ble.BottleSensorGateway
import com.tamim.hydrationtracker.ble.FakeBleManager
import com.tamim.hydrationtracker.ble.RealBottleSensorGateway
import com.tamim.hydrationtracker.ble.SensorMode
import com.tamim.hydrationtracker.data.local.AppDatabase
import com.tamim.hydrationtracker.data.repo.HydrationRepository
import com.tamim.hydrationtracker.ui.HydrationApp
import com.tamim.hydrationtracker.ui.HydrationViewModel
import com.tamim.hydrationtracker.ui.theme.HydrationTrackerTheme

class MainActivity : ComponentActivity() {

    private val sensorMode: SensorMode = SensorMode.REAL_BOTTLE

    private val sensorGateway: BottleSensorGateway by lazy {
        when (sensorMode) {
            SensorMode.SIMULATION -> FakeBleManager()
            SensorMode.REAL_BOTTLE -> RealBottleSensorGateway(applicationContext)
        }
    }

    private val viewModel: HydrationViewModel by viewModels {
        val repository = HydrationRepository(
            context = applicationContext,
            hydrationDao = AppDatabase.getInstance(applicationContext).hydrationDao()
        )
        HydrationViewModel.factory(
            app = application,
            repository = repository,
            sensorGateway = sensorGateway
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            HydrationTrackerTheme {
                HydrationApp(viewModel = viewModel)
            }
        }
    }
}
