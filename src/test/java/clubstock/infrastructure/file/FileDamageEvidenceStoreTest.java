package clubstock.infrastructure.file;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import clubstock.application.ApplicationErrorCode;
import clubstock.application.ApplicationException;
import clubstock.application.port.StagedDamageImage;
import clubstock.domain.report.DamageImageFormat;
import clubstock.domain.report.DamageImageReference;

class FileDamageEvidenceStoreTest {
    private static final int MAX_IMAGE_SIZE_BYTES = 5 * 1024 * 1024;

    @TempDir
    Path temporaryDirectory;

    @Test
    void exclusiveAccessSerializesStoreInstancesForOneEvidenceDirectory() throws Exception {
        Path evidenceDirectory = temporaryDirectory.resolve("damage-evidence");
        FileDamageEvidenceStore firstStore = new FileDamageEvidenceStore(evidenceDirectory);
        FileDamageEvidenceStore secondStore = new FileDamageEvidenceStore(evidenceDirectory);
        CountDownLatch firstEntered = new CountDownLatch(1);
        CountDownLatch allowFirstToExit = new CountDownLatch(1);
        CountDownLatch secondStarted = new CountDownLatch(1);
        CountDownLatch secondEntered = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<?> firstOperation = executor.submit(() -> firstStore.withExclusiveAccess(() -> {
                firstEntered.countDown();
                await(allowFirstToExit);
                return null;
            }));
            assertTrue(firstEntered.await(5, TimeUnit.SECONDS));

            Future<?> secondOperation = executor.submit(() -> {
                secondStarted.countDown();
                return secondStore.withExclusiveAccess(() -> {
                    secondEntered.countDown();
                    return null;
                });
            });
            assertTrue(secondStarted.await(5, TimeUnit.SECONDS));
            assertFalse(secondEntered.await(100, TimeUnit.MILLISECONDS));

            allowFirstToExit.countDown();
            firstOperation.get(5, TimeUnit.SECONDS);
            secondOperation.get(5, TimeUnit.SECONDS);
            assertEquals(0, secondEntered.getCount());
        } finally {
            allowFirstToExit.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void stageAcceptsAbsoluteAndRelativeImagePathsAndFinalizesGeneratedKeys() throws IOException {
        Path evidenceDirectory = temporaryDirectory.resolve("damage-evidence");
        FileDamageEvidenceStore store = new FileDamageEvidenceStore(evidenceDirectory);
        byte[] pngBytes = imageBytes("png");
        Path absoluteSource = temporaryDirectory.resolve("original-photo.png");
        Files.write(absoluteSource, pngBytes);

        StagedDamageImage stagedAbsolute = store.stage(absoluteSource);
        assertEquals(DamageImageFormat.PNG, stagedAbsolute.format());
        assertEquals(pngBytes.length, stagedAbsolute.sizeBytes());
        assertTrue(Files.isRegularFile(evidenceDirectory.resolve(".staging")
                .resolve(stagedAbsolute.stagingToken() + ".stage")));

        Path testDirectory = Path.of("build", "tmp", "test");
        Files.createDirectories(testDirectory);
        Path relativeDirectory = Files.createTempDirectory(testDirectory, "evidence-");
        Path relativeSource = relativeDirectory.resolve("selected-photo.png");
        try {
            Files.write(relativeSource, pngBytes);
            StagedDamageImage stagedRelative = store.stage(relativeSource);
            DamageImageReference reference = store.finalizeImage(stagedRelative);

            assertTrue(reference.storageKey().matches(
                    "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.png"));
            assertFalse(reference.storageKey().contains("original-photo"));
            assertArrayEquals(pngBytes, store.find(reference).orElseThrow().bytes());
            assertFalse(Files.exists(evidenceDirectory.resolve(".staging")
                    .resolve(stagedRelative.stagingToken() + ".stage")));
        } finally {
            Files.deleteIfExists(relativeSource);
            Files.deleteIfExists(relativeDirectory);
        }
    }

    @Test
    void stageRejectsEmptyMalformedAndOversizedFiles() throws IOException {
        FileDamageEvidenceStore store = new FileDamageEvidenceStore(
                temporaryDirectory.resolve("damage-evidence"));
        Path emptyImage = temporaryDirectory.resolve("empty.png");
        Files.createFile(emptyImage);
        Path malformedImage = temporaryDirectory.resolve("malformed.jpg");
        Files.writeString(malformedImage, "not a JPEG image");
        Path oversizedImage = temporaryDirectory.resolve("oversized.png");
        Files.write(oversizedImage, new byte[MAX_IMAGE_SIZE_BYTES + 1]);

        assertApplicationError(ApplicationErrorCode.VALIDATION_FAILED,
                () -> store.stage(emptyImage));
        assertApplicationError(ApplicationErrorCode.VALIDATION_FAILED,
                () -> store.stage(malformedImage));
        assertApplicationError(ApplicationErrorCode.VALIDATION_FAILED,
                () -> store.stage(oversizedImage));
        assertApplicationError(ApplicationErrorCode.VALIDATION_FAILED,
                () -> store.stage(temporaryDirectory.resolve("missing.png")));
    }

    @Test
    void stageRejectsImagesWithExcessivePixelCountOrDimension() throws IOException {
        FileDamageEvidenceStore store = new FileDamageEvidenceStore(
                temporaryDirectory.resolve("damage-evidence"));
        Path oversizedRaster = temporaryDirectory.resolve("oversized-raster.png");
        byte[] oversizedRasterBytes = imageBytes(4_001, 4_000, BufferedImage.TYPE_BYTE_GRAY);
        assertTrue(oversizedRasterBytes.length < MAX_IMAGE_SIZE_BYTES);
        Files.write(oversizedRaster, oversizedRasterBytes);

        Path oversizedDimension = temporaryDirectory.resolve("oversized-dimension.png");
        byte[] oversizedDimensionBytes = imageBytes(10_001, 1, BufferedImage.TYPE_BYTE_GRAY);
        assertTrue(oversizedDimensionBytes.length < MAX_IMAGE_SIZE_BYTES);
        Files.write(oversizedDimension, oversizedDimensionBytes);

        assertApplicationError(ApplicationErrorCode.VALIDATION_FAILED,
                () -> store.stage(oversizedRaster));
        assertApplicationError(ApplicationErrorCode.VALIDATION_FAILED,
                () -> store.stage(oversizedDimension));
    }

    @Test
    void findRejectsOversizedLegacyImagesAndPreservesTheirFiles() throws IOException {
        Path root = temporaryDirectory.resolve("damage-evidence");
        FileDamageEvidenceStore store = new FileDamageEvidenceStore(root);
        Files.createDirectories(root.resolve("archive"));
        for (String format : List.of("png", "jpeg")) {
            byte[] bytes = imageBytes(format, 4_001, 4_000, BufferedImage.TYPE_BYTE_GRAY);
            assertTrue(bytes.length < MAX_IMAGE_SIZE_BYTES);
            String key = "archive/large." + format;
            Files.write(root.resolve(key), bytes);
            DamageImageFormat imageFormat = format.equals("png")
                    ? DamageImageFormat.PNG : DamageImageFormat.JPEG;
            assertTrue(store.find(new DamageImageReference(key, imageFormat, bytes.length)).isEmpty());
            assertArrayEquals(bytes, Files.readAllBytes(root.resolve(key)));
        }
        byte[] tallImage = imageBytes(1, 10_001, BufferedImage.TYPE_BYTE_GRAY);
        Files.write(root.resolve("archive/tall.png"), tallImage);
        assertTrue(store.find(new DamageImageReference("archive/tall.png",
                DamageImageFormat.PNG, tallImage.length)).isEmpty());
    }

    @Test
    void findValidatesStoredContentAndFormatOnEveryRead() throws IOException {
        Path root = temporaryDirectory.resolve("damage-evidence");
        FileDamageEvidenceStore store = new FileDamageEvidenceStore(root);
        byte[] bytes = imageBytes("png");
        Path storedImage = root.resolve("legacy.png");
        Files.write(storedImage, bytes);
        DamageImageReference reference = new DamageImageReference("legacy.png",
                DamageImageFormat.PNG, bytes.length);
        assertArrayEquals(bytes, store.find(reference).orElseThrow().bytes());
        assertTrue(store.find(new DamageImageReference("legacy.png",
                DamageImageFormat.JPEG, bytes.length)).isEmpty());

        Files.write(storedImage, new byte[bytes.length]);
        assertTrue(store.find(reference).isEmpty());
        assertTrue(Files.exists(storedImage));
    }

    @Test
    void failedFinalizationAndDiscardLeaveNoFinalImage() throws IOException {
        Path evidenceDirectory = temporaryDirectory.resolve("damage-evidence");
        FileDamageEvidenceStore store = new FileDamageEvidenceStore(evidenceDirectory);
        Path source = temporaryDirectory.resolve("damage.png");
        Files.write(source, imageBytes("png"));
        StagedDamageImage stagedImage = store.stage(source);
        store.discardStaged(stagedImage);

        assertApplicationError(ApplicationErrorCode.IMAGE_STORAGE_FAILURE,
                () -> store.finalizeImage(stagedImage));
        assertEquals(0, countFinalizedImages(evidenceDirectory));
    }

    @Test
    void rejectsUnsafeKeysAndSymlinkEscapesForReadsAndDeletes() throws IOException {
        Path evidenceDirectory = temporaryDirectory.resolve("damage-evidence");
        FileDamageEvidenceStore store = new FileDamageEvidenceStore(evidenceDirectory);
        DamageImageReference unsafeReference = new DamageImageReference("nested//image.png",
                DamageImageFormat.PNG, 1);
        assertThrows(IllegalArgumentException.class, () -> store.find(unsafeReference));
        DamageImageReference stagingReference = new DamageImageReference(".staging/image.png",
                DamageImageFormat.PNG, 1);
        assertThrows(IllegalArgumentException.class, () -> store.find(stagingReference));

        Path externalImage = temporaryDirectory.resolve("external.png");
        Files.write(externalImage, new byte[] {1});
        String generatedKey = UUID.randomUUID() + ".png";
        Path symbolicImage = evidenceDirectory.resolve(generatedKey);
        try {
            Files.createSymbolicLink(symbolicImage, externalImage);
        } catch (UnsupportedOperationException | IOException | SecurityException exception) {
            org.junit.jupiter.api.Assumptions.assumeTrue(false,
                    "This file system does not permit symbolic links.");
        }

        DamageImageReference linkedReference = new DamageImageReference(generatedKey,
                DamageImageFormat.PNG, 1);
        assertApplicationError(ApplicationErrorCode.IMAGE_STORAGE_FAILURE,
                () -> store.find(linkedReference));
        assertApplicationError(ApplicationErrorCode.IMAGE_STORAGE_FAILURE,
                () -> store.discardFinalized(linkedReference));
        assertTrue(Files.exists(externalImage));
    }

    @Test
    void reconcileClearsStagingAndOrphanGeneratedImagesButPreservesCommittedAndLegacyImages()
            throws IOException {
        Path evidenceDirectory = temporaryDirectory.resolve("damage-evidence");
        Path stagingDirectory = evidenceDirectory.resolve(".staging");
        Path legacyDirectory = evidenceDirectory.resolve("archive");
        Files.createDirectories(stagingDirectory.resolve("nested"));
        Files.createDirectories(legacyDirectory);
        FileDamageEvidenceStore store = new FileDamageEvidenceStore(evidenceDirectory);

        Path committedLegacyImage = legacyDirectory.resolve("legacy-report.jpg");
        Path unreferencedLegacyImage = evidenceDirectory.resolve("older-report.jpg");
        String committedGeneratedKey = UUID.randomUUID() + ".png";
        String orphanGeneratedKey = UUID.randomUUID() + ".jpg";
        Path committedGeneratedImage = evidenceDirectory.resolve(committedGeneratedKey);
        Path orphanGeneratedImage = evidenceDirectory.resolve(orphanGeneratedKey);
        Path abandonedStage = stagingDirectory.resolve("nested/interrupted.stage");
        Path externalDirectory = temporaryDirectory.resolve("external-directory");
        Path externalMarker = externalDirectory.resolve("preserved.txt");
        Path stagedSymbolicLink = stagingDirectory.resolve("nested/external-link");
        Files.createDirectories(externalDirectory);
        Files.writeString(externalMarker, "keep");
        Files.createSymbolicLink(stagedSymbolicLink, externalDirectory);
        Files.write(committedLegacyImage, new byte[] {1});
        Files.write(unreferencedLegacyImage, new byte[] {2});
        Files.write(committedGeneratedImage, new byte[] {3});
        Files.write(orphanGeneratedImage, new byte[] {4});
        Files.write(abandonedStage, new byte[] {5});

        store.reconcile(List.of(
                new DamageImageReference("archive/legacy-report.jpg", DamageImageFormat.JPEG, 1),
                new DamageImageReference(committedGeneratedKey, DamageImageFormat.PNG, 1)));

        assertTrue(Files.exists(committedLegacyImage));
        assertTrue(Files.exists(unreferencedLegacyImage));
        assertTrue(Files.exists(committedGeneratedImage));
        assertFalse(Files.exists(orphanGeneratedImage));
        assertFalse(Files.exists(abandonedStage));
        assertFalse(Files.exists(stagedSymbolicLink));
        assertTrue(Files.exists(externalMarker));
        assertTrue(Files.isDirectory(stagingDirectory));
    }

    @Test
    void constructorRejectsAStagingDirectoryThatIsAFile() throws IOException {
        Path evidenceDirectory = temporaryDirectory.resolve("damage-evidence");
        Files.createDirectories(evidenceDirectory);
        Files.createFile(evidenceDirectory.resolve(".staging"));

        assertApplicationError(ApplicationErrorCode.IMAGE_STORAGE_FAILURE,
                () -> new FileDamageEvidenceStore(evidenceDirectory));
    }

    @Test
    void stageDetectsJpegContentRegardlessOfSourcePathName() throws IOException {
        FileDamageEvidenceStore store = new FileDamageEvidenceStore(
                temporaryDirectory.resolve("damage-evidence"));
        Path source = temporaryDirectory.resolve("misleading-name.bin");
        Files.write(source, imageBytes("jpeg"));

        StagedDamageImage stagedImage = store.stage(source);
        DamageImageReference reference = store.finalizeImage(stagedImage);

        assertEquals(DamageImageFormat.JPEG, reference.format());
        assertTrue(reference.storageKey().endsWith(".jpg"));
    }

    private static byte[] imageBytes(String format) throws IOException {
        BufferedImage image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        assertTrue(ImageIO.write(image, format, output));
        return output.toByteArray();
    }

    private static byte[] imageBytes(int width, int height, int imageType) throws IOException {
        return imageBytes("PNG", width, height, imageType);
    }

    private static byte[] imageBytes(String format, int width, int height, int imageType)
            throws IOException {
        BufferedImage image = new BufferedImage(width, height, imageType);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        assertTrue(ImageIO.write(image, format, output));
        return output.toByteArray();
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError(exception);
        }
    }

    private static long countFinalizedImages(Path evidenceDirectory) throws IOException {
        try (var files = Files.newDirectoryStream(evidenceDirectory, "*.{jpg,png}")) {
            long count = 0;
            for (Path ignored : files) {
                count++;
            }
            return count;
        }
    }

    private static void assertApplicationError(ApplicationErrorCode expectedCode,
            Runnable operation) {
        ApplicationException exception = assertThrows(ApplicationException.class, operation::run);
        assertEquals(expectedCode, exception.errorCode());
    }
}
