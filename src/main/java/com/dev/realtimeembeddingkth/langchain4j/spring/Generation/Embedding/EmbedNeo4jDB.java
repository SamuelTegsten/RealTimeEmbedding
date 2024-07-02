package com.dev.realtimeembeddingkth.langchain4j.spring.Generation.Embedding;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.bge.small.en.v15.BgeSmallEnV15QuantizedEmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import org.neo4j.driver.*;
import org.neo4j.driver.Record;
import org.neo4j.driver.types.Node;
import org.neo4j.driver.types.Relationship;
import org.neo4j.driver.types.TypeSystem;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.dev.realtimeembeddingkth.langchain4j.spring.Generation.Embedding.InitializeNeo4j.initializeNeo4j;
import static dev.langchain4j.data.document.Metadata.metadata;

@Component
public class EmbedNeo4jDB {
    public static void main(String[] args) {
        EmbeddingStore embeddingStore = initializeNeo4j();
        embedNodesWithQuery(embeddingStore, "MATCH (k:Kommun)" +
                "MATCH (ay:Years)" +
                "WHERE ay.year = 2021 " +
                "MATCH (kat:kulturskol_antal_total)" +
                "WHERE kat.value >= 0 " +
                "MATCH (kat)-[:LOCATED_IN]->(k) " +
                "MATCH (kat)-[:YEAR]->(ay) " +
                "RETURN k, kat",
                "List the number of participants in cultural schools for the year 2021 next to its municipality (Kommun). " +
                        "You are looking at data from municipalities (Kommun) and years (Years). " +
                        "I want data specifically for the year 2021.");
    }

    public void executeQuery(String query, String NLPQuestion){
        EmbeddingStore<TextSegment> embeddingStore = initializeNeo4j();
        embedNodesWithQuery(embeddingStore, query, NLPQuestion);
    }

    //Takes any query and embeds the result for both nodes and relationships
    private static void embedNodesWithQuery(EmbeddingStore embeddingStore, String query, String NLPQuestion) {
        EmbeddingModel embeddingModel = new BgeSmallEnV15QuantizedEmbeddingModel();
        Driver driver = GraphDatabase.driver("bolt://localhost:7687", AuthTokens.basic("neo4j", "Password123"));
        Session session = driver.session(SessionConfig.forDatabase("neo4j"));

        List<TextSegment> segments = new ArrayList<>();
        var result = session.run(query);
        while (result.hasNext()) {
            Record record = result.next();
            for (String key : record.keys()) {
                Value value = record.get(key);
                if (value.hasType(TypeSystem.getDefault().NODE())) {
                    Node node = value.asNode();
                    String nodeText = formatNodeText(node);
                    String label = node.labels().iterator().next();
                    TextSegment segment = createTextSegment(nodeText, label);
                    segments.add(segment);
                } else if (value.hasType(TypeSystem.getDefault().RELATIONSHIP())) {
                    Relationship relationship = value.asRelationship();
                    String relationshipText = formatRelationshipText(relationship);
                    TextSegment segment = createTextSegment(relationshipText, relationship.type());
                    segments.add(segment);
                } else if (value.hasType(TypeSystem.getDefault().STRING())) {
                    String stringValue = value.asString();
                    TextSegment segment = createTextSegment(stringValue, "string");
                    segments.add(segment);
                }
            }
        }

        // Embed all segments and concatenate embeddings
        List<Embedding> embeddings = embeddingModel.embedAll(segments).content();
        Embedding combinedEmbedding = combineEmbeddings(embeddings);

        TextSegment textSegment = TextSegment.from(NLPQuestion + " Content: " + segments);
        textSegment = TextSegment.from(cleanTextSegment(String.valueOf(textSegment), NLPQuestion));

        embeddingStore.add(combinedEmbedding, textSegment);

        session.close();
        driver.close();
    }

    private static String cleanTextSegment(String input, String NLPQuestion) {
        // Clean out id: followed by a UUID
        String cleanedText = input.replaceAll("id: [a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12} ,", "");

        // Clean out value: followed by a number
        cleanedText = cleanedText.replaceAll("value: (\\d+) ,", "$1 ,");

        // Remove stray closing curly braces and encapsulate each block in curly braces {}
        cleanedText = encapsulateBlocksInCurlyBraces(cleanedText);

        return NLPQuestion + " " + cleanedText.trim();
    }

    private static String encapsulateBlocksInCurlyBraces(String input) {
        // Pattern to find each block and encapsulate it in curly braces {}, also add Entry for each row in the neo4j table
        Pattern blockPattern = Pattern.compile("\\w+: [^,}]+( , \\w+: [^,}]+)*");
        Matcher matcher = blockPattern.matcher(input);

        String label = extractLabel(input);
        int rows = 0;

        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            String block = matcher.group();
            if(block.contains(label)){
                result.append("} { Entry ").append(rows).append(":").append(" ").append(block).append(" ").append(", ");
                rows++;
            } else {
                result.append(" ").append(block).append(" ").append(", ");
            }
        }

        if (!result.isEmpty()) {
            result.delete(result.length() - 2, result.length());
        }

        return result.toString();
    }

    private static String extractLabel(String input) {
        Pattern pattern = Pattern.compile("text = \"(\\w+):");
        Matcher matcher = pattern.matcher(input);

        if (matcher.find()) {
            return matcher.group(1);
        } else {
            return "";
        }
    }

    private static Map<String, Object> metadataMap(String value) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("label", value);
        return metadata;
    }

    private static TextSegment createTextSegment(String cleanedText, String label) {
        if (cleanedText == null || cleanedText.isBlank()) {
            throw new IllegalArgumentException("Cleaned text cannot be null or blank");
        }
        return TextSegment.from(label + ": " + cleanedText, Metadata.from(metadataMap(label)));
    }

    private static Embedding combineEmbeddings(List<Embedding> embeddings) {
        int length = embeddings.get(0).dimension();
        float[] combinedContent = new float[length];
        for (Embedding embedding : embeddings) {
            float[] content = embedding.vector();
            for (int i = 0; i < length; i++) {
                combinedContent[i] += content[i];
            }
        }
        for (int i = 0; i < length; i++) {
            combinedContent[i] /= embeddings.size();
        }
        return new Embedding(combinedContent);
    }

    // Helper method to format node text for embedding
    private static String formatNodeText(Node node) {
        StringBuilder sb = new StringBuilder();
        node.asMap().forEach((key, value) -> sb.append(key).append(": ").append(value.toString()).append(" ,"));
        return sb.toString();
    }

    // Helper method to format relationship text for embedding (not recommended)
    private static String formatRelationshipText(Relationship relationship) {
        String startNode = relationship.startNodeId() + "";
        String endNode = relationship.endNodeId() + "";
        String type = relationship.type();
        StringBuilder sb = new StringBuilder();
        sb.append("Start Node ID: ").append(startNode).append(", ");
        sb.append("End Node ID: ").append(endNode).append(", ");
        sb.append("Type: ").append(type).append(", ");
        relationship.asMap().forEach((key, value) -> sb.append(key).append(": ").append(value).append(", "));
        return sb.toString();
    }

    // Method to embed the entire database
    private static void EmbedDB(EmbeddingStore embeddingStore) {
        EmbeddingModel embeddingModel = new BgeSmallEnV15QuantizedEmbeddingModel();

        Driver driver = GraphDatabase.driver("bolt://localhost:7687", AuthTokens.basic("neo4j", "Password123"));
        Session session = driver.session(SessionConfig.forDatabase("neo4j"));

        // Embedding all nodes
        var nodesResult = session.run("MATCH (n) RETURN n");
        while (nodesResult.hasNext()) {
            Record record = nodesResult.next();
            Node node = record.get("n").asNode();

            String nodeText = formatNodeText(node);

            String label = node.labels().iterator().next();
            TextSegment segment = TextSegment.from(nodeText, metadata("label", label));

            embeddingStore.add(embeddingModel.embed(segment).content(), segment);
        }

        // Embedding all relationships (not recommended)
        var relationshipsResult = session.run("MATCH ()-[r]->() RETURN r");
        while (relationshipsResult.hasNext()) {
            Record record = relationshipsResult.next();
            Relationship relationship = record.get("r").asRelationship();

            String relationshipText = formatRelationshipText(relationship);

            String type = relationship.type();
            TextSegment segment = TextSegment.from(relationshipText, metadata("relationship_type", type));

            embeddingStore.add(embeddingModel.embed(segment).content(), segment);
        }
    }

    //Query 1
    private static void EmbedQuery1(EmbeddingStore embeddingStore) {
        EmbeddingModel embeddingModel = new BgeSmallEnV15QuantizedEmbeddingModel();

        Driver driver = GraphDatabase.driver("bolt://localhost:7687", AuthTokens.basic("neo4j", "Password123"));
        Session session = driver.session(SessionConfig.forDatabase("neo4j"));

        var nodesResult = session.run("MATCH (k:Kommun)" +
                "MATCH (ay:Years)" +
                "WHERE ay.year = 2021 " +
                "MATCH (kat:kulturskol_antal_total)" +
                "WHERE kat.value >= 0 " +
                "MATCH (kat)-[:LOCATED_IN]->(k) " +
                "MATCH (kat)-[:YEAR]->(ay) " +
                "RETURN k, kat");
        while (nodesResult.hasNext()) {
            Record record = nodesResult.next();
            Node node = record.get("k").asNode();
            Node katNode = record.get("kat").asNode();

            String nodeText = formatNodeText(node);
            String katNodeText = formatNodeText(katNode);

            String label = node.labels().iterator().next();
            TextSegment segment = TextSegment.from("Question: List the number of participants in cultural schools for the year 2021 next to its municipality (Kommun). Content: " + nodeText + "\n" + katNodeText, metadata("label", label));
            embeddingStore.add(embeddingModel.embed(segment).content(), segment);
        }
    }

    private static void EmbedQuery2(EmbeddingStore embeddingStore) {
        EmbeddingModel embeddingModel = new BgeSmallEnV15QuantizedEmbeddingModel();

        Driver driver = GraphDatabase.driver("bolt://localhost:7687", AuthTokens.basic("neo4j", "Password123"));
        Session session = driver.session(SessionConfig.forDatabase("neo4j"));

        var nodesResult = session.run("MATCH (k:Kommun) " +
                "MATCH (ay:Years) " +
                "WHERE ay.year = 2021 " +
                "MATCH (ag:AgeGroup) " +
                "WHERE ag.value = '6-19' " +
                "MATCH (kp:kulturskol_antal_total_age) " +
                "WHERE kp.value >= 0 " +
                "MATCH (kp)-[:LOCATED_IN]->(k) " +
                "MATCH (kp)-[:YEAR]->(ay) " +
                "MATCH (kp)-[:AGE_GROUP]->(ag) " +
                "RETURN kp.value as antal, k.kommunnamn as kommun " +
                "ORDER BY kommun");

        while (nodesResult.hasNext()) {
            Record record = nodesResult.next();
            int antal = record.get("antal").asInt();
            String kommun = record.get("kommun").asString();

            TextSegment segment = TextSegment.from("Question: the total number of participants in cultural schools for each municipality (Kommun), within the age group of 6 to 19 years for the year 2021. Content: " +
                    kommun + " is " + antal + ".", metadata("label", "kommun"));

            embeddingStore.add(embeddingModel.embed(segment).content(), segment);
        }
    }

    private static void EmbedQuery3(EmbeddingStore embeddingStore) {
        EmbeddingModel embeddingModel = new BgeSmallEnV15QuantizedEmbeddingModel();

        Driver driver = GraphDatabase.driver("bolt://localhost:7687", AuthTokens.basic("neo4j", "Password123"));
        Session session = driver.session(SessionConfig.forDatabase("neo4j"));

        var nodesResult = session.run("MATCH (k:Kommun) " +
                "MATCH (ay:Years) " +
                "WHERE ay.year = 2021 " +
                "MATCH (kp:kulturskol_kurs_offered_count) " +
                "WHERE kp.value >= 0 " +
                "MATCH (kp)-[:LOCATED_IN]->(k) " +
                "MATCH (kp)-[:YEAR]->(ay) " +
                "RETURN k.kommunnamn as kommun, kp.value as antal " +
                "ORDER BY kommun");

        while (nodesResult.hasNext()) {
            Record record = nodesResult.next();
            String kommun = record.get("kommun").asString();
            int antal = record.get("antal").asInt();

            TextSegment segment = TextSegment.from("Question: : Retrieve the number of subject courses offered in cultural schools (count of subject areas) for the year 2021, categorized by municipalities. Content:" +
                    kommun + " is " + antal + ".", metadata("label", "kommun"));
            embeddingStore.add(embeddingModel.embed(segment).content(), segment);
        }
    }

    private static void EmbedQuery4(EmbeddingStore embeddingStore) {
        EmbeddingModel embeddingModel = new BgeSmallEnV15QuantizedEmbeddingModel();

        Driver driver = GraphDatabase.driver("bolt://localhost:7687", AuthTokens.basic("neo4j", "Password123"));
        Session session = driver.session(SessionConfig.forDatabase("neo4j"));

        var nodesResult = session.run("MATCH (b:bibliotek) " +
                "MATCH (k:Kommun) " +
                "MATCH (bt:bib_type) " +
                "WHERE k.kommunnamn = 'Nynäshamn' " +
                "MATCH (b)-[:LOCATED_IN]->(k) " +
                "MATCH (b)-[:LIBRARY_TYPE]->(bt) " +
                "RETURN b.bibliotek as bibliotek, bt.type as bibtyp");

        while (nodesResult.hasNext()) {
            Record record = nodesResult.next();
            String bibliotek = record.get("bibliotek").asString();
            String bibtyp = record.get("bibtyp").asString();

            TextSegment segment = TextSegment.from("Question: Retrieve information about libraries in Nynäshamn, including their types. We're looking at data from libraries (bibliotek) and library types (bib_type). " +
                            "Content: The bibliotek '" + bibliotek + "' in Nynäshamn is of bibtyp '" + bibtyp + "'.",
                    metadata("label", "bibliotek"));
            embeddingStore.add(embeddingModel.embed(segment).content(), segment);
        }
    }


    private static void EmbedQuery5(EmbeddingStore embeddingStore) {
        EmbeddingModel embeddingModel = new BgeSmallEnV15QuantizedEmbeddingModel();

        Driver driver = GraphDatabase.driver("bolt://localhost:7687", AuthTokens.basic("neo4j", "Password123"));
        Session session = driver.session(SessionConfig.forDatabase("neo4j"));

        var nodesResult = session.run("MATCH (bp:bib_param) " +
                "WHERE bp.param = 'Publ299' " +
                "MATCH (bv:bib_param_value) " +
                "MATCH (bv)-[:DESC]->(bp) " +
                "MATCH (ay:Years) " +
                "WHERE ay.year = 2022 " +
                "MATCH (bv)-[:YEAR]->(ay) " +
                "MATCH (b:bibliotek) " +
                "MATCH (k:Kommun) " +
                "MATCH (bt:bib_type) " +
                "WHERE k.kommunnamn = 'Nynäshamn' " +
                "MATCH (b)-[:LOCATED_IN]->(k) " +
                "MATCH (bv)-[:LIBRARY_PARAMETER]->(b) " +
                "WHERE bv.value >= 0 " +
                "RETURN b.bibliotek as bibliotek, sum(bv.value) as antal_aktiviteter");

        while (nodesResult.hasNext()) {
            Record record = nodesResult.next();
            String bibliotek = record.get("bibliotek").asString();
            int antal_aktiviteter = record.get("antal_aktiviteter").asInt();

            TextSegment segment = TextSegment.from("Question: Retrieve the total number of activities for children and young people in libraries for the year 2022 in Nynäshamn municipality. Content: " + "The library '" + bibliotek + "' in Nynäshamn has a total of " + antal_aktiviteter + " activities for the year 2022.",
                    metadata("label", "library"));
            embeddingStore.add(embeddingModel.embed(segment).content(), segment);
        }
    }

    private static void EmbedQuery6(EmbeddingStore embeddingStore) {
        EmbeddingModel embeddingModel = new BgeSmallEnV15QuantizedEmbeddingModel();

        Driver driver = GraphDatabase.driver("bolt://localhost:7687", AuthTokens.basic("neo4j", "Password123"));
        Session session = driver.session(SessionConfig.forDatabase("neo4j"));

        var nodesResult = session.run("MATCH (incs:IncomeStandard) " +
                "MATCH (loc:Kommun) " +
                "WHERE loc.kommunnamn = 'Nynäshamn' " +
                "MATCH (agr:AgeGroup) " +
                "WHERE agr.value = '0-21' " +
                "MATCH (isl:IncomeStandardLevel) " +
                "MATCH (ay:Years) " +
                "WHERE ay.year > 2015 " +
                "MATCH (incs)-[:INCOME_LEVEL]->(isl) " +
                "MATCH (incs)-[:LOCATED_IN]->(loc) " +
                "MATCH (incs)-[:REPORTED_YEAR]->(ay) " +
                "MATCH (incs)-[:AGE_GROUP]->(agr) " +
                "RETURN sum(incs.value) as Antal, ay.year as År, isl.value as Kategori " +
                "ORDER BY År");

        while (nodesResult.hasNext()) {
            Record record = nodesResult.next();
            int Antal = record.get("Antal").asInt();
            int År = record.get("År").asInt();
            String Kategori = record.get("Kategori").asString();

            TextSegment segment = TextSegment.from("Question: Retrieve the number of individuals with income standard within the age group of 0 to 21 years in Nynäshamn municipality from Statistics Sweden (SCB) data, sorted by year." +
                    " Content:" + "For the year " + År + ", the total income standard value for the age group 0-21 in Nynäshamn, categorized as " + Kategori + ", is " + Antal + ".",
                    metadata("label", "income"));
            embeddingStore.add(embeddingModel.embed(segment).content(), segment);
        }
    }


    private static void EmbedQuery7(EmbeddingStore embeddingStore) {
        EmbeddingModel embeddingModel = new BgeSmallEnV15QuantizedEmbeddingModel();

        Driver driver = GraphDatabase.driver("bolt://localhost:7687", AuthTokens.basic("neo4j", "Password123"));
        Session session = driver.session(SessionConfig.forDatabase("neo4j"));

        var nodesResult = session.run("MATCH (incs:IncomeStandard) " +
                "MATCH (loc:Kommun) " +
                "WHERE loc.kommunnamn = 'Nynäshamn' " +
                "MATCH (agr:AgeGroup) " +
                "WHERE agr.value = '0-21' " +
                "MATCH (isl:IncomeStandardLevel) " +
                "WHERE isl.value = 'low income standard' " +
                "MATCH (ay:Years) " +
                "WHERE ay.year > 2015 " +
                "MATCH (k:Kön) " +
                "MATCH (incs)-[:INCOME_LEVEL]->(isl) " +
                "MATCH (incs)-[:LOCATED_IN]->(loc) " +
                "MATCH (incs)-[:REPORTED_YEAR]->(ay) " +
                "MATCH (incs)-[:AGE_GROUP]->(agr) " +
                "MATCH (incs)-[:Kon]->(k) " +
                "RETURN incs.value as Antal, ay.year as År, k.sv as Kön " +
                "ORDER BY År");

        while (nodesResult.hasNext()) {
            Record record = nodesResult.next();
            int Antal = record.get("Antal").asInt();
            int År = record.get("År").asInt();
            String Kön = record.get("Kön").asString();

            TextSegment segment = TextSegment.from("Question: Retrieve the number of individuals with low income standard within the age group of 0 to 21 years in Nynäshamn municipality from Statistics Sweden (SCB) data categorized by gender and sorted by year. " +
                    " Content: " + "For the year " + År + ", the total income standard value for the age group 0-21 in Nynäshamn, categorized as 'low income standard', for the gender '" + Kön + "' is " + Antal + ".",
                    metadata("label", "income"));
            embeddingStore.add(embeddingModel.embed(segment).content(), segment);
        }
    }

    private static void EmbedQuery8(EmbeddingStore embeddingStore) {
        EmbeddingModel embeddingModel = new BgeSmallEnV15QuantizedEmbeddingModel();

        Driver driver = GraphDatabase.driver("bolt://localhost:7687", AuthTokens.basic("neo4j", "Password123"));
        Session session = driver.session(SessionConfig.forDatabase("neo4j"));

        var nodesResult = session.run("MATCH (incs:IncomeStandard) " +
                "MATCH (loc:Kommun) " +
                "WHERE loc.kommunnamn = 'Nynäshamn' " +
                "MATCH (agr:AgeGroup) " +
                "WHERE agr.value = '0-21' " +
                "MATCH (isl:IncomeStandardLevel) " +
                "WHERE isl.value = 'medium income standard' " +
                "MATCH (ay:Years) " +
                "WHERE ay.year > 2015 " +
                "MATCH (k:Kön) " +
                "MATCH (incs)-[:INCOME_LEVEL]->(isl) " +
                "MATCH (incs)-[:LOCATED_IN]->(loc) " +
                "MATCH (incs)-[:REPORTED_YEAR]->(ay) " +
                "MATCH (incs)-[:AGE_GROUP]->(agr) " +
                "MATCH (incs)-[:Kon]->(k) " +
                "RETURN incs.value as Antal, ay.year as År, k.sv as Kön " +
                "ORDER BY År");

        while (nodesResult.hasNext()) {
            Record record = nodesResult.next();
            int Antal = record.get("Antal").asInt();
            int År = record.get("År").asInt();
            String Kön = record.get("Kön").asString();

            TextSegment segment = TextSegment.from("Question: Retrieve the number of individuals with within the age group of 0 to 21 years in Nynäshamn municipality from Statistics Sweden (SCB) data, categorized by gender and sorted by year, retrieved based on “medium income standard”. " +
                            " Content: " + "For the year " + År + ", the total income standard value for the age group 0-21 in Nynäshamn, categorized as 'medium income standard', for the gender '" + Kön + "' is " + Antal + ".",
                    metadata("label", "income"));
            embeddingStore.add(embeddingModel.embed(segment).content(), segment);
        }
    }


    private static void EmbedQuery9(EmbeddingStore embeddingStore) {
        EmbeddingModel embeddingModel = new BgeSmallEnV15QuantizedEmbeddingModel();

        Driver driver = GraphDatabase.driver("bolt://localhost:7687", AuthTokens.basic("neo4j", "Password123"));
        Session session = driver.session(SessionConfig.forDatabase("neo4j"));

        var nodesResult = session.run("MATCH (incs:IncomeStandard) " +
                "MATCH (loc:Kommun) " +
                "WHERE loc.kommunnamn = 'Nynäshamn' " +
                "MATCH (agr:AgeGroup) " +
                "WHERE agr.value = '0-21' " +
                "MATCH (isl:IncomeStandardLevel) " +
                "WHERE isl.value = 'high income standard' " +
                "MATCH (ay:Years) " +
                "WHERE ay.year > 2015 " +
                "MATCH (k:Kön) " +
                "MATCH (incs)-[:INCOME_LEVEL]->(isl) " +
                "MATCH (incs)-[:LOCATED_IN]->(loc) " +
                "MATCH (incs)-[:REPORTED_YEAR]->(ay) " +
                "MATCH (incs)-[:AGE_GROUP]->(agr) " +
                "MATCH (incs)-[:Kon]->(k) " +
                "RETURN incs.value as Antal, ay.year as År, k.sv as Kön " +
                "ORDER BY År");

        while (nodesResult.hasNext()) {
            Record record = nodesResult.next();
            int Antal = record.get("Antal").asInt();
            int År = record.get("År").asInt();
            String Kön = record.get("Kön").asString();

            TextSegment segment = TextSegment.from("Question: Retrieve the number of individuals within the age group of 0 to 21 years in Nynäshamn municipality from Statistics Sweden (SCB) data, categorized by gender and sorted by year, retrieved based up-on “high income standard”." +
                            " Content: " + "For the year " + År + ", the total income standard value for the age group 0-21 in Nynäshamn, categorized as 'high income standard', for the gender '" + Kön + "' is " + Antal + ".",
                    metadata("label", "income"));
            embeddingStore.add(embeddingModel.embed(segment).content(), segment);
        }
    }

    private static void EmbedQuery10(EmbeddingStore embeddingStore) {
        EmbeddingModel embeddingModel = new BgeSmallEnV15QuantizedEmbeddingModel();

        Driver driver = GraphDatabase.driver("bolt://localhost:7687", AuthTokens.basic("neo4j", "Password123"));
        Session session = driver.session(SessionConfig.forDatabase("neo4j"));

        var nodesResult = session.run("MATCH (ag:AgeGroup) " +
                "MATCH (sex:Kön) " +
                "MATCH (sp:Idrott) " +
                "WHERE sp.sv <> 'Total' " +
                "MATCH (p:Period) " +
                "WHERE p.sv = 'vår' OR p.sv = 'höst' " +
                "MATCH (ay:Years) " +
                "WHERE ay.year = 2020 " +
                "MATCH (km:Kommun) " +
                "WHERE km.kommunnamn = 'Nynäshamn' " +
                "MATCH (rf:RF_Klubb) " +
                "MATCH (rf)-[:LOCATED_IN]->(km) " +
                "MATCH (dl:RF_Deltagartillfällen) " +
                "MATCH (dl)-[:RF_KLUBB]->(rf) " +
                "MATCH (dl)-[:SPEL]->(sp) " +
                "MATCH (dl)-[:YEAR]->(ay) " +
                "MATCH (dl)-[:PERIOD]->(p) " +
                "MATCH (dl)-[:AGE_GROUP]->(ag) " +
                "MATCH (dl)-[:Kön]->(sex) " +
                "RETURN sum(dl.value) as deltagartillfällen, sp.sv as Idrott, ag.value as åldersgrupp " +
                "ORDER BY (CASE åldersgrupp " +
                "WHEN '7-12' THEN 0 " +
                "WHEN '13-16' THEN 1 " +
                "WHEN '17-20' THEN 2 " +
                "WHEN '21-25' THEN 3 " +
                "WHEN '26-' THEN 4 " +
                "ELSE -1 END)");

        while (nodesResult.hasNext()) {
            Record record = nodesResult.next();
            int deltagartillfällen = record.get("deltagartillfällen").asInt();
            String idrott = record.get("Idrott").asString();
            String åldersgrupp = record.get("åldersgrupp").asString();

            TextSegment segment = TextSegment.from("Question: Retrieve the number of sports participants per age group for the year 2020 in Nynäshamn municipali-ty." +
                            " Content: " + "For the sport " + idrott + " in the age group " + åldersgrupp + ", there were " + deltagartillfällen + " participants in Nynäshamn during the year 2020.",
                    metadata("label", "sports"));
            embeddingStore.add(embeddingModel.embed(segment).content(), segment);
        }
    }
}
