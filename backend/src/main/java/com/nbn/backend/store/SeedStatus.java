package com.nbn.backend.store;

import com.nbn.backend.http.JsonResponse;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public final class SeedStatus {
    private static final List<String> EXPECTED_SEED_FILES = List.of(
            "home_feed.json",
            "ad_details.json",
            "search_results.json",
            "messages.json",
            "profile.json",
            "reviews.json",
            "app_config.json"
    );

    private final List<String> loadedFiles;

    private SeedStatus(List<String> loadedFiles) {
        this.loadedFiles = List.copyOf(loadedFiles);
    }

    public static List<String> expectedSeedFiles() {
        return EXPECTED_SEED_FILES;
    }

    public static SeedStatus fromLoadedFiles(Collection<String> loadedFiles) {
        List<String> knownLoadedFiles = new ArrayList<>();
        for (String seedFile : EXPECTED_SEED_FILES) {
            if (loadedFiles.contains(seedFile)) {
                knownLoadedFiles.add(seedFile);
            }
        }
        return new SeedStatus(knownLoadedFiles);
    }

    public static SeedStatus inspectDefaultResources(ClassLoader classLoader) {
        List<String> loadedFiles = new ArrayList<>();
        for (String seedFile : EXPECTED_SEED_FILES) {
            URL resource = classLoader.getResource("seed/" + seedFile);
            if (resource != null) {
                loadedFiles.add(seedFile);
            }
        }
        return new SeedStatus(loadedFiles);
    }

    public int expectedCount() {
        return EXPECTED_SEED_FILES.size();
    }

    public int loadedCount() {
        return loadedFiles.size();
    }

    public String status() {
        if (loadedFiles.isEmpty()) {
            return "not_loaded";
        }
        if (loadedFiles.size() == EXPECTED_SEED_FILES.size()) {
            return "loaded";
        }
        return "partial";
    }

    public String toJson() {
        StringBuilder filesJson = new StringBuilder("[");
        for (int index = 0; index < EXPECTED_SEED_FILES.size(); index++) {
            if (index > 0) {
                filesJson.append(",");
            }
            String name = EXPECTED_SEED_FILES.get(index);
            filesJson.append("{")
                    .append("\"name\":\"")
                    .append(JsonResponse.escape(name))
                    .append("\",")
                    .append("\"loaded\":")
                    .append(loadedFiles.contains(name))
                    .append("}");
        }
        filesJson.append("]");
        return "{"
                + "\"status\":\"" + status() + "\","
                + "\"expectedCount\":" + expectedCount() + ","
                + "\"loadedCount\":" + loadedCount() + ","
                + "\"files\":" + filesJson
                + "}";
    }
}
