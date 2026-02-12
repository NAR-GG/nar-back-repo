package com.toy.nar.app.data.maintenance;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
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
    private final ResourcePatternResolver resourcePatternResolver;

    private static final String IMAGE_DIR_PATTERN = "classpath:static/images/players/*";
    // Frontend expects /images/players/...
    // Spring Boot serves classpath:/static/images/players/ as /images/players/ by
    // default.

    @Transactional
    public Map<String, Object> migratePlayerImages() {
        log.info("Starting player image migration scanning: {}", IMAGE_DIR_PATTERN);
        Map<String, Object> result = new HashMap<>();
        int updatedCount = 0;
        int skippedCount = 0;
        int failedCount = 0;

        try {
            Resource[] resources = resourcePatternResolver.getResources(IMAGE_DIR_PATTERN);
            log.info("Found {} resources", resources.length);

            for (Resource resource : resources) {
                String filename = resource.getFilename();
                if (filename == null || !isImageFile(filename)) {
                    continue;
                }

                try {
                    boolean updated = processImage(filename);
                    if (updated) {
                        updatedCount++;
                    } else {
                        skippedCount++;
                    }
                } catch (Exception e) {
                    log.error("Failed to process image: {}", filename, e);
                    failedCount++;
                }
            }

            result.put("success", true);
            result.put("updated", updatedCount);
            result.put("skipped", skippedCount);
            result.put("failed", failedCount);
            result.put("message", String.format("Migration complete. Updated: %d, Skipped: %d, Failed: %d",
                    updatedCount, skippedCount, failedCount));

        } catch (IOException e) {
            log.error("Failed to list resources", e);
            result.put("success", false);
            result.put("message", "Failed to access image resources: " + e.getMessage());
        }

        return result;
    }

    private boolean processImage(String filename) {
        // Format: SummonerName_RealName.extension or SummonerName.extension
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
                log.info("Updated image for player {}: {}", summonerName, newUrl);
                return true;
            } else {
                return false;
            }
        } else {
            log.warn("Player not found for file: {} (Extracted name: {})", filename, summonerName);
            return false; // Treated as skipped/failed regarding update count? Or just ignored.
            // Current logic: if false -> skippedCount++.
            // If player not found, maybe we should not count as "skipped" in the sense of
            // "already up to date",
            // but effectively we did nothing. Use skipped for now.
        }
    }

    private boolean isImageFile(String filename) {
        String lower = filename.toLowerCase();
        return lower.endsWith(".jpg") || lower.endsWith(".jpeg")
                || lower.endsWith(".png") || lower.endsWith(".webp");
    }
}
