package com.samaki.farm.support;

import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.Properties;
import java.util.UUID;

/**
 * Database ya majaribio inayotupwa - inatengenezwa mwanzoni mwa run na
 * kufutwa mwishoni.
 *
 * =====================================================================
 * KWA NINI SI DATABASE YA DEV
 *
 * Majaribio haya yanafuta majedwali kati ya kila test. Yakiendeshwa juu
 * ya `samakiFarm` (database ya maendeleo) yangefuta wahusika na data
 * unayoitumia kwa mkono - na yakiendeshwa juu ya production yangefuta
 * kila kitu. Hivyo hayapewi CHAGUO: jina la database linatengenezwa hapa
 * likianza na `samaki_test_`, na kuna ukaguzi unaokataa kuanza kama jina
 * hilo linafanana na DB_NAME ya mazingira.
 *
 * FLYWAY V1 -> ya mwisho, kila run, kutoka utupu. Hii ndiyo inayofanya
 * migrations kuwa kitu KINACHOJARIBIWA badala ya kitu kinachotumainiwa:
 * kabla ya harness hii, njia ya "database mpya kabisa" haikuwa
 * ikiendeshwa popote isipokuwa siku ya kwanza.
 * =====================================================================
 *
 * SIRI hazimo kwenye source: DB_HOST/DB_PORT/DB_USER/DB_PASSWORD
 * zinasomwa kutoka environment variables, na kama hazipo, kutoka `.env`
 * ya mzizi wa project (ile ile app yenyewe inayoisoma, na ambayo haiingii
 * kwenye git). Ni mtindo ule ule wa application.yml.
 */
public final class TestDatabase {

    private static final String PREFIX = "samaki_test_";

    private static String host;
    private static String port;
    private static String user;
    private static String password;
    private static String databaseName;
    private static boolean created;

    private TestDatabase() {}

    /** Inatengeneza database MARA MOJA kwa kila JVM ya majaribio. */
    public static synchronized void createOnce() {
        if (created) {
            return;
        }
        Properties env = loadEnv();
        host = value(env, "DB_HOST", "localhost");
        port = value(env, "DB_PORT", "5432");
        user = value(env, "DB_USER", "postgres");
        password = value(env, "DB_PASSWORD", "");

        databaseName = PREFIX + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        guardAgainstNonTestDatabase(env);

        execOnMaintenanceDb("CREATE DATABASE \"" + databaseName + "\"");
        created = true;
        System.out.println("[harness] test database CREATED: " + databaseName + " on " + host + ":" + port);

        Runtime.getRuntime().addShutdownHook(new Thread(TestDatabase::drop));
    }

    /**
     * WITH (FORCE) kwa makusudi: pool ya Hikari bado inashikilia
     * connections wakati JVM inafungwa, na bila hii DROP ingeshindwa na
     * kuacha database ya majaribio ikiwa imebaki nyuma kila run.
     * (Inahitaji PostgreSQL 13+.)
     */
    private static void drop() {
        if (!created) {
            return;
        }
        try {
            execOnMaintenanceDb("DROP DATABASE IF EXISTS \"" + databaseName + "\" WITH (FORCE)");
            System.out.println("[harness] test database DROPPED: " + databaseName);
        } catch (RuntimeException e) {
            System.err.println("[harness] IMESHINDWA kufuta " + databaseName + ": " + e.getMessage());
        }
    }

    /**
     * Ulinzi: jina la database ya majaribio LAZIMA lianze na `samaki_test_`
     * na LAZIMA litofautiane na DB_NAME ya mazingira (ya dev au prod).
     * Ni ukaguzi wa kijinga kwa makusudi - gharama yake ni sifuri, na
     * linalozuia ni kufuta database ya kweli.
     */
    private static void guardAgainstNonTestDatabase(Properties env) {
        if (!databaseName.startsWith(PREFIX)) {
            throw new IllegalStateException("Jina la database ya majaribio si salama: " + databaseName);
        }
        String configured = value(env, "DB_NAME", "");
        if (databaseName.equalsIgnoreCase(configured)) {
            throw new IllegalStateException(
                    "Database ya majaribio inafanana na DB_NAME ya mazingira (" + configured + ")");
        }
    }

    private static void execOnMaintenanceDb(String sql) {
        String url = "jdbc:postgresql://" + host + ":" + port + "/postgres";
        try (Connection connection = DriverManager.getConnection(url, user, password);
             Statement statement = connection.createStatement()) {
            statement.execute(sql);
        } catch (Exception e) {
            throw new IllegalStateException("Imeshindwa: " + sql + " -> " + e.getMessage(), e);
        }
    }

    public static String jdbcUrl() {
        return "jdbc:postgresql://" + host + ":" + port + "/" + databaseName;
    }

    public static String username() {
        return user;
    }

    public static String password() {
        return password;
    }

    public static String databaseName() {
        return databaseName;
    }

    /** Environment variables halisi kwanza, kisha `.env` ya mzizi wa project. */
    private static Properties loadEnv() {
        Properties properties = new Properties();
        Path dotEnv = Path.of(".env");
        if (Files.exists(dotEnv)) {
            try (InputStream in = new FileInputStream(dotEnv.toFile())) {
                properties.load(in);
            } catch (Exception e) {
                // Si kosa: environment variables halisi zinaweza kutosha.
                System.out.println("[harness] .env haikusomeka: " + e.getMessage());
            }
        }
        return properties;
    }

    private static String value(Properties env, String key, String fallback) {
        String fromEnvironment = System.getenv(key);
        if (fromEnvironment != null && !fromEnvironment.isBlank()) {
            return fromEnvironment;
        }
        return env.getProperty(key, fallback);
    }
}
