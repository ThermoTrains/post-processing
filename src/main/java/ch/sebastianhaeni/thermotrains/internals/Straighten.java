package ch.sebastianhaeni.thermotrains.internals;

import ch.sebastianhaeni.thermotrains.util.FileUtil;
import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import javax.annotation.Nonnull;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.DoubleStream;

import static ch.sebastianhaeni.thermotrains.util.FileUtil.emptyFolder;
import static ch.sebastianhaeni.thermotrains.util.FileUtil.saveMat;
import static org.opencv.imgcodecs.Imgcodecs.imread;
import static org.opencv.imgproc.Imgproc.*;

public final class Straighten {

  private static final int TRACK_THRESH = 200;
  private static final int THRESHOLD_1 = 10;
  private static final int THRESHOLD_2 = 70;
  private static final int HOUGH_THRESHOLD = 100;
  private static final double MIN_LINE_LENGTH = 100.0;
  private static final double MAX_LINE_GAP = 20.0;

  private static int index = 0;

  private Straighten() {
    // nop
  }

  public static void straighten(@Nonnull String inputFolder, @Nonnull String outputFolder) {
    emptyFolder(outputFolder);

    int i = 0;
    List<Path> inputFiles = FileUtil.getFiles(inputFolder, "**.jpg");

    for (Path inputFile : inputFiles) {
      Mat img = imread(inputFile.toString());
      Mat dst = new Mat();
      straighten(img, dst);

      // save to disk
      saveMat(outputFolder, dst, ++i);
    }
  }

  private static void straighten(@Nonnull Mat source, @Nonnull Mat destination) {
    Mat srcGray = new Mat();

    // convert to gray scale
    cvtColor(source, srcGray, Imgproc.COLOR_BGR2GRAY);
    FileUtil.saveMat("/Users/rlaubscher/projects/bfh/thermotrains/target/steps", srcGray, "gray");

    // only allow train track pixels, set rest to white
    maskTrainTracks(srcGray);

    // find lines using hough transform
    Mat lines = findLines(srcGray);

    // calculate angle by averaging line angles
    double[] angles = new double[lines.rows()];
    for (int i = 0; i < lines.rows(); i++) {
      double[] val = lines.get(i, 0);

      angles[i] = calculateAngle(val[0], val[1], val[2], val[3]);
    }

    double angle = DoubleStream.of(angles).average().orElse(0.0);

    Point center = new Point(source.cols() / 2, source.rows() / 2);
    Mat rotationMatrix = getRotationMatrix2D(center, -angle, 1.0);

    // rotate
    warpAffine(source, destination, rotationMatrix, source.size());
  }

  /**
   * Leaves only pixels where train tracks are. The rest is set to white.
   */
  private static void maskTrainTracks(@Nonnull Mat srcGray) {
    threshold(srcGray, srcGray, TRACK_THRESH, 255, THRESH_BINARY_INV);
    FileUtil.saveMat("/Users/rlaubscher/projects/bfh/thermotrains/target/steps", srcGray, "treshold");
  }

  /**
   * Finds lines with hough in the image.
   */
  @Nonnull
  private static Mat findLines(@Nonnull Mat srcGray) {
    Mat edges = new Mat();
    Mat lines = new Mat();
    int kernelSize = 3 * 2;

    blur(srcGray, srcGray, new Size(kernelSize, kernelSize));
    FileUtil.saveMat("/Users/rlaubscher/projects/bfh/thermotrains/target/steps", srcGray, "blur");
    Canny(srcGray, edges, THRESHOLD_1, THRESHOLD_2);
    FileUtil.saveMat("/Users/rlaubscher/projects/bfh/thermotrains/target/steps", edges, "canny");
    HoughLinesP(edges, lines, 1.0, Math.PI / 180, HOUGH_THRESHOLD, MIN_LINE_LENGTH, MAX_LINE_GAP);

    for (int x = 0; x < lines.cols(); x++)
    {
      double[] vec = lines.get(0, x);
      if(vec != null) {
        double x1 = vec[0], y1 = vec[1], x2 = vec[2], y2 = vec[3];
        Point start = new Point(x1, y1);
        Point end = new Point(x2, y2);

        cvtColor(edges, edges, COLOR_GRAY2RGB);
        line(edges, start, end, new Scalar(0, 0, 255), 3);
      }
    }

    FileUtil.saveMat("/Users/rlaubscher/projects/bfh/thermotrains/target/steps", edges, "hough" + index);
    index++;
//    System.exit(0);

    return lines;
  }

  /**
   * Calculates the gradient angle of a line.
   */
  private static double calculateAngle(double x1, double y1, double x2, double y2) {
    double angle = Math.toDegrees(Math.atan2(x2 - x1, y2 - y1)) - 90;
    // Keep angle between 0 and 360
    angle = angle + Math.ceil(-angle / 360) * 360;

    return angle;
  }
}
