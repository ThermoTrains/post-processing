package ch.sebastianhaeni.thermotrains.internals;

import ch.sebastianhaeni.thermotrains.util.FileUtil;
import org.apache.commons.imaging.Imaging;
import org.apache.commons.imaging.common.ImageMetadata;
import org.apache.commons.imaging.formats.jpeg.JpegImageMetadata;
import org.apache.commons.imaging.formats.jpeg.exif.ExifRewriter;
import org.apache.commons.imaging.formats.tiff.TiffImageMetadata;
import org.apache.commons.imaging.formats.tiff.constants.ExifTagConstants;
import org.apache.commons.imaging.formats.tiff.write.TiffOutputDirectory;
import org.apache.commons.imaging.formats.tiff.write.TiffOutputSet;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.*;
import java.nio.file.Path;
import java.util.List;

import static ch.sebastianhaeni.thermotrains.util.FileUtil.emptyFolder;

public final class MetadataExtractor {

  private static final Logger LOG = LogManager.getLogger(MetadataExtractor.class);
  private static int _minValue = 0;
  private static double _scale = 1.0;
  private static String _metadata = "";

  private MetadataExtractor() {
    //nop
  }

  public static void exportScaling(String videoFile, String sourceFolder, String outputFolder) {
    emptyFolder(outputFolder);
    getMetadata(videoFile);
    writeMetadata(sourceFolder, outputFolder);
  }

  private static void writeMetadata(String srcFolder, String outputFolder) {
    List<Path> srcFiles = FileUtil.getFiles(srcFolder, "**.jpg");

    for (Path srcFile : srcFiles) {

      File src = srcFile.toFile();
      File dst = FileUtil.getFile(outputFolder, src.getName());

      try (FileOutputStream fos = new FileOutputStream(dst); OutputStream os = new BufferedOutputStream(fos)) {

        TiffOutputSet outputSet = null;
        final ImageMetadata metadata = Imaging.getMetadata(src);
        final JpegImageMetadata jpegMetadata = (JpegImageMetadata) metadata;

        if (null != jpegMetadata) {
          final TiffImageMetadata exif = jpegMetadata.getExif();

          if (null != exif) {
            outputSet = exif.getOutputSet();
          }
        }

        if (null == outputSet) {
          outputSet = new TiffOutputSet();
        }

        final TiffOutputDirectory exifDirectory = outputSet.getOrCreateExifDirectory();
        exifDirectory.removeField(ExifTagConstants.EXIF_TAG_USER_COMMENT);
        exifDirectory.add(ExifTagConstants.EXIF_TAG_USER_COMMENT, _metadata);

        new ExifRewriter().updateExifMetadataLossless(src, os, outputSet);
      }
      catch (Exception e) {
        e.printStackTrace();
      }
    }
  }

  private static void getMetadata(String inputFile) {
    try {
      StringBuffer sb = new StringBuffer();
        Process p = Runtime.getRuntime().exec("exiftool -s -s -s -Comment " + inputFile);
      p.waitFor();
      BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));

      String line = "";
      while ((line = reader.readLine()) != null) {
        sb.append(line + "\n");
      }
      String output = sb.toString().replaceAll("\\n", "");
      String[] values = output.split("/");
      _metadata = output;
      _minValue = Integer.valueOf(values[0]);
      _scale = Double.valueOf(values[1].replace(',', '.'));
      LOG.info("offset " + _minValue);
      LOG.info("_scale " + _scale);
    }
    catch (Exception e) {
      e.printStackTrace();
    }
  }
}
