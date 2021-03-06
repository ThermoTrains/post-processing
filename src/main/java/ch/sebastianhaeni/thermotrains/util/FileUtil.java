package ch.sebastianhaeni.thermotrains.util;

import java.io.File;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import javax.annotation.Nonnull;

import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.opencv.core.Mat;

import static org.opencv.imgcodecs.Imgcodecs.imwrite;

/**
 * Util functions to handle files.
 */
public final class FileUtil {

  private static final Logger LOG = LogManager.getLogger(FileUtil.class);

  private FileUtil() {
    // nop
  }

  /**
   * Get a list of files in the folder matching the pattern.
   */
  @Nonnull
  public static List<Path> getFiles(@Nonnull String inputFolder, @Nonnull String globPattern) {
    PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:" + globPattern);
    File folder = new File(inputFolder);

    File[] files = folder.listFiles();

    if (files == null) {
      throw new IllegalStateException("Cannot read files from folder: " + inputFolder);
    }

    List<Path> inputFiles = Arrays.stream(files)
      .map(File::toPath)
      .filter(matcher::matches)
      .collect(Collectors.toList());

    if (inputFiles.isEmpty()) {
      LOG.warn("Could not find any files in {} with pattern {}", inputFolder, globPattern);
    }

    Collections.sort(inputFiles);
    return inputFiles;
  }

  /**
   * Saves the {@link Mat} to the folder with the formatted integer index.
   */
  public static void saveMat(@Nonnull String outputFolder, @Nonnull Mat mat, int index) {
    String filename = String.format("%04d.jpg", index);
    File file = getFile(outputFolder, filename);
    imwrite(file.getAbsolutePath(), mat);

    LOG.info("saved {}", file.getAbsoluteFile());
  }

  /**
   * Saves the {@link Mat} to the folder with the given filename. The extension .jpg is automatically added.
   */
  public static void saveMat(@Nonnull String outputFolder, @Nonnull Mat mat, @Nonnull String filename) {
    File file = getFile(outputFolder, filename + ".jpg");
    imwrite(file.getAbsolutePath(), mat);

    LOG.info("saved {}", file.getAbsoluteFile());
  }

  /**
   * Gets the file reference to the given file. If the folder it should be in, doesn't exist yet, it will be created.
   */
  @Nonnull
  public static File getFile(@Nonnull String outputFolder, @Nonnull String filename) {
    File folder = new File(outputFolder);

    if (!folder.exists()) {
      if (!folder.mkdir()) {
        throw new IllegalStateException("Could not create dir " + outputFolder);
      }

      LOG.info("created dir {}", outputFolder);
    }

    return new File(outputFolder, filename);
  }

  /**
   * Clears the given folder of any content.
   */
  public static void emptyFolder(@Nonnull String folder) {

    File folderFile = new File(folder);

    if (!folderFile.exists()) {
      // folder doesn't exist, nothing to clear
      return;
    }

    File[] files = Optional.ofNullable(folderFile.listFiles())
      .orElseThrow(() -> new IllegalStateException("Cannot read files from folder: " + folder));

    Arrays.stream(files)
      .map(file -> new ImmutablePair<>(file, file.delete()))
      .filter(pair -> !pair.getRight())
      .forEach(pair -> LOG.error("Could not delete file: " + pair.getLeft().toString()));
  }
}
