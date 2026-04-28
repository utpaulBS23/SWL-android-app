package com.phq.swl.pioms

import android.app.Application
import com.phq.swl.pioms.data.ObjectBoxStore
import com.phq.swl.pioms.di.AppModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import org.koin.ksp.generated.module

class MainApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@MainApplication)
            modules(AppModule().module)
        }
        ObjectBoxStore.init(this)
    }
}
