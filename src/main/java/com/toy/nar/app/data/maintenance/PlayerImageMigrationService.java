package com.toy.nar.app.data.maintenance;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.toy.nar.domain.participant.entity.Player;
import com.toy.nar.domain.participant.repository.PlayerRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class PlayerImageMigrationService {

    private final PlayerRepository playerRepository;

    private static final String IMAGE_DIR_PATH = "src/main/resources/static/images/players";

    @Transactional
    public Map<String, Object> migratePlayerImages() {
        log.info("Starting player image migration from: {}", IMAGE_DIR_PATH);
        Map<String, Object> result = new HashMap<>();

        Path dirPath = Paths.get(IMAGE_DIR_PATH);
        if (!Files.exists(dirPath) || !Files.isDirectory(dirPath)) {
            log.error("Image directory not found: {}", dirPath.toAbsolutePath());
            result.put("success", false);
            result.put("message", "Directory not found: " + dirPath.toAbsolutePath());
            return result;
        }

        int updatedCount = 0;
        int failedCount = 0;
        int skippedCount = 0;

        try (Stream<Path> stream = Files.list(dirPath)) {
            List<Path> files = stream
                    .filter(file -> !Files.isDirectory(file))
                    .toList();

            for (Path file : files) {
                String filename = file.getFileName().toString();
                // Format: SummonerName_RealName.extension or SummonerName.extension
                // Split by first '_' to get SummonerName?
                // Or last '_'? User said "선수명(영어)_선수이름(한글)" -> e.g. "Faker_LeeSangHyeok.jpg"

                String namePart = filename;
                if (namePart.contains(".")) {
                    namePart = namePart.substring(0, namePart.lastIndexOf('.'));
                }

                String summonerName;
                if (namePart.contains("_")) {
                    // "Faker_Lee" -> "Faker"
                    summonerName = namePart.split("_")[0];
                } else {
                    summonerName = namePart;
                }

                Optional<Player> playerOpt = playerRepository.findByName(summonerName);
                if (playerOpt.isPresent()) {
                    Player player = playerOpt.get();
                    String newUrl = "/images/players/" + filename;

                    if (!newUrl.equals(player.getImageUrl())) {
                        player.setImageUrl(newUrl);
                        updatedCount++;
                        log.info("Updated image for player {}: {}", summonerName, newUrl);
                    } else {
                        skippedCount++;
                    }
                } else {
                    log.warn("Player not found for file: {} (Extracted name: {})", filename, summonerName);
                    failedCount++;
                }
            }

        } catch (IOException e) {
            log.error("Error reading image directory", e);
            result.put("success", false);
            result.put("message", "Error reading directory: " + e.getMessage());
            return result;
        }

        result.put("success", true);
        result.put("updated", updatedCount);
        result.put("skipped", skippedCount);
        result.put("failed", failedCount);
        result.put("message", String.format("Migration complete. Updated: %d, Skipped: %d, Failed: %d", updatedCount,
                skippedCount, failedCount));

        log.info("Migration result: {}", result);
        return result;
    }
}
