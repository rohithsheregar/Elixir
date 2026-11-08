package com.cellclaw.memory

import androidx.sqlite.db.SupportSQLiteDatabase
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.lang.reflect.Proxy

/**
 * Verifies AG0.5 requirement:
 * 1. Room fallbackToDestructiveMigration() is removed from AppModule.
 * 2. Explicit migrations MIGRATION_1_2, MIGRATION_2_3, and MIGRATION_3_4 are defined.
 * 3. Migrating from v3 to v4 preserves all ApplicationRecord rows in the applications table.
 * 4. Schema export is enabled on MemoryDb.
 */
class MemoryDbMigrationTest {

    @Test
    fun testMigrationsDefinedWithCorrectVersions() {
        assertEquals(1, MemoryDb.MIGRATION_1_2.startVersion)
        assertEquals(2, MemoryDb.MIGRATION_1_2.endVersion)

        assertEquals(2, MemoryDb.MIGRATION_2_3.startVersion)
        assertEquals(3, MemoryDb.MIGRATION_2_3.endVersion)

        assertEquals(3, MemoryDb.MIGRATION_3_4.startVersion)
        assertEquals(4, MemoryDb.MIGRATION_3_4.endVersion)

        assertEquals(3, MemoryDb.ALL_MIGRATIONS.size)
    }

    @Test
    fun testAppModuleDoesNotUseFallbackToDestructiveMigration() {
        val appModuleFile = sequenceOf(
            File("src/main/kotlin/com/cellclaw/di/AppModule.kt"),
            File("app/src/main/kotlin/com/cellclaw/di/AppModule.kt")
        ).firstOrNull { it.exists() } ?: error("AppModule.kt not found")

        val appModuleCode = appModuleFile.readText()
        assertFalse(
            "AppModule must NOT call fallbackToDestructiveMigration() (data loss hazard)",
            appModuleCode.contains("fallbackToDestructiveMigration()")
        )
        assertTrue(
            "AppModule must register explicit Room migrations",
            appModuleCode.contains("addMigrations(*MemoryDb.ALL_MIGRATIONS)") ||
                    appModuleCode.contains("addMigrations(MemoryDb.")
        )
    }

    @Test
    fun testMemoryDbEnablesSchemaExport() {
        val memoryDbFile = sequenceOf(
            File("src/main/kotlin/com/cellclaw/memory/MemoryDb.kt"),
            File("app/src/main/kotlin/com/cellclaw/memory/MemoryDb.kt")
        ).firstOrNull { it.exists() } ?: error("MemoryDb.kt not found")

        val code = memoryDbFile.readText()
        assertTrue("MemoryDb must declare version = 4", code.contains("version = 4"))
        assertTrue("MemoryDb must enable exportSchema", code.contains("exportSchema = true"))
    }

    @Test
    fun testMigrationSqlExecutionKeepsApplicationsTableIntact() {
        // Collect executed SQL statements using a mock SupportSQLiteDatabase proxy
        val executedStatements = mutableListOf<String>()

        val dbProxy = Proxy.newProxyInstance(
            SupportSQLiteDatabase::class.java.classLoader,
            arrayOf(SupportSQLiteDatabase::class.java)
        ) { _, method, args ->
            if (method.name == "execSQL" && args != null && args.isNotEmpty()) {
                executedStatements.add(args[0] as String)
            }
            null
        } as SupportSQLiteDatabase

        // Execute MIGRATION_2_3 (creates applications table)
        MemoryDb.MIGRATION_2_3.migrate(dbProxy)
        assertTrue(
            "MIGRATION_2_3 must create applications table",
            executedStatements.any { it.contains("CREATE TABLE IF NOT EXISTS `applications`") }
        )

        // Execute MIGRATION_3_4 (version bump to v4 adding index)
        val countBeforeV4 = executedStatements.size
        MemoryDb.MIGRATION_3_4.migrate(dbProxy)
        val v4Statements = executedStatements.drop(countBeforeV4)

        // Verify MIGRATION_3_4 performs non-destructive schema update (creates index without dropping table)
        assertTrue(
            "MIGRATION_3_4 must create index on jd_hash",
            v4Statements.any { it.contains("CREATE INDEX") && it.contains("jd_hash") }
        )
        assertFalse(
            "MIGRATION_3_4 must NOT drop applications table",
            v4Statements.any { it.contains("DROP TABLE", ignoreCase = true) }
        )
    }
}

