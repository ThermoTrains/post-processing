package ch.sebastianhaeni.thermotrains.internals;

import ch.sebastianhaeni.thermotrains.internals.geometry.BoundingBox;
import ch.sebastianhaeni.thermotrains.internals.geometry.Line;
import ch.sebastianhaeni.thermotrains.util.FileUtil;
import ch.sebastianhaeni.thermotrains.util.MathUtil;
import org.opencv.core.*;

import javax.annotation.Nonnull;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.ToDoubleFunction;
import java.util.stream.Collectors;

import static ch.sebastianhaeni.thermotrains.util.FileUtil.*;
import static ch.sebastianhaeni.thermotrains.util.MatUtil.crop;
import static org.opencv.core.Core.*;
import static org.opencv.imgcodecs.Imgcodecs.imread;
import static org.opencv.imgproc.Imgproc.*;

/**
 * Tries to find the train contour and rectifies it.
 */
public final class Rectify {

  private static final int FREQUENCY_RESOLUTION = 2;
  private static final int LINE_THRESHOLD = 50;
  private static final double MIN_LINE_LENGTH = 50.0;
  private static final double MAX_LINE_GAP = 30.0;

  private static final int CANNY_THRESHOLD_1 = 10;
  private static final int CANNY_THRESHOLD_2 = 35;

  private static int index = 0;

  private Rectify() {
    // nop
  }

  /**
   * Find the train contour and rectify it.
   */
  public static void transform(@Nonnull String inputFolder, @Nonnull String outputFolder) {
    emptyFolder(outputFolder);

    List<Path> files = getFiles(inputFolder, "**.jpg");
    files.remove(0);
    files.remove(0);

    //enhance image contrast
//    List<Mat> enhancedImages = MotionCrop.getEnhancedImages(files);
//    List<Mat> enhancedImages = files.stream()
//      .map(file -> imread(file.toString()))
//      .collect(Collectors.toList());

    index = 0;

//    Mat background = new Mat(enhancedImages.get(0).rows(), enhancedImages.get(0).cols(), CvType.CV_8UC1, new Scalar(0,0,0));
//    Mat background = enhancedImages.get(0);
//    enhancedImages.remove(0);
//    List<Optional<MarginBox>> polygons = enhancedImages.stream()
//      .map(img -> MotionCrop.findBoundingBox(img, background, .9))
//      .collect(Collectors.toList());
//
//    List<BoundingBox> bboxes = polygons.stream()
//      .map(p -> new BoundingBox(new Point(p.get().getLeft(), p.get().getTop()), new Point(p.get().getRight(), p.get().getTop()), new Point(p.get().getRight(), p.get().getBottom()), new Point(p.get().getLeft(), p.get().getBottom())))
//      .collect(Collectors.toList());
//    List<BoundingBox> polygons = enhancedImages.stream()
//      .map(Rectify::findBoundingBox)
//      .collect(Collectors.toList());

    List<BoundingBox> bboxes = files.stream()
      .map(file -> imread(file.toString()))
      .map(Rectify::findBoundingBox)
      .filter(Optional::isPresent)
      .map(Optional::get)
      .collect(Collectors.toList());

    BoundingBox median = getMedianBox(bboxes);
    BoundingBox rectangle = rectifyBox(median);
    Mat perspectiveTransform = getPerspectiveTransform(median.getMat(), rectangle.getMat());

    for (int i = 1; i <= files.size(); i++) {
      Mat img = imread(files.get(i-1).toString());

      // apply matrix
      warpPerspective(img, img, perspectiveTransform, new Size(img.width(), img.height()));

      saveMat(outputFolder, img, i);
    }
  }

  /**
   * Prepare the img in highlighting the upper edge and lower edge of the car. Then use hough to get the lines from that
   * and return the bounding box of the two lines.
   */
  @Nonnull
  private static Optional<BoundingBox> findBoundingBox(@Nonnull Mat img) {
    Mat w = img.clone();

    Mat gray = new Mat();
    cvtColor(w, gray, COLOR_BGR2GRAY);

//    // give it a good blur
    int sigma = 4;
    int kernelSize = 4 * sigma + 1;
    GaussianBlur(gray, gray, new Size(kernelSize, kernelSize), sigma);
    FileUtil.saveMat("/Users/rlaubscher/projects/bfh/thermotrains/target/steps", gray, "13_blurred" + index);

    int fullHeight = gray.height();
    int height = fullHeight / 3;

    Mat upperPart = crop(gray, 0, 0, fullHeight - height, 0);
    FileUtil.saveMat("/Users/rlaubscher/projects/bfh/thermotrains/target/steps", upperPart, "14_top" + index);
    Mat lowerPart = crop(gray, fullHeight - height, 0, 0, 0);
    FileUtil.saveMat("/Users/rlaubscher/projects/bfh/thermotrains/target/steps", lowerPart, "15_bottom" + index);

    Optional<Line> line1 = getLine(upperPart)
      .map(line -> line.expand(0, img.width(), 0, fullHeight));
    Optional<Line> line2 = getLine(lowerPart)
      .map(line -> line
        .translate(0, fullHeight - height)
        .expand(0, img.width(), 0, fullHeight));

    index++;

    if (!line1.isPresent() || !line2.isPresent()) {
      return Optional.empty();
    }

    return Optional.of(new BoundingBox(line1.get(), line2.get()));
  }

  /**
   * Gets the strongest line based on y frequency changes.
   */
  @Nonnull
  private static Optional<Line> getLine(@Nonnull Mat src) {
//    Mat lines = findMaxYFrequency(src);
    Mat freq = findMaxYFrequency(src);
    FileUtil.saveMat("/Users/rlaubscher/projects/bfh/thermotrains/target/steps", freq, "16_maxFreq" + index);

    // canny edge filter
    Mat edge = new Mat();
    Canny(src, edge, CANNY_THRESHOLD_1, CANNY_THRESHOLD_2);
    FileUtil.saveMat("/Users/rlaubscher/projects/bfh/thermotrains/target/steps", edge, "16_canny" + index);


//    HoughLinesP(lines, lines, 1.0, Math.PI / 180, LINE_THRESHOLD, MIN_LINE_LENGTH, MAX_LINE_GAP);
    Mat lines = new Mat();
    HoughLines(edge, lines, 1.0, Math.PI / 180, LINE_THRESHOLD);

    if (lines.rows() == 0) {
      return Optional.empty();
    }

    Mat srcColor = new Mat(src.rows(), src.cols(), CvType.CV_8UC3);
    cvtColor(src, srcColor, COLOR_GRAY2RGB);

    double maxHyp = -1;
    Line longestLine = new Line(new Point(0.0, 0.0), new Point(0.0, 0.0));

    for (int i = 0; i < lines.cols(); i++) {
      double data[] = lines.get(0, i);
      double rho1 = data[0];
      double theta1 = data[1];
      double cosTheta = Math.cos(theta1);
      double sinTheta = Math.sin(theta1);
      double x0 = cosTheta * rho1;
      double y0 = sinTheta * rho1;
      Point pt1 = new Point(x0 + 10000 * (-sinTheta), y0 + 10000 * cosTheta);
      Point pt2 = new Point(x0 - 10000 * (-sinTheta), y0 - 10000 * cosTheta);
      double x = Math.max(pt1.x, pt2.x) - Math.min(pt1.x, pt2.x);
      double y = Math.max(pt1.y, pt2.y) - Math.min(pt1.y, pt2.y);
      double hyp = Math.hypot(x, y);
      if(hyp > maxHyp) {
        maxHyp = hyp;
        longestLine = new Line(pt1, pt2);
      }
    }

    line(srcColor, longestLine.getP1(), longestLine.getP2(), new Scalar(0, 0, 255), 2);
    FileUtil.saveMat("/Users/rlaubscher/projects/bfh/thermotrains/target/steps", srcColor, "16_hough" + index);

    //    double[] val = lines.get(0, 0);
//    Point p1 = new Point(val[0] * FREQUENCY_RESOLUTION, val[1] * FREQUENCY_RESOLUTION);
//    Point p2 = new Point(val[2] * FREQUENCY_RESOLUTION, val[3] * FREQUENCY_RESOLUTION);
//
//    cvtColor(src, src, COLOR_GRAY2BGR);
//    line(src, p1, p2, new Scalar(0, 0, 255), 3);
//    FileUtil.saveMat("/Users/rlaubscher/projects/bfh/thermotrains/target/steps", src, "16_hough" + index);

    return Optional.of(longestLine);
  }

  /**
   * Searches for the maximum frequency in y direction in every column and marks it white.
   */
  @Nonnull
  private static Mat findMaxYFrequency(@Nonnull Mat src) {

    Mat w = new Mat();
    resize(src, w, new Size(src.width() / FREQUENCY_RESOLUTION, src.height() / FREQUENCY_RESOLUTION));

    Size diffSize = new Size(w.width(), w.height() - 1);

    Mat top = new Mat(diffSize, CvType.CV_8UC1);
    Mat bottom = new Mat(diffSize, CvType.CV_8UC1);

    w.rowRange(1, w.height() - 1).copyTo(top);
    w.rowRange(2, w.height()).copyTo(bottom);

    Mat diff = new Mat();
    absdiff(top, bottom, diff);

    Mat output = new Mat(diffSize, CvType.CV_8UC1);

    for (int x = 0; x < output.width(); x++) {
      MinMaxLocResult max = minMaxLoc(diff.col(x));
      output.put((int) max.maxLoc.y, x, 255);
    }

    return output;
  }

  @Nonnull
  private static BoundingBox getMedianBox(@Nonnull List<BoundingBox> polygons) {

    Point topLeft = medianPoint(polygons, BoundingBox::getTopLeft);
    Point topRight = medianPoint(polygons, BoundingBox::getTopRight);
    Point bottomLeft = medianPoint(polygons, BoundingBox::getBottomLeft);
    Point bottomRight = medianPoint(polygons, BoundingBox::getBottomRight);

    return new BoundingBox(topLeft, topRight, bottomRight, bottomLeft);
  }

  @Nonnull
  private static Point medianPoint(
    @Nonnull Collection<BoundingBox> polygons,
    @Nonnull Function<BoundingBox, Point> mapper) {

    double x = median(polygons, polygon -> mapper.apply(polygon).x);
    double y = median(polygons, polygon -> mapper.apply(polygon).y);

    return new Point(x, y);
  }

  private static double median(
    @Nonnull Collection<BoundingBox> polygons,
    @Nonnull ToDoubleFunction<? super BoundingBox> mapper) {

    Double[] values = polygons.stream()
      .mapToDouble(mapper)
      .boxed()
      .toArray(Double[]::new);

    return MathUtil.median(values);
  }

  /**
   * Creates a bounding rectangle from the polygon.
   */
  @Nonnull
  private static BoundingBox rectifyBox(@Nonnull BoundingBox polygon) {
    double top = Math.min(polygon.getTopLeft().y, polygon.getTopRight().y);
    double bottom = Math.max(polygon.getBottomLeft().y, polygon.getBottomRight().y);

    return new BoundingBox(
      new Point(polygon.getTopLeft().x, top),
      new Point(polygon.getTopRight().x, top),
      new Point(polygon.getBottomRight().x, bottom),
      new Point(polygon.getBottomLeft().x, bottom)
    );
  }
}
