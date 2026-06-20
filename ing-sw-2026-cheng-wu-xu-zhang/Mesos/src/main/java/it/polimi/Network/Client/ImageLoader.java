package it.polimi.Network.Client;

import javafx.scene.image.Image;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.net.URISyntaxException;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Utility class to load and cache card images for the JavaFX client.
 */
public class ImageLoader {

    /** Creates an image loader. */
    public ImageLoader() {
    }
    private static final ConcurrentHashMap<Integer, Image> cache = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, Image> tileCache = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, Image> tokenCache = new ConcurrentHashMap<>();
    private static Path cardsDirectory;
    private static Path tilesDirectory;
    private static boolean warningShown = false;

    /**
     * Gets a card image from the cache or loads it from disk.
     *
     * @param assetId the ID of the card image
     * @return the Image object, or null if not found
     */
    public static Image getCardImage(int assetId) {
        if (assetId <= 0) return null;
        
        return cache.computeIfAbsent(assetId, id -> {
            Path dir = resolveCardsDirectory();
            if (dir == null) return null;
            
            Path file = dir.resolve(String.format("card_%03d.png", id));
            if (!Files.exists(file)) return null;
            
            try {
                // JavaFX Image loading (uses file:// URL)
                return new Image(file.toUri().toString(), 160, 0, true, true);
            } catch (Exception e) {
                return null;
            }
        });
    }

    /**
     * Loads an image from the 'ui' folder in the resources.
     *
     * @param filename the name of the file
     * @return the Image object, or null if not found
     */
    public static Image getUIImage(String filename) {
        try {
            String path = "/ui/" + filename;
            java.net.URL url = ImageLoader.class.getResource(path);
            if (url != null) {
                return new Image(url.toExternalForm());
            }
        } catch (Exception e) {
            // Ignore and return null
        }
        return null;
    }

    /**
     * Gets an image from the tiles folder.
     *
     * @param filename the file name inside mesos-cards/tiles
     * @return the Image object, or null if not found
     */
    public static Image getTileImage(String filename) {
        if (filename == null || filename.trim().isEmpty()) return null;

        String normalized = filename.trim();
        return tileCache.computeIfAbsent(normalized, name -> {
            Image fromResources = getResourceImage("tiles", name);
            if (fromResources != null) return fromResources;

            Path dir = resolveTilesDirectory();
            if (dir == null) return null;

            Path file = dir.resolve(name);
            if (!Files.exists(file)) return null;

            try {
                return new Image(file.toUri().toString(), 140, 0, true, true);
            } catch (Exception e) {
                return null;
            }
        });
    }

    /**
     * Gets a front token image from mesos-cards/tokens.
     *
     * @param colorName player color name, e.g. BLUE or YELLOW
     * @return the Image object, or null if not found
     */
    public static Image getTokenFrontImage(String colorName) {
        if (colorName == null || colorName.trim().isEmpty()) return null;

        String normalized = "front:" + colorName.trim().toLowerCase();
        return tokenCache.computeIfAbsent(normalized, color -> {
            String filename = "token_front_" + color.substring("front:".length()) + ".png";
            Path cards = resolveCardsDirectory();
            if (cards == null) return null;

            Path file = cards.resolve("tokens").resolve(filename);
            if (!Files.exists(file)) return null;

            try {
                return new Image(file.toUri().toString(), 86, 0, true, true);
            } catch (Exception e) {
                return null;
            }
        });
    }

    /**
     * Gets a regular token image from mesos-cards/tokens.
     *
     * @param colorName player color name, e.g. BLUE or YELLOW
     * @return the Image object, or null if not found
     */
    public static Image getTokenImage(String colorName) {
        if (colorName == null || colorName.trim().isEmpty()) return null;

        String normalized = "token:" + colorName.trim().toLowerCase();
        return tokenCache.computeIfAbsent(normalized, color -> {
            String filename = "token_" + color.substring("token:".length()) + ".png";
            Path cards = resolveCardsDirectory();
            if (cards == null) return null;

            Path file = cards.resolve("tokens").resolve(filename);
            if (!Files.exists(file)) return null;

            try {
                return new Image(file.toUri().toString(), 86, 0, true, true);
            } catch (Exception e) {
                return null;
            }
        });
    }

    /**
     * Loads an image from the 'preview' folder in the project root or from resources.
     *
     * @param filename the name of the file (e.g., "background.png")
     * @return the Image object, or null if not found
     */
    public static Image getPreviewImage(String filename) {
        // Try to load from resources first
        Image fromResources = getUIImage(filename);
        if (fromResources != null) return fromResources;

        // Fallback to project root 'preview' folder
        Path root = findProjectRoot();
        if (root == null) return null;
        
        Path file = root.resolve("preview").resolve(filename);
        if (!Files.exists(file)) return null;
        
        return new Image(file.toUri().toString());
    }

    /**
     * Loads an image from a classpath folder.
     *
     * @param folder resource folder
     * @param filename image filename
     * @return the image, or {@code null} when the resource cannot be loaded
     */
    private static Image getResourceImage(String folder, String filename) {
        try {
            String path = "/" + folder + "/" + filename;
            java.net.URL url = ImageLoader.class.getResource(path);
            if (url != null) {
                return new Image(url.toExternalForm());
            }
        } catch (Exception e) {
            // Ignore and return null
        }
        return null;
    }

    /**
     * Searches parent directories for the project root.
     *
     * @return the project root, or {@code null} when it cannot be located
     */
    private static Path findProjectRoot() {
        Path current = Paths.get(System.getProperty("user.dir")).toAbsolutePath();
        while (current != null) {
            if (Files.isDirectory(current.resolve("preview"))) {
                return current;
            }
            current = current.getParent();
        }
        return null;
    }

    /**
     * Resolves the directory containing card images.
     *
     * @return the card-image directory, or {@code null} when unavailable
     */
    private static Path resolveCardsDirectory() {
        if (cardsDirectory != null && Files.isDirectory(cardsDirectory)) {
            return cardsDirectory;
        }

        for (Path start : new Path[]{
                Paths.get(System.getProperty("user.dir")).toAbsolutePath(),
                getCodeSourceDirectory()
        }) {
            Path found = findCardsDirectory(start);
            if (found != null) {
                cardsDirectory = found;
                System.out.println("Card images found at: " + found);
                return found;
            }
        }

        if (!warningShown) {
            warningShown = true;
            System.err.println("Could not find a 'mesos-cards' folder.");
        }
        return null;
    }

    /**
     * Resolves the directory containing offer-tile images.
     *
     * @return the tile-image directory, or {@code null} when unavailable
     */
    private static Path resolveTilesDirectory() {
        if (tilesDirectory != null && Files.isDirectory(tilesDirectory)) {
            return tilesDirectory;
        }

        for (Path start : new Path[]{
                Paths.get(System.getProperty("user.dir")).toAbsolutePath(),
                getCodeSourceDirectory()
        }) {
            if (start == null) continue;

            Path found = findTilesDirectory(start);
            if (found != null) {
                tilesDirectory = found;
                return found;
            }
        }

        return null;
    }

    /**
     * Searches upward from a path for the card-image directory.
     *
     * @param start starting path
     * @return the located directory, or {@code null}
     */
    private static Path findCardsDirectory(Path start) {
        Path current = start;
        while (current != null) {
            for (String folderName : new String[]{"mesos-cards", "mesos-card"}) {
                Path candidate = current.resolve(folderName);
                if (Files.isDirectory(candidate)) {
                    return candidate;
                }
            }
            current = current.getParent();
        }
        return null;
    }

    /**
     * Searches upward from a path for the offer-tile image directory.
     *
     * @param start starting path
     * @return the located directory, or {@code null}
     */
    private static Path findTilesDirectory(Path start) {
        Path current = start;
        while (current != null) {
            Path candidate = current.resolve("mesos-cards").resolve("tiles");
            if (Files.isDirectory(candidate)) {
                return candidate;
            }
            current = current.getParent();
        }
        return null;
    }

    /**
     * Resolves the directory from which this class was loaded.
     *
     * @return the code-source directory, or {@code null} when resolution fails
     */
    private static Path getCodeSourceDirectory() {
        try {
            Path location = Paths.get(ImageLoader.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            return Files.isDirectory(location) ? location.toAbsolutePath() : location.toAbsolutePath().getParent();
        } catch (URISyntaxException | SecurityException | NullPointerException e) {
            return null;
        }
    }
}
