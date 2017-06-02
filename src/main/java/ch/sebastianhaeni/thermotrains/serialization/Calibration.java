package ch.sebastianhaeni.thermotrains.serialization;

import java.util.List;

import org.opencv.core.Mat;
import org.opencv.core.Size;

public class Calibration {
  private final Mat cameraMatrix;
  private final Mat distCoeffs;
  private final Size imageSize;
  private final List<Mat> rvecs;
  private final List<Mat> tvecs;

  public Calibration(Mat cameraMatrix, Mat distCoeffs, Size imageSize, List<Mat> rvecs, List<Mat> tvecs) {
    this.cameraMatrix = cameraMatrix;
    this.distCoeffs = distCoeffs;
    this.imageSize = imageSize;
    this.rvecs = rvecs;
    this.tvecs = tvecs;
  }

  public Mat getCameraMatrix() {
    return cameraMatrix;
  }

  public Mat getDistCoeffs() {
    return distCoeffs;
  }

  public Size getImageSize() {
    return imageSize;
  }

  public List<Mat> getRvecs() {
    return rvecs;
  }

  public List<Mat> getTvecs() {
    return tvecs;
  }
}
