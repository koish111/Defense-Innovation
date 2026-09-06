package com.example.blockmod.logic;

public class GreatshieldFormulas {

    public static double distance(double damage, double baseBlocks, double referenceDamage) {
        if (!Double.isFinite(damage) || !Double.isFinite(baseBlocks)
                || !Double.isFinite(referenceDamage) || damage <= 0 || baseBlocks <= 0 || referenceDamage <= 0) {
            return 0;
        }
        // Divide before multiplying to remain finite even for very large finite damage.
        double result = baseBlocks * (2 / (1 + referenceDamage / damage));
        return Double.isFinite(result) ? result : 0;
    }

    /**
     * Inverts vanilla's discrete move-then-drag trajectory on a flat, dry surface.
     * The first tick uses ground drag even when the impulse lifts the target.
     * After landing, the remaining horizontal travel is a geometric series.
     * AI input, walls, fluids and unusual surfaces can change the actual distance.
     */
    public static double horizontalSpeed(double distance, double lift, double gravity,
            double groundFriction, double airDrag, double verticalDrag) {
        if (!Double.isFinite(distance) || distance <= 0 || !Double.isFinite(lift) || lift < 0
                || !Double.isFinite(gravity) || gravity < 0
                || !Double.isFinite(groundFriction) || groundFriction <= 0 || groundFriction >= 1
                || !Double.isFinite(airDrag) || airDrag <= 0 || airDrag >= 1
                || !Double.isFinite(verticalDrag) || verticalDrag <= 0 || verticalDrag >= 1) {
            return 0;
        }
        double groundDrag = groundFriction * airDrag;
        if (lift == 0 || gravity == 0) {
            return distance * (1 - groundDrag);
        }
        double height = 0;
        double vy = lift;
        double vx = 1;
        double travel = 0;
        for (int tick = 0; tick < 64; tick++) {
            travel += vx;
            height += vy;
            vx *= tick == 0 ? groundDrag : airDrag;
            if (height <= 0) {
                return distance / (travel + vx / (1 - groundDrag));
            }
            vy = (vy - gravity) * verticalDrag;
        }
        return distance / (travel + vx / (1 - airDrag));
    }

    /**
     * Calibrate against the velocity actually applied. Airborne targets retain
     * their Y and use a finite air-drag response window, their landing height is
     * unknown.
     */
    public static double motionSpeed(double distance, boolean grounded, double appliedY, double gravity,
            double groundFriction, double airDrag, double verticalDrag, int airResponseTicks) {
        if (!Double.isFinite(distance) || distance <= 0 || !Double.isFinite(appliedY)
                || !Double.isFinite(airDrag) || airDrag <= 0 || airDrag >= 1 || airResponseTicks <= 0) {
            return 0;
        }
        if (grounded) {
            return horizontalSpeed(distance, Math.max(0, appliedY), gravity,
                    groundFriction, airDrag, verticalDrag);
        }
        return distance * (1 - airDrag) / (1 - Math.pow(airDrag, airResponseTicks));
    }

    /**
     * Smallest initial speed whose maximum retreat reaches distance within the
     * response window, assuming constant inward movement input. At tick n the
     * projected position is A[n] * initialSpeed - B[n], so each candidate is
     * (distance + B[n]) / A[n]. This is a bounded linear pass.
     */
    public static double opposedGroundSpeed(double distance, double appliedY, double gravity,
            double groundFriction, double airDrag, double verticalDrag, int responseTicks,
            double groundAcceleration, double airAcceleration) {
        if (!Double.isFinite(distance) || distance <= 0 || !Double.isFinite(appliedY)
                || !Double.isFinite(gravity) || gravity < 0
                || !Double.isFinite(groundFriction) || groundFriction <= 0 || groundFriction >= 1
                || !Double.isFinite(airDrag) || airDrag <= 0 || airDrag >= 1
                || !Double.isFinite(verticalDrag) || verticalDrag <= 0 || verticalDrag >= 1
                || !Double.isFinite(groundAcceleration) || groundAcceleration < 0
                || !Double.isFinite(airAcceleration) || airAcceleration < 0 || responseTicks <= 0) {
            return 0;
        }
        double height = 0, vy = Math.max(0, appliedY);
        double velocityFactor = 1, velocityLoss = 0, positionFactor = 0, positionLoss = 0;
        double required = Double.POSITIVE_INFINITY;
        boolean grounded = true;
        for (int tick = 0; tick < responseTicks; tick++) {
            double drag = grounded ? groundFriction * airDrag : airDrag;
            velocityLoss += grounded ? groundAcceleration : airAcceleration;
            positionFactor += velocityFactor;
            positionLoss += velocityLoss;
            required = Math.min(required, (distance + positionLoss) / positionFactor);
            height += vy;
            grounded = height <= 0;
            if (grounded) {
                height = 0;
                vy = 0;
            }
            vy = (vy - gravity) * verticalDrag;
            velocityFactor *= drag;
            velocityLoss *= drag;
        }
        return Double.isFinite(required) ? required : 0;
    }

    /** Cancel inward momentum without stacking repeated outward impulses. */
    public static double outwardAdjustment(double currentOutwardSpeed, double desiredSpeed) {
        if (!Double.isFinite(currentOutwardSpeed) || !Double.isFinite(desiredSpeed) || desiredSpeed <= 0) {
            return 0;
        }
        double result = Math.max(0, desiredSpeed - currentOutwardSpeed);
        return Double.isFinite(result) ? result : 0;
    }

    public static double verticalSpeed(double currentY, boolean grounded, double lift) {
        if (!Double.isFinite(currentY) || !Double.isFinite(lift) || lift < 0) {
            return 0;
        }
        return grounded ? Math.max(currentY, lift) : currentY;
    }
}
