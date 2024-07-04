package com.dev.realtimeembeddingkth.langchain4j.spring.Neo4jConfig.Neo4jService;

import com.dev.realtimeembeddingkth.langchain4j.spring.Neo4jConfig.Neo4jConnector.Neo4jConnector;
import org.neo4j.driver.*;
import org.neo4j.driver.Record;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.List;

@Service
public class Neo4jService {

    private final Neo4jConnector neo4jConnector = new Neo4jConnector();
    private Driver driver;

    public boolean connect(String uri, String user, String password) {
        boolean connected = neo4jConnector.connect(uri, user, password);
        if (connected) {
            driver = GraphDatabase.driver(uri, AuthTokens.basic(user, password));
        }
        return connected;
    }

    public void createDatabase(String databaseName, String user, String password) {
        neo4jConnector.createDatabase(databaseName, user, password);
    }

    public List<String> listDatabases() {
        return neo4jConnector.listDatabases();
    }

    public boolean isNeo4jRunning() {
        return neo4jConnector.isNeo4jRunning();
    }

    public void createDatabaseFromFile(MultipartFile file) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split(",");
                if (parts.length == 3) {
                    String dbName = parts[0].trim();
                    String user = parts[1].trim();
                    String password = parts[2].trim();
                    neo4jConnector.createDatabase(dbName, user, password);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void loadDatabaseFromDump(String databaseName, String dumpName, MultipartFile dumpFile) throws IOException, InterruptedException {
        File tempFile = File.createTempFile(dumpName, ".dump");
        dumpFile.transferTo(tempFile);
        neo4jConnector.loadDatabaseFromDump(databaseName, tempFile);
        tempFile.delete();
    }

    public boolean deleteDocument(String text) {
        if (driver == null) {
            throw new IllegalStateException("Not connected to Neo4j database");
        }

        try (Session session = driver.session()) {
            String query = "MATCH (d:Document) WHERE d.text CONTAINS $text DETACH DELETE d RETURN COUNT(d) AS count";
            Result result = session.run(query, Values.parameters("text", text));

            if (result.hasNext()) {
                Record record = result.next();
                int count = record.get("count").asInt();
                return count > 0;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }
}
