package com.armanmaurya.archiv.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import com.armanmaurya.archiv.data.local.dao.DocumentDao
import com.armanmaurya.archiv.data.local.dao.DocumentTagDao
import com.armanmaurya.archiv.data.local.dao.TagDao
import com.armanmaurya.archiv.data.local.entities.DocumentEntity
import com.armanmaurya.archiv.data.local.entities.DocumentTagEntity
import com.armanmaurya.archiv.data.local.entities.TagEntity

@Database(
	entities = [
		DocumentEntity::class,
		TagEntity::class,
		DocumentTagEntity::class
	],
	version = 2
)
abstract class ArchivDatabase : RoomDatabase() {

	abstract fun documentDao(): DocumentDao
	abstract fun tagDao(): TagDao
	abstract fun documentTagDao(): DocumentTagDao

	companion object {
		@Volatile
		private var instance: ArchivDatabase? = null

		private val migration1To2 = object : Migration(1, 2) {
			override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
				database.execSQL(
					"CREATE TABLE IF NOT EXISTS `tags` (" +
						"`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
						"`name` TEXT NOT NULL" +
					")"
				)
				database.execSQL(
					"CREATE UNIQUE INDEX IF NOT EXISTS `index_tags_name` ON `tags` (`name`)"
				)
				database.execSQL(
					"CREATE TABLE IF NOT EXISTS `document_tags` (" +
						"`documentId` TEXT NOT NULL, " +
						"`tagId` INTEGER NOT NULL, " +
						"PRIMARY KEY(`documentId`, `tagId`), " +
						"FOREIGN KEY(`documentId`) REFERENCES `documents`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE, " +
						"FOREIGN KEY(`tagId`) REFERENCES `tags`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE" +
					")"
				)
				database.execSQL(
					"CREATE INDEX IF NOT EXISTS `index_document_tags_documentId` ON `document_tags` (`documentId`)"
				)
				database.execSQL(
					"CREATE INDEX IF NOT EXISTS `index_document_tags_tagId` ON `document_tags` (`tagId`)"
				)
			}
		}

		private val migrations: Array<Migration> = arrayOf(migration1To2)

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
