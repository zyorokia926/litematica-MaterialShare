package dev.materialshare.server;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.materialshare.common.ProjectRegistry;
import org.junit.jupiter.api.Test;

class ServerConfigTest {
    @Test
    void defaultsAreTheBuiltInLimit() {
        assertEquals(ProjectRegistry.MAX_PROJECTS, ServerConfig.defaults().maxProjects());
    }

    @Test
    void brokenOrEmptyTextGivesDefaults() {
        for (String text : new String[] {"", "not json", "[]", "null", "{\"maxProjects\": \"x\"}"}) {
            assertEquals(ProjectRegistry.MAX_PROJECTS, ServerConfig.parse(text).maxProjects(), text);
        }
    }

    @Test
    void theNumberIsKeptInRange() {
        assertEquals(10, ServerConfig.parse("{\"maxProjects\":10}").maxProjects());
        assertEquals(ProjectRegistry.MAX_PROJECTS, ServerConfig.parse("{\"maxProjects\":100000}").maxProjects());
        assertEquals(1, ServerConfig.parse("{\"maxProjects\":-3}").maxProjects());
    }

    @Test
    void writtenFileReadsBackTheSame() {
        assertEquals(10, ServerConfig.parse(ServerConfig.parse("{\"maxProjects\":10}").toJson()).maxProjects());
    }
}
