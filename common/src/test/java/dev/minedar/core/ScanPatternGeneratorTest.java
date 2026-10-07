package dev.minedar.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class ScanPatternGeneratorTest {

    @Test
    void directionsAreUnitLengthWithinCone() {
        List<double[]> dirs = ScanPatternGenerator.directions(1.0, 1000);
        double cosLimit = Math.cos(ScanPatternGenerator.CONE_HALF_ANGLE);
        for (double[] d : dirs) {
            double len = Math.sqrt(d[0] * d[0] + d[1] * d[1] + d[2] * d[2]);
            assertEquals(1.0, len, 1e-6);
            assertTrue(d[2] >= cosLimit - 1e-9, "direction outside forward cone");
        }
    }

    @Test
    void largerDensityFactorYieldsMorePoints() {
        int sparse = ScanPatternGenerator.directions(0.25, 100000).size();
        int dense = ScanPatternGenerator.directions(1.0, 100000).size();
        assertTrue(dense > sparse);
    }

    @Test
    void pointCountIsBounded() {
        assertEquals(10, ScanPatternGenerator.directions(1.0, 10).size());
    }

    @Test
    void directionDiversityIsEven() {
        List<double[]> dirs = ScanPatternGenerator.directions(1.0, 200);
        // Golden-angle spiral should cover all quadrants of the cone, not cluster.
        boolean posX = false;
        boolean negX = false;
        boolean posY = false;
        boolean negY = false;
        for (double[] d : dirs) {
            if (d[0] > 0.1) {
                posX = true;
            }
            if (d[0] < -0.1) {
                negX = true;
            }
            if (d[1] > 0.1) {
                posY = true;
            }
            if (d[1] < -0.1) {
                negY = true;
            }
        }
        assertTrue(posX && negX && posY && negY);
    }
}
