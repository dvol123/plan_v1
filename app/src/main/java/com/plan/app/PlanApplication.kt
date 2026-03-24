package com.plan.app

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * Application class for the Plan app.
 * Initializes Hilt dependency injection.
 */
@HiltAndroidApp
class PlanApplication : Application()
