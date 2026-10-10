package com.yike.ai.di

import android.content.Context
import androidx.room.Room
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.yike.ai.core.database.AppDatabase
import com.yike.ai.core.database.MIGRATION_1_2
import com.yike.ai.core.database.MIGRATION_2_3
import com.yike.ai.core.document.DocumentParser
import com.yike.ai.core.network.AiClient
import com.yike.ai.core.security.KeyVault
import com.yike.ai.data.repository.*

class AppContainer(context: Context) {
    val db =
        Room.databaseBuilder(context, AppDatabase::class.java, "yike.db")
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
            .build()
    val study = StudyRepository(db)
    val preferences = Preferences(context)
    val vault = KeyVault(context)
    val ai = AiClient()
    val parser = DocumentParser(context)

    init {
        PDFBoxResourceLoader.init(context)
    }
}
