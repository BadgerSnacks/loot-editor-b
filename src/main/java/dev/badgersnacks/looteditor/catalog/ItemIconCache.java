package dev.badgersnacks.looteditor.catalog;

import javafx.scene.image.Image;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;

import java.io.ByteArrayInputStream;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Lightweight cache that turns raw PNG bytes into JavaFX Image objects on demand.
 */
public final class ItemIconCache {

    private final Map<String, Image> cache = new ConcurrentHashMap<>();
    private final Image placeholder;

    public ItemIconCache() {
        this.placeholder = buildPlaceholder();
    }

    public Image imageFor(ItemDescriptor descriptor) {
        return imageFor(descriptor.qualifiedId(), descriptor.iconData());
    }

    public Image imageFor(String qualifiedId, ItemCatalog catalog) {
        byte[] data = catalog == null ? null : catalog.iconDataFor(qualifiedId);
        return imageFor(qualifiedId, data);
    }

    private Image imageFor(String key, byte[] bytes) {
        return cache.computeIfAbsent(key, ignore -> {
            if (bytes == null || bytes.length == 0) {
                return placeholder;
            }
            return new Image(new ByteArrayInputStream(bytes), 32, 32, true, true);
        });
    }

    /**
     * Clears the cached images so future requests re-read the latest icon bytes (used when a new catalog loads).
     */
    public void clear() {
        cache.clear();
    }

    private static Image buildPlaceholder() {
        WritableImage image = new WritableImage(16, 16);
        PixelWriter writer = image.getPixelWriter();
        for (int x = 0; x < 16; x++) {
            for (int y = 0; y < 16; y++) {
                boolean dark = (x / 4 + y / 4) % 2 == 0;
                writer.setArgb(x, y, dark ? 0xFF555555 : 0xFF777777);
            }
        }
        return image;
    }
}
