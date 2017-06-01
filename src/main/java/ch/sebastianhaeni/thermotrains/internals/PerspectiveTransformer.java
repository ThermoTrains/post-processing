package ch.sebastianhaeni.thermotrains.internals;

import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Size;

import java.nio.file.Path;
import java.util.List;

import static ch.sebastianhaeni.thermotrains.util.FileUtil.getFiles;
import static ch.sebastianhaeni.thermotrains.util.FileUtil.saveMat;
import static org.opencv.imgcodecs.Imgcodecs.imread;
import static org.opencv.imgproc.Imgproc.getPerspectiveTransform;
import static org.opencv.imgproc.Imgproc.warpPerspective;

public class PerspectiveTransformer {

  public static void transform(String inputFolder, String outputFolder) {

    // TODO figure out the corner points of the train and write them to src
    Mat src = new Mat(new Size(4, 1), CvType.CV_32FC2);
    // TODO transform them into a rectangle and write them to dst
    Mat dst = new Mat(new Size(4, 1), CvType.CV_32FC2);

    double srcTopLeftX = 613;
    double srcTopLeftY = 74;
    double srcTopRightX = 1732;
    double srcTopRightY = 39;
    double srcBottomRightX = 1740;
    double srcBottomRightY = 532;
    double srcBottomLeftX = 605;
    double srcBottomLeftY = 536;

    src.put(0, 0,
      srcTopLeftX, srcTopLeftY,
      srcTopRightX, srcTopRightY,
      srcBottomRightX, srcBottomRightY,
      srcBottomLeftX, srcBottomLeftY);

    double dstTopLeftX = 640;
    double dstTopLeftY = 46;
    double dstTopRightX = 1732;
    double dstTopRightY = 39;
    double dstBottomRightX = 1740;
    double dstBottomRightY = 532;
    double dstBottomLeftX = 645;
    double dstBottomLeftY = 536;

    dst.put(0, 0,
      dstTopLeftX, dstTopLeftY,
      dstTopRightX, dstTopRightY,
      dstBottomRightX, dstBottomRightY,
      dstBottomLeftX, dstBottomLeftY);

    Mat perspectiveTransform = getPerspectiveTransform(src, dst);

    List<Path> files = getFiles(inputFolder, "**.jpg");

    for (int i = 0; i < files.size(); i++) {
      Path path = files.get(i);

      Mat img = imread(path.toString());
      Mat out = new Mat();
      warpPerspective(img, out, perspectiveTransform, new Size(img.width(), img.height()));

      saveMat(outputFolder, out, i);
    }
  }
}
