package com.batin.tvremote

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * Application entry point. Hilt generates the top-level component graph from here;
 * every [dagger.hilt.android.AndroidEntryPoint] (MainActivity) and every
 * [dagger.hilt.android.qualifiers.ApplicationContext]-scoped dependency ultimately
 * hangs off this class.
 */
@HiltAndroidApp
class TvRemoteApplication : Application()
