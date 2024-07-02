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

@Component
public class EmbedNeo4jDB {
    public void executeQuery(String query, String NLPQuestion){
        EmbeddingStore<TextSegment> embeddingStore = initializeNeo4j();
        embedNodesWithQuery(embeddingStore, query, NLPQuestion);
    }

    //Takes any query and embeds the result for both nodes and relationships
    private static void embedNodesWithQuery(EmbeddingStore<TextSegment> embeddingStore, String query, String NLPQuestion) {
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

    //Override the metadata tag from Neo4j with customized format
    private static Map<String, Object> metadataMap(String value) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("label", value);
        return metadata;
    }

    //Method to create a TextSegment from both the Node / Relationship / String data with a label
    private static TextSegment createTextSegment(String cleanedText, String label) {
        if (cleanedText == null || cleanedText.isBlank()) {
            throw new IllegalArgumentException("Cleaned text cannot be null or blank");
        }
        return TextSegment.from(label + ": " + cleanedText, Metadata.from(metadataMap(label)));
    }

    //Combines the individual embedding from each TextSegments and aggregates them into 1 so that it can be placed in a single Document in the Neo4j database
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

    private static String cleanTextSegment(String input, String NLPQuestion) {
        // Clean out id: followed by a UUID
        String cleanedText = input.replaceAll("id: [a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12} ,", "");

        // Clean out value: followed by a number
        cleanedText = cleanedText.replaceAll("value: (\\d+) ,", "$1 ,");

        // Remove stray closing curly braces and encapsulate each block in curly braces {}
        cleanedText = encapsulateBlocksInCurlyBraces(cleanedText);

        return NLPQuestion + " " + cleanedText.trim();
    }

    // Find each block and encapsulate it in curly braces {}, also add Entry for each row in the neo4j table
    private static String encapsulateBlocksInCurlyBraces(String input) {
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
}
