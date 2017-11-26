package ch.sebastianhaeni.thermotrains.internals;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Iterator;
import java.util.function.Function;
import java.util.function.Predicate;

import javax.annotation.Nonnull;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.stream.ImageInputStream;

import ch.sebastianhaeni.thermotrains.util.Direction;
import com.drew.imaging.ImageMetadataReader;
import com.drew.imaging.ImageProcessingException;
import com.drew.metadata.Directory;
import com.drew.metadata.Metadata;
import com.drew.metadata.Tag;
import com.sun.javafx.iio.ImageMetadata;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.opencv.core.Mat;
import org.opencv.imgproc.Imgproc;
import org.opencv.videoio.VideoCapture;
import org.opencv.videoio.Videoio;
import org.w3c.dom.Node;

import static ch.sebastianhaeni.thermotrains.util.FileUtil.emptyFolder;
import static ch.sebastianhaeni.thermotrains.util.FileUtil.saveMat;
import static org.opencv.core.Core.flip;
import static org.opencv.core.CvType.CV_16UC1;
import static org.opencv.core.CvType.CV_8UC1;
import static org.opencv.imgproc.Imgproc.cvtColor;

public final class ExtractFrames {

  private static final Logger LOG = LogManager.getLogger(ExtractFrames.class);

  private ExtractFrames() {
    // nop
  }

  public static void extractFrames(
    @Nonnull String inputVideoFilename,
    @Nonnull String outputFolder) {

    extractFrames(inputVideoFilename, outputFolder, Direction.FORWARD, 50);
  }

  static void extractFrames(
    @Nonnull String inputVideoFilename,
    @Nonnull String outputFolder,
    @Nonnull Direction direction,
    int framesToExtract) {

    extractFrames(inputVideoFilename, outputFolder, direction, framesToExtract, 1);
  }

  /**
   * Extract n frames in a direction from an input file. The lengthFactor gets multiplied with the video length and only
   * the frames from start this amount of frames will be considered.
   */
  static void extractFrames(
    @Nonnull String inputVideoFilename,
    @Nonnull String outputFolder,
    @Nonnull Direction direction,
    int framesToExtract,
    double lengthFactor) {

    emptyFolder(outputFolder);

    VideoCapture capture = new VideoCapture();

    if (!capture.open(inputVideoFilename)) {
      throw new IllegalStateException("Cannot open the video file");
    }

    boolean isForward = direction == Direction.FORWARD;
    int frameCount = (int) (capture.get(Videoio.CAP_PROP_FRAME_COUNT) * lengthFactor);
    int framesBetween = (frameCount / framesToExtract) + 1;
    int frameCounter = 0;

    Predicate<Integer> termination = isForward ?
      i -> i < frameCount :
      i -> i > 0;
    Function<Integer, Integer> increment = isForward ?
      i -> i + 1 :
      i -> i - 1;

    int i = isForward ? 0 : frameCount;

    //GetCompressionParameters
    int minValue = 0;
    double inverseScale = 1.0;

    try {
      StringBuffer sb = new StringBuffer();
      Process p = Runtime.getRuntime().exec("exiftool -s -s -s -Comment " + inputVideoFilename);
      p.waitFor();
      BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));

      String line = "";
      while ((line = reader.readLine())!= null) {
        sb.append(line + "\n");
      }
      String output = sb.toString().replaceAll("\\n", "");
      String[] values = output.split("/");
      minValue = Integer.valueOf(values[0]);
      inverseScale = Double.valueOf(values[1].replace(',', '.'));
      inverseScale = 1 / inverseScale;
      LOG.info(minValue);
      LOG.info(inverseScale);
    }
    catch (Exception e) {
      e.printStackTrace();
    }

    while (termination.test(i) && frameCounter <= framesToExtract) {
      i = increment.apply(i);

      Mat frame = new Mat(512, 640, CV_8UC1);
      boolean success = capture.read(frame);
      if (!success) {
        LOG.warn("Cannot read frame {}", i);
        continue;
      }

      if (i > 1 && (i == 0 || i % framesBetween != 0)) {
        // do not extract every frame, but once in a while so we have a fixed number of frames
        // not correlated to the frame count
        continue;
      }

      if (!isForward) {
        flip(frame, frame, 1);
      }

      Mat gray = new Mat();
      cvtColor(frame, gray, Imgproc.COLOR_BGR2GRAY);

      Mat denormalized = new Mat();

      gray.convertTo(denormalized, CV_16UC1, inverseScale, minValue);

      StringBuffer sb = new StringBuffer();
//      sb.append("[");
      for (int row = 0; row < gray.rows(); row++) {
        for (int col = 0; col < gray.cols(); col++) {
          double[] data = gray.get(row, col);
          sb.append(data[0]);
          sb.append(" ");
        }
        sb.append("\n");
      }
//      sb.append("]");
//      LOG.info(sb.toString());

      try(  PrintWriter out = new PrintWriter( "target/steps/gray_raw_data.txt" )  ){
        out.println( sb.toString() );
        out.close();
      }
      catch (FileNotFoundException e) {
        e.printStackTrace();
      }

      saveMat(outputFolder, gray, ++frameCounter);
    }
  }
}
