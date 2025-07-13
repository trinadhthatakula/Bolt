package com.valhalla.bolt.di

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import com.valhalla.bolt.model.DocumentHandler
import com.valhalla.bolt.viewModel.BackUpViewModel
import com.valhalla.bolt.viewModel.FlasherViewModel
import com.valhalla.bolt.viewModel.RestoreViewModel
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val appModules = module {
    single<SharedPreferences> {
        androidContext().getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
    }
    singleOf(::DocumentHandler)
    viewModelOf(::FlasherViewModel)
    viewModelOf(::BackUpViewModel)
    viewModelOf(::RestoreViewModel)
}

fun Application.initKoin() {
    startKoin {
        androidContext(this@initKoin)
        androidLogger()
        modules(appModules)
    }
}