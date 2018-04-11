package fi.elidor.expose;

/**
 * Created by teppo on 7.3.2018.
 */

public class LowPassFilter {
    private double ALPHA = 0.2f;

    private boolean initialized = false;
    public double value = 0.0;

    public LowPassFilter() {}

    public LowPassFilter(double alpha) {
        ALPHA = alpha;
    }

    public  void update(double input) {
        if(!initialized) {
            initialized = true;
            value = input;
        } else {
            value = value + ALPHA * (input - value);
        }
    }

    public void reset() {
        value = 0.0;
        initialized = false;
    }

}
