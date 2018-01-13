package ch.sebastianhaeni.thermotrains.internals;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.ToIntFunction;
import java.util.stream.IntStream;

import javax.annotation.Nonnull;

import ch.sebastianhaeni.thermotrains.internals.geometry.MarginBox;
import ch.sebastianhaeni.thermotrains.util.FileUtil;
import ch.sebastianhaeni.thermotrains.util.MatUtil;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.opencv.core.*;
import org.opencv.imgproc.CLAHE;
import org.opencv.imgproc.Imgproc;

import static ch.sebastianhaeni.thermotrains.util.FileUtil.emptyFolder;
import static ch.sebastianhaeni.thermotrains.util.FileUtil.saveMat;
import static ch.sebastianhaeni.thermotrains.util.MathUtil.median;
import static org.opencv.core.Core.absdiff;
import static org.opencv.imgcodecs.Imgcodecs.imread;
import static org.opencv.imgproc.Imgproc.*;

public final class MotionCrop {

  private static final Logger LOG = LogManager.getLogger(MotionCrop.class);
  private static int index = 0;

  private MotionCrop() {
    // nop
  }

  public static void cropToMotion(@Nonnull String inputFolder, @Nonnull String outputFolder) {
    emptyFolder(outputFolder);

    List<Path> inputFiles = FileUtil.getFiles(inputFolder, "**.jpg");

    //enhance image contrast
    List<Mat> enhancedImages = getEnhancedImages(inputFiles);

    index = 0;
//    Mat background = MatUtil.background("/Users/rlaubscher/projects/bfh/post-processing/target/steps/07_histeq0.jpg");
    Mat background = enhancedImages.get(0);
//    Mat background = new Mat(enhancedImages.get(0).rows(), enhancedImages.get(0).cols(), CvType.CV_8UC1, new Scalar(0,0,0));
    Map<Integer, MarginBox> bboxes = new HashMap<>();

    for (int i = 0; i < enhancedImages.size(); i++) {
      Path inputFile = inputFiles.get(i);
      Mat img = enhancedImages.get(i);
      Optional<MarginBox> boundingBox = findBoundingBox(img, background, .9);

      index++;

      if (!boundingBox.isPresent()) {
        LOG.info("Found little to no motion on {}", inputFile);
        continue;
      }

      LOG.info("Found motion in {}", inputFile);

      // save to disk
      bboxes.put(i, boundingBox.get());
    }

    // get median bounding box
    MarginBox medianBox = new MarginBox();
    medianBox.setTop(getMedian(bboxes.values(), MarginBox::getTop));
    medianBox.setBottom(getMedian(bboxes.values(), MarginBox::getBottom));
    medianBox.setLeft(getMedian(bboxes.values(), MarginBox::getLeft));
    medianBox.setRight(getMedian(bboxes.values(), MarginBox::getRight));

    for (int i = 0; i < inputFiles.size(); i++) {

      if (!bboxes.containsKey(i)) {
        // if the key is not present, we found no motion in this file
        continue;
      }

      Path inputFile = inputFiles.get(i);
      Mat img = imread(inputFile.toString());
      img = crop(img, medianBox);

      saveMat(outputFolder, img, i);
    }
  }

  private static int getMedian(@Nonnull Collection<MarginBox> boxes, @Nonnull ToIntFunction<MarginBox> mapper) {
    Integer[] numArray = boxes.stream()
      .mapToInt(mapper)
      .boxed()
      .toArray(Integer[]::new);

    return median(numArray);
  }

  @Nonnull
  static Optional<MarginBox> findBoundingBox(@Nonnull Mat source, @Nonnull Mat background, double minWidthFactor) {
    Mat dst = source.clone();
    Mat gray = new Mat();
//    cvtColor(dst, gray, COLOR_BGR2GRAY);
    gray = dst;

    Mat diff = new Mat();
    Mat t = new Mat();

    FileUtil.saveMat("/Users/rlaubscher/projects/bfh/post-processing/target/steps", background, "08_diff_background" + index);
    FileUtil.saveMat("/Users/rlaubscher/projects/bfh/post-processing/target/steps", gray, "08_diff_gray" + index);
    // compute absolute diff between current frame and first frame
    absdiff(background, gray, diff);
    FileUtil.saveMat("/Users/rlaubscher/projects/bfh/post-processing/target/steps", diff, "08_diff" + index);
    threshold(diff, t, 125.0, 255.0, Imgproc.THRESH_BINARY + Imgproc.THRESH_OTSU);
//    adaptiveThreshold(diff, t, 255.0, Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C, Imgproc.THRESH_BINARY, 9, 2.0);
    FileUtil.saveMat("/Users/rlaubscher/projects/bfh/post-processing/target/steps", t, "09_threshold" + index);

    // erode to get rid of small dots
    int erodeSize = 5;
    Mat erodeElement = getStructuringElement(MORPH_ELLIPSE,
      new Size(2 * erodeSize + 1, 2 * erodeSize + 1),
      new Point(erodeSize, erodeSize));
    erode(t, t, erodeElement);
    FileUtil.saveMat("/Users/rlaubscher/projects/bfh/post-processing/target/steps", t, "10_erode" + index);

    // dilate the threshold image to fill in holes
    int dilateSize = 30;
    Mat dilateElement = getStructuringElement(MORPH_ELLIPSE,
      new Size(2 * dilateSize + 1, 2 * dilateSize + 1),
      new Point(dilateSize, dilateSize));
    dilate(t, t, dilateElement); // TODO this seems to be hogging the CPU hard, is there a way around this?
    FileUtil.saveMat("/Users/rlaubscher/projects/bfh/post-processing/target/steps", t, "11_dilate" + index);

    // find contours
    List<MatOfPoint> contours = new ArrayList<>();
    Mat hierarchy = new Mat();
    findContours(t, contours, hierarchy, RETR_EXTERNAL, CHAIN_APPROX_SIMPLE);

    if (contours.isEmpty()) {
      // no contours, so we purge
      return Optional.empty();
    }

    Mat contourImg = new Mat(t.size(), t.type());
    cvtColor(t, t, COLOR_GRAY2BGR);
    for (int i = 0; i < contours.size(); i++) {
//      Imgproc.drawContours(contourImg, contours, i, new Scalar(0, 0, 255), 1);
      Imgproc.drawContours(t, contours, i, new Scalar(0, 0, 255), 3);
    }
    FileUtil.saveMat("/Users/rlaubscher/projects/bfh/post-processing/target/steps", t, "12_contour" + index);

    MatOfPoint largestContour = getLargestContour(contours);

    // find bounding box of contour
    MarginBox bbox = new MarginBox();
    bbox.setTop(streamCoordinates(largestContour, 1).min().orElse(0));
    bbox.setBottom(streamCoordinates(largestContour, 1).max().orElse(dst.height()));
    bbox.setLeft(streamCoordinates(largestContour, 0).min().orElse(0));
    bbox.setRight(streamCoordinates(largestContour, 0).max().orElse(dst.width()));

    if (bbox.getRight() - bbox.getLeft() < (dst.width() * minWidthFactor)) {
      // => the motion area covers not almost the whole width
      // this can be one of the following reasons
      // - it's the start of the train
      // - it's the end of the train
      // - a bird flew over the empty background
      return Optional.empty();
    }

    return Optional.of(bbox);
  }
  /**
   * Crops the {@link Mat} to the bounding box.
   */
  @Nonnull
  private static MatOfPoint getLargestContour(@Nonnull List<MatOfPoint> contours) {
    double max = 0;
    int maxIndex = 0;
    for (int i = 0; i < contours.size(); i++) {
      double area = contourArea( contours.get(i) );
      if(area > max) {
        max = area;
        maxIndex = i;
      }
    }
    return contours.get(maxIndex);
  }

  /**
   * Crops the {@link Mat} to the bounding box.
   */
  @Nonnull
  private static Mat crop(@Nonnull Mat mat, @Nonnull MarginBox bbox) {
    Rect roi = new Rect(bbox.getLeft(),
      bbox.getTop(),
      bbox.getRight() - bbox.getLeft(),
      bbox.getBottom() - bbox.getTop());

    return new Mat(mat, roi);
  }

  /**
   * Returns a stream of contour points with the given index.
   */
  @Nonnull
  private static IntStream streamCoordinates(@Nonnull MatOfPoint contour, int index) {
    return IntStream.rangeClosed(0, contour.rows() - 1)
      .map(i -> (int) contour.get(i, 0)[index]);
  }

  /**
   * Returns a list of contrast enhanced images
   */
  @Nonnull
  public static List<Mat> getEnhancedImages(@Nonnull List<Path> inputFiles) {
    int index = 0;
    List<Mat> enhancedImages = new ArrayList<>();
    for (int i = 0; i < inputFiles.size(); i++) {
      Path inputFile = inputFiles.get(i);
      Mat img = imread(inputFile.toString());
      Mat gray = new Mat();
      cvtColor(img, gray, Imgproc.COLOR_BGR2GRAY);
      CLAHE clahe = Imgproc.createCLAHE(3.0, new Size(8.0, 8.0));
      Mat histEq = new Mat(gray.rows(), gray.cols(), CvType.CV_8UC1);
      clahe.apply(gray, histEq);
      //      Imgproc.equalizeHist(histEq, histEq);
      FileUtil.saveMat("/Users/rlaubscher/projects/bfh/post-processing/target/steps", histEq, "07_histeq" + index);
      enhancedImages.add(histEq);
      index++;
    }
    return enhancedImages;
  }
}
