package ch.sebastianhaeni.thermotrains.serialization;

public class Scaling {

  private int minValue;
  private double inverseScale;

  public Scaling(int minValue, double inverseScale) {
    this.minValue = minValue;
    this.inverseScale = inverseScale;
  }

  public int getMinValue() {
    return minValue;
  }

  public double getInverseScale() {
    return inverseScale;
  }
}
