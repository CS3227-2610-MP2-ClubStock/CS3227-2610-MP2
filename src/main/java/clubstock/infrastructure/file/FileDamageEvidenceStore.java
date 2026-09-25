package clubstock.infrastructure.file;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;
import java.util.regex.Pattern;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

import clubstock.application.ApplicationErrorCode;
import clubstock.application.ApplicationException;
import clubstock.application.port.ManagedDamageImageStore;
import clubstock.application.port.StagedDamageImage;
import clubstock.application.verification.DamageEvidence;
import clubstock.domain.report.DamageImageFormat;
import clubstock.domain.report.DamageImageReference;

/**
 * Stores validated damage images under an application-owned directory.
 */
public final class FileDamageEvidenceStore implements ManagedDamageImageStore {
    private static final long MAX_SIZE_BYTES = 5L * 1024 * 1024;
    private static final String STAGING_DIRECTORY_NAME = ".staging";
    private static final String OPERATION_LOCK_FILENAME = ".image-operations.lock";
    private static final ConcurrentMap<Path, ReentrantLock> JVM_OPERATION_LOCKS =
            new ConcurrentHashMap<>();
    private static final Pattern GENERATED_IMAGE_KEY = Pattern.compile(
            "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.(jpg|png)");
    private final Path root;
    private final Path stagingDirectory;
    private final Path operationLockFile;
    private final ReentrantLock jvmOperationLock;

    /**
     * Creates a store rooted at an application-managed directory.
     *
     * @param root Root directory for managed damage evidence.
     */
    public FileDamageEvidenceStore(Path root) {
        if (root == null) {
            throw new IllegalArgumentException("Damage evidence directory cannot be null.");
        }

        Path normalizedRoot = root.toAbsolutePath().normalize();
        try {
            if (Files.isSymbolicLink(normalizedRoot)) {
                throw storageFailure(null);
            }
            Files.createDirectories(normalizedRoot);
            if (Files.isSymbolicLink(normalizedRoot)
                    || !Files.isDirectory(normalizedRoot, LinkOption.NOFOLLOW_LINKS)) {
                throw storageFailure(null);
            }
            this.root = normalizedRoot.toRealPath();
            stagingDirectory = this.root.resolve(STAGING_DIRECTORY_NAME);
            operationLockFile = this.root.resolve(OPERATION_LOCK_FILENAME);
            jvmOperationLock = JVM_OPERATION_LOCKS.computeIfAbsent(this.root,
                    ignored -> new ReentrantLock());
            if (Files.isSymbolicLink(stagingDirectory)) {
                throw storageFailure(null);
            }
            Files.createDirectories(stagingDirectory);
            requireManagedDirectories();
        } catch (IOException | SecurityException exception) {
            throw storageFailure(exception);
        }
    }

    @Override
    public <T> T withExclusiveAccess(Supplier<T> operation) {
        if (operation == null) {
            throw new IllegalArgumentException("Exclusive image operation cannot be null.");
        }

        boolean alreadyHeld = jvmOperationLock.isHeldByCurrentThread();
        jvmOperationLock.lock();
        try {
            if (alreadyHeld) {
                return operation.get();
            }
            try (FileChannel channel = FileChannel.open(operationLockFile,
                    StandardOpenOption.CREATE, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS);
                    FileLock fileLock = channel.lock()) {
                return operation.get();
            } catch (IOException | SecurityException exception) {
                throw storageFailure(exception);
            }
        } finally {
            jvmOperationLock.unlock();
        }
    }

    @Override
    public StagedDamageImage stage(Path sourcePath) {
        if (sourcePath == null) {
            throw new IllegalArgumentException("Damage image source cannot be null.");
        }

        byte[] imageBytes = readSelectedImage(sourcePath);
        DamageImageFormat format = decodeFormat(imageBytes);
        if (format == null) {
            throw invalidImage();
        }

        requireManagedDirectories();
        String token = UUID.randomUUID().toString();
        Path stagedPath = stagedPath(token);
        try {
            Files.write(stagedPath, imageBytes, StandardOpenOption.CREATE_NEW,
                    StandardOpenOption.WRITE);
            return new StagedDamageImage(token, format, imageBytes.length);
        } catch (IOException | SecurityException exception) {
            try {
                Files.deleteIfExists(stagedPath);
            } catch (IOException | SecurityException cleanupException) {
                exception.addSuppressed(cleanupException);
            }
            throw storageFailure(exception);
        }
    }

    @Override
    public DamageImageReference finalizeImage(StagedDamageImage stagedImage) {
        requireStagedImage(stagedImage);
        requireManagedDirectories();
        Path stagedPath = stagedPath(stagedImage.stagingToken());
        byte[] imageBytes = readStagedImage(stagedPath, stagedImage.sizeBytes());
        DamageImageFormat detectedFormat = decodeFormat(imageBytes);
        if (detectedFormat != stagedImage.format()) {
            throw storageFailure(null);
        }

        String extension = detectedFormat == DamageImageFormat.PNG ? ".png" : ".jpg";
        String storageKey = UUID.randomUUID() + extension;
        Path finalizedPath = root.resolve(storageKey);
        try {
            try {
                Files.move(stagedPath, finalizedPath, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(stagedPath, finalizedPath);
            }
            return new DamageImageReference(storageKey, detectedFormat, imageBytes.length);
        } catch (IOException | SecurityException exception) {
            throw storageFailure(exception);
        }
    }

    @Override
    public void discardStaged(StagedDamageImage stagedImage) {
        requireStagedImage(stagedImage);
        requireManagedDirectories();
        Path stagedPath = stagedPath(stagedImage.stagingToken());
        try {
            Files.deleteIfExists(stagedPath);
        } catch (IOException | SecurityException exception) {
            throw storageFailure(exception);
        }
    }

    @Override
    public void discardFinalized(DamageImageReference reference) {
        if (reference == null) {
            throw new IllegalArgumentException("Damage image reference cannot be null.");
        }
        if (!isGeneratedImageKey(reference.storageKey())) {
            throw new IllegalArgumentException("Only generated damage images can be discarded.");
        }

        requireManagedDirectories();
        Path imagePath = resolveStoredPath(reference.storageKey());
        try {
            Files.deleteIfExists(imagePath);
        } catch (IOException | SecurityException exception) {
            throw storageFailure(exception);
        }
    }

    @Override
    public void reconcile(Collection<DamageImageReference> committedReferences) {
        if (committedReferences == null) {
            throw new IllegalArgumentException("Committed damage image references cannot be null.");
        }
        requireManagedDirectories();

        Set<String> referencedKeys = new HashSet<>();
        for (DamageImageReference reference : committedReferences) {
            if (reference == null) {
                throw new IllegalArgumentException("Committed damage image references cannot contain null.");
            }
            resolveStoredPath(reference.storageKey());
            referencedKeys.add(reference.storageKey());
        }

        clearStagingDirectory();
        try (var files = Files.newDirectoryStream(root)) {
            for (Path file : files) {
                String storageKey = file.getFileName().toString();
                if (isGeneratedImageKey(storageKey) && !referencedKeys.contains(storageKey)) {
                    deleteUnreferencedGeneratedFile(file);
                }
            }
        } catch (IOException | SecurityException exception) {
            throw storageFailure(exception);
        }
    }

    @Override
    public Optional<DamageEvidence> find(DamageImageReference reference) {
        if (reference == null) {
            throw new IllegalArgumentException("Damage image reference cannot be null.");
        }

        requireManagedDirectories();
        Path imagePath = resolveStoredPath(reference.storageKey());
        if (!Files.isRegularFile(imagePath, LinkOption.NOFOLLOW_LINKS)) {
            return Optional.empty();
        }

        try {
            if (Files.size(imagePath) != reference.sizeBytes()) {
                return Optional.empty();
            }
            byte[] imageBytes = Files.readAllBytes(imagePath);
            if (imageBytes.length != reference.sizeBytes()) {
                return Optional.empty();
            }
            return Optional.of(new DamageEvidence(imageBytes, reference.format()));
        } catch (IOException | SecurityException exception) {
            throw storageFailure(exception);
        }
    }

    /**
     * Checks that both managed directories remain real directories rather than symlinks.
     */
    private void requireManagedDirectories() {
        try {
            if (Files.isSymbolicLink(root)
                    || !Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)
                    || !root.toRealPath().equals(root)
                    || Files.isSymbolicLink(stagingDirectory)
                    || !Files.isDirectory(stagingDirectory, LinkOption.NOFOLLOW_LINKS)) {
                throw storageFailure(null);
            }
        } catch (IOException | SecurityException exception) {
            throw storageFailure(exception);
        }
    }

    /**
     * Reads a selected source without retaining more than the supported image-size limit.
     */
    private static byte[] readSelectedImage(Path sourcePath) {
        if (!Files.isRegularFile(sourcePath)) {
            throw invalidImage();
        }

        try {
            long sourceSize = Files.size(sourcePath);
            if (sourceSize < 1 || sourceSize > MAX_SIZE_BYTES) {
                throw invalidImage();
            }

            try (InputStream input = Files.newInputStream(sourcePath);
                    ByteArrayOutputStream output = new ByteArrayOutputStream((int) sourceSize)) {
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = input.read(buffer)) != -1) {
                    if (output.size() + bytesRead > MAX_SIZE_BYTES) {
                        throw invalidImage();
                    }
                    output.write(buffer, 0, bytesRead);
                }
                byte[] imageBytes = output.toByteArray();
                if (imageBytes.length < 1 || imageBytes.length > MAX_SIZE_BYTES) {
                    throw invalidImage();
                }
                return imageBytes;
            }
        } catch (IOException | SecurityException exception) {
            throw invalidImage(exception);
        }
    }

    /**
     * Reads a stage file only when it still matches the staged size.
     */
    private byte[] readStagedImage(Path stagedPath, long expectedSize) {
        try {
            if (Files.isSymbolicLink(stagedPath)
                    || !Files.isRegularFile(stagedPath, LinkOption.NOFOLLOW_LINKS)
                    || Files.size(stagedPath) != expectedSize) {
                throw storageFailure(null);
            }
            byte[] imageBytes = Files.readAllBytes(stagedPath);
            if (imageBytes.length != expectedSize || imageBytes.length > MAX_SIZE_BYTES) {
                throw storageFailure(null);
            }
            return imageBytes;
        } catch (IOException | SecurityException exception) {
            throw storageFailure(exception);
        }
    }

    /**
     * Detects and decodes the first frame of a supported image.
     */
    private static DamageImageFormat decodeFormat(byte[] imageBytes) {
        try (ImageInputStream input = ImageIO.createImageInputStream(
                new ByteArrayInputStream(imageBytes))) {
            if (input == null) {
                return null;
            }

            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                return null;
            }

            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                String formatName = reader.getFormatName();
                DamageImageFormat format;
                if (formatName.equalsIgnoreCase("PNG")) {
                    format = DamageImageFormat.PNG;
                } else if (formatName.equalsIgnoreCase("JPEG")
                        || formatName.equalsIgnoreCase("JPG")) {
                    format = DamageImageFormat.JPEG;
                } else {
                    return null;
                }

                BufferedImage decodedImage = reader.read(0);
                return decodedImage == null ? null : format;
            } finally {
                reader.dispose();
            }
        } catch (IOException | RuntimeException exception) {
            return null;
        }
    }

    /**
     * Resolves a relative key while rejecting traversal and symbolic-link components.
     */
    private Path resolveStoredPath(String storageKey) {
        if (storageKey == null || storageKey.isBlank() || storageKey.startsWith("/")
                || storageKey.startsWith("\\") || storageKey.contains("\\")
                || storageKey.indexOf('\0') >= 0 || isWindowsDriveQualifiedPath(storageKey)) {
            throw new IllegalArgumentException("Damage image reference is not a safe relative key.");
        }

        String[] segments = storageKey.split("/", -1);
        if (segments[0].equals(STAGING_DIRECTORY_NAME)) {
            throw new IllegalArgumentException("Damage image reference uses a reserved storage key.");
        }
        Path imagePath = root;
        for (int index = 0; index < segments.length; index++) {
            String segment = segments[index];
            if (segment.isBlank() || segment.equals(".") || segment.equals("..")) {
                throw new IllegalArgumentException("Damage image reference is not a safe relative key.");
            }
            imagePath = imagePath.resolve(segment);
            if (Files.isSymbolicLink(imagePath)) {
                throw storageFailure(null);
            }
            if (index < segments.length - 1
                    && Files.exists(imagePath, LinkOption.NOFOLLOW_LINKS)
                    && !Files.isDirectory(imagePath, LinkOption.NOFOLLOW_LINKS)) {
                throw storageFailure(null);
            }
        }
        return imagePath;
    }

    /**
     * Removes abandoned stage entries without following symbolic links.
     */
    private void clearStagingDirectory() {
        try {
            Files.walkFileTree(stagingDirectory, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attributes)
                        throws IOException {
                    Files.delete(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path directory, IOException exception)
                        throws IOException {
                    if (exception != null) {
                        throw exception;
                    }
                    if (!directory.equals(stagingDirectory)) {
                        Files.delete(directory);
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException | SecurityException exception) {
            throw storageFailure(exception);
        }
    }

    /**
     * Deletes an unreferenced generated file or symlink without following it.
     */
    private static void deleteUnreferencedGeneratedFile(Path file) throws IOException {
        if (Files.isDirectory(file, LinkOption.NOFOLLOW_LINKS) && !Files.isSymbolicLink(file)) {
            throw storageFailure(null);
        }
        Files.deleteIfExists(file);
    }

    /**
     * Returns the staging path for a validated opaque token.
     */
    private Path stagedPath(String token) {
        return stagingDirectory.resolve(token + ".stage");
    }

    /**
     * Validates the opaque staged-image handle.
     */
    private static void requireStagedImage(StagedDamageImage stagedImage) {
        if (stagedImage == null) {
            throw new IllegalArgumentException("Staged damage image cannot be null.");
        }
    }

    /**
     * Returns whether a key is a generated finalized-image key.
     */
    private static boolean isGeneratedImageKey(String storageKey) {
        return storageKey != null && GENERATED_IMAGE_KEY.matcher(storageKey).matches();
    }

    /**
     * Returns whether a key begins with a Windows drive specifier.
     */
    private static boolean isWindowsDriveQualifiedPath(String storageKey) {
        return storageKey.length() >= 2 && Character.isLetter(storageKey.charAt(0))
                && storageKey.charAt(1) == ':';
    }

    /**
     * Creates an input-validation failure for unsupported or unreadable source images.
     */
    private static ApplicationException invalidImage() {
        return invalidImage(null);
    }

    /**
     * Creates an input-validation failure for unsupported or unreadable source images.
     */
    private static ApplicationException invalidImage(Throwable cause) {
        return new ApplicationException(ApplicationErrorCode.VALIDATION_FAILED,
                "Select a valid JPEG or PNG image between 1 byte and 5 MiB.", cause);
    }

    /**
     * Creates a managed-storage failure without exposing local filesystem details.
     */
    private static ApplicationException storageFailure(Throwable cause) {
        return new ApplicationException(ApplicationErrorCode.IMAGE_STORAGE_FAILURE,
                "Damage evidence storage could not be accessed safely.", cause);
    }
}
