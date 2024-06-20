package com.dev.realtimeembeddingkth.langchain4j.spring.Neo4jConfig.Neo4jController;

import com.dev.realtimeembeddingkth.langchain4j.spring.Neo4jConfig.Neo4jService.Neo4jService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/neo4j")
public class Neo4jController {

    @Autowired
    private Neo4jService neo4jService;

    @PostMapping("/connect")
    public boolean connect(@RequestParam String uri, @RequestParam String user, @RequestParam String password) {
        return neo4jService.connect(uri, user, password);
    }

    @PostMapping("/createDatabase")
    public String createDatabase(@RequestParam String databaseName, @RequestParam String user, @RequestParam String password) {
        neo4jService.createDatabase(databaseName, user, password);
        return "Database and user created successfully";
    }

    @GetMapping("/listDatabases")
    public List<String> listDatabases() {
        return neo4jService.listDatabases();
    }

    @GetMapping("/isRunning")
    public boolean isNeo4jRunning() {
        return neo4jService.isNeo4jRunning();
    }

    @PostMapping("/uploadFile")
    public String uploadFile(@RequestParam("file") MultipartFile file) {
        neo4jService.createDatabaseFromFile(file);
        return "Databases created from file successfully";
    }

    @PostMapping("/loadDump")
    public String loadDump(@RequestParam String databaseName,@RequestParam String dumpName, @RequestParam("dumpFile") MultipartFile dumpFile) {
        try {
            neo4jService.loadDatabaseFromDump(databaseName, dumpName,dumpFile);
            return "Database loaded from dump file successfully";
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
            return "Failed to load database from dump file";
        }
    }
}



