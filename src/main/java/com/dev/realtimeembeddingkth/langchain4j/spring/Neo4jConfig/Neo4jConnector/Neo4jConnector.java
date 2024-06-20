package com.dev.realtimeembeddingkth.langchain4j.spring.Neo4jConfig.Neo4jConnector;

import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.neo4j.driver.Session;
import org.neo4j.driver.Result;
import org.neo4j.driver.exceptions.ServiceUnavailableException;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class Neo4jConnector {

    private Driver driver;

    // Method to connect to the Neo4j database
    public boolean connect(String uri, String user, String password) {
        try {
            driver = GraphDatabase.driver(uri, AuthTokens.basic(user, password));
            // Test the connection
            driver.verifyConnectivity();
            return true;
        } catch (ServiceUnavailableException e) {
            e.printStackTrace();
            return false;
        }
    }

    // Method to create a new database with a username and password
    public void createDatabase(String databaseName, String user, String password) {
        try (Session session = driver.session()) {
            String createDatabaseQuery = String.format("CREATE DATABASE %s", databaseName);
            session.run(createDatabaseQuery);

            // Optionally, you can create users and assign roles
            String createUserQuery = String.format("CREATE USER %s SET PASSWORD '%s'", user, password);
            session.run(createUserQuery);

            String assignRoleQuery = String.format("GRANT ROLE admin TO %s", user);
            session.run(assignRoleQuery);

            System.out.println("Database and user created successfully");
        }
    }

    // Method to list all databases in the Neo4j DBMS
    public List<String> listDatabases() {
        List<String> databases = new ArrayList<>();
        try (Session session = driver.session()) {
            String listDatabasesQuery = "SHOW DATABASES";
            Result result = session.run(listDatabasesQuery);

            result.list().forEach(record -> {
                databases.add(record.get("name").asString());
            });
        }
        return databases;
    }

    // Method to check if any Neo4j instance is running
    public boolean isNeo4jRunning() {
        try {
            if (driver != null) {
                driver.verifyConnectivity();
                return true;
            }
        } catch (ServiceUnavailableException e) {
            // Instance is not running
        }
        return false;
    }

    // Method to close the driver
    public void close() {
        if (driver != null) {
            driver.close();
        }
    }

    // Method to load a database from a .dump file
    public void loadDatabaseFromDump(String databaseName, File dumpFile) throws IOException, InterruptedException {
        String neo4jHome = System.getenv("NEO4J_HOME");
        if (neo4jHome == null) {
            throw new IllegalStateException("NEO4J_HOME environment variable is not set");
        }

        String command = String.format("%s/bin/neo4j-admin load --from=%s --database=%s --force",
                neo4jHome, dumpFile.getAbsolutePath(), databaseName);

        Process process = Runtime.getRuntime().exec(command);
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new RuntimeException("Failed to load database from dump file. Exit code: " + exitCode);
        }
    }
}



