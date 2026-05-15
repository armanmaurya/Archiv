package com.armanmaurya.archiv.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import com.armanmaurya.archiv.data.local.dao.DocumentDao
import com.armanmaurya.archiv.data.local.entities.DocumentEntity

@Database(
	entities = [DocumentEntity::class],
	version = 1
)
abstract class ArchivDatabase : RoomDatabase() {

	abstract fun documentDao(): DocumentDao

	companion object {
		@Volatile
		private var instance: ArchivDatabase? = null

		private val migrations: Array<Migration> = emptyArray()

		fun getInstance(context: Context): ArchivDatabase {
			return instance ?: synchronized(this) {
				instance ?: Room.databaseBuilder(
					context.applicationContext,
					ArchivDatabase::class.java,
					"archiv.db"
				)
					.addMigrations(*migrations)
					.build()
					.also { instance = it }
			}
		}
	}
}
