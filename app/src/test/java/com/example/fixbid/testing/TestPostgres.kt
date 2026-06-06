package com.example.fixbid.testing

import org.testcontainers.containers.PostgreSQLContainer
import java.io.File
import java.sql.Connection
import java.sql.DriverManager

/**
 * Shared Postgres testcontainer + migration applier for FixBid SQL/RPC
 * property-based tests.
 *
 * Test classes obtain a [Connection] via [TestPostgres.connection] in their
 * `@BeforeContainer` / setup hook. The container is started lazily on first
 * use and stopped automatically on JVM exit, so it amortises across all PBT
 * properties in a test run.
 *
 * The bootstrap order is:
 *   1. `sql/test_bootstrap.sql`                           (auth.users, bookings, payments stubs)
 *   2. `supabase/migrations/20260603_wallets.sql`         (wallets, wallet_transactions, hold/release RPCs)
 *   3. `supabase/migrations/0007_refund_escrow_to_customer.sql` (refund RPC + index swap)
 */
internal object TestPostgres {

    private val container: PostgreSQLContainer<*> by lazy {
        PostgreSQLContainer("postgres:16-alpine")
            .withDatabaseName("fixbid_test")
            .withUsername("test")
            .withPassword("test")
            .also { c ->
                c.start()
                Runtime.getRuntime().addShutdownHook(Thread { runCatching { c.stop() } })
                applyBootstrapAndMigrations(c)
            }
    }

    /**
     * Returns a fresh JDBC connection backed by the (singleton) container.
     * Each call opens a new connection so transactions in one test don't bleed
     * into another. The caller is responsible for closing it (use Kotlin's
     * `use { ... }` for safety).
     */
    fun connection(): Connection {
        // Ensure container init has run.
        container
        return DriverManager.getConnection(container.jdbcUrl, container.username, container.password)
    }

    /**
     * Truncates every mutable table so each property iteration starts with a
     * clean ledger. Schema and functions stay in place. Cheap on a small DB.
     */
    fun truncateAll() {
        connection().use { conn ->
            conn.createStatement().use { stmt ->
                stmt.execute(
                    """
                    truncate table public.wallet_transactions restart identity cascade;
                    truncate table public.wallets             restart identity cascade;
                    truncate table public.payments            restart identity cascade;
                    truncate table public.bookings            restart identity cascade;
                    truncate table auth.users                 restart identity cascade;
                    """.trimIndent()
                )
            }
        }
    }

    private fun applyBootstrapAndMigrations(c: PostgreSQLContainer<*>) {
        DriverManager.getConnection(c.jdbcUrl, c.username, c.password).use { conn ->
            execScript(conn, loadResource("sql/test_bootstrap.sql"))
            execScript(conn, loadProjectFile("supabase/migrations/20260603_wallets.sql"))
            execScript(conn, loadProjectFile("supabase/migrations/0007_refund_escrow_to_customer.sql"))
        }
    }

    private fun execScript(conn: Connection, sql: String) {
        conn.createStatement().use { stmt -> stmt.execute(sql) }
    }

    private fun loadResource(path: String): String =
        TestPostgres::class.java.classLoader!!.getResourceAsStream(path).use { stream ->
            checkNotNull(stream) { "Missing test resource: $path" }
            stream.bufferedReader().readText()
        }

    /**
     * Migrations live under `supabase/migrations/` at the repository root.
     * The unit-test JVM working directory is `app/`, so we walk up one level
     * to find the project root.
     */
    private fun loadProjectFile(relativePath: String): String {
        val cwd = File(System.getProperty("user.dir"))
        val candidates = listOf(
            File(cwd, relativePath),
            File(cwd.parentFile ?: cwd, relativePath),
        )
        val found = candidates.firstOrNull { it.exists() }
            ?: error("Could not locate $relativePath. Tried: ${candidates.joinToString { it.absolutePath }}")
        return found.readText()
    }
}
