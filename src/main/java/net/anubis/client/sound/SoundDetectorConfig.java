package net.anubis.client.sound;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Stands in for Donut Client's ClientConfig: holds the Sound Detector preferences and saves them
 * to config/anubis/sound-detector.json (written to a temporary file, then moved into place).
 */
public final class SoundDetectorConfig {
    private static final Logger LOGGER = LoggerFactory.getLogger("Anubis");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static SoundDetectorConfig instance;

    public SoundDetectorSettings soundDetector = new SoundDetectorSettings();

    private SoundDetectorConfig() { }

    public static synchronized SoundDetectorConfig get() {
        if (instance == null) {
            instance = new SoundDetectorConfig();
            instance.load();
        }
        return instance;
    }

    private static Path file() { return FabricLoader.getInstance().getConfigDir().resolve("anubis").resolve("sound-detector.json"); }

    private void load() {
        Path path = file();
        if (!Files.isRegularFile(path)) return;
        try {
            SoundDetectorSettings loaded = GSON.fromJson(Files.readString(path, StandardCharsets.UTF_8), SoundDetectorSettings.class);
            if (loaded != null) soundDetector = loaded;
        } catch (IOException | RuntimeException e) {
            LOGGER.warn("Could not read {}, using defaults", path, e);
        }
        soundDetector.sanitize();
    }

    public void save() {
        Path path = file();
        try {
            Files.createDirectories(path.getParent());
            Path temporary = path.resolveSibling(path.getFileName() + ".tmp");
            Files.writeString(temporary, GSON.toJson(soundDetector), StandardCharsets.UTF_8);
            Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            LOGGER.warn("Could not save {}", path, e);
        }
    }
}
