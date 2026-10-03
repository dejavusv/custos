package com.custos.modules.backup.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertTrue;

class StorageItemDtoJsonTests {

    // The frontend reads item.isDirectory; Lombok alone would serialize the property as "directory"
    @Test
    void serializesDirectoryFlagAsIsDirectory() throws Exception {
        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        StorageItemDto dto = StorageItemDto.builder()
                .name("html").path("/html").absolutePath("/html")
                .isDirectory(true).sizeBytes(0).lastModified(Instant.EPOCH).build();

        JsonNode json = mapper.readTree(mapper.writeValueAsString(dto));

        assertTrue(json.has("isDirectory"), json.toString());
        assertTrue(json.get("isDirectory").asBoolean());
    }
}
