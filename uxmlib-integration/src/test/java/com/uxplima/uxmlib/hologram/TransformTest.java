package com.uxplima.uxmlib.hologram;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

/** Pure tests of the friendly Transform -> native Transformation mapping. */
class TransformTest {

    @Test
    void uniformScaleSetsAllAxes() {
        Transform t = Transform.scale(2.0f);
        assertThat(t.scaleX()).isEqualTo(2.0f);
        assertThat(t.scaleY()).isEqualTo(2.0f);
        assertThat(t.scaleZ()).isEqualTo(2.0f);
        assertThat(t.yawDegrees()).isZero();
    }

    @Test
    void rotationKeepsDefaultScale() {
        Transform t = Transform.rotation(90f);
        assertThat(t.scaleX()).isEqualTo(1.0f);
        assertThat(t.yawDegrees()).isEqualTo(90f);
    }

    @Test
    void withScaleAndWithYawCompose() {
        Transform t = Transform.NONE.withScale(3f).withYaw(45f);
        assertThat(t.scaleX()).isEqualTo(3f);
        assertThat(t.yawDegrees()).isEqualTo(45f);
    }

    @Test
    void mapsToNativeTransformationScale() {
        var bukkit = Transform.scale(2.5f).toBukkit();
        assertThat(bukkit.getScale().x()).isEqualTo(2.5f);
        assertThat(bukkit.getScale().y()).isEqualTo(2.5f);
    }

    @Test
    void perAxisScaleSetsEachAxisIndependently() {
        Transform t = Transform.scale(2f, 3f, 4f);
        assertThat(t.scaleX()).isEqualTo(2f);
        assertThat(t.scaleY()).isEqualTo(3f);
        assertThat(t.scaleZ()).isEqualTo(4f);
        assertThat(t.yawDegrees()).isZero();
    }

    @Test
    void perAxisScaleMapsToNativeTransformation() {
        var bukkit = Transform.scale(2f, 3f, 4f).toBukkit();
        assertThat(bukkit.getScale().x()).isEqualTo(2f);
        assertThat(bukkit.getScale().y()).isEqualTo(3f);
        assertThat(bukkit.getScale().z()).isEqualTo(4f);
    }

    @Test
    void translationOffsetsTheNativeTransformation() {
        Transform t = Transform.translation(1.5f, -2f, 0.25f);
        assertThat(t.transX()).isEqualTo(1.5f);
        assertThat(t.transY()).isEqualTo(-2f);
        assertThat(t.transZ()).isEqualTo(0.25f);
        var bukkit = t.toBukkit();
        assertThat(bukkit.getTranslation().x()).isEqualTo(1.5f);
        assertThat(bukkit.getTranslation().y()).isEqualTo(-2f);
        assertThat(bukkit.getTranslation().z()).isEqualTo(0.25f);
        // A bare translation leaves scale at the default 1.0 on every axis.
        assertThat(bukkit.getScale().x()).isEqualTo(1f);
    }

    @Test
    void withTranslationKeepsScaleAndRotation() {
        Transform t = Transform.NONE.withScale(2f).withYaw(45f).withTranslation(0f, 1f, 0f);
        assertThat(t.scaleX()).isEqualTo(2f);
        assertThat(t.yawDegrees()).isEqualTo(45f);
        assertThat(t.transY()).isEqualTo(1f);
    }

    @Test
    void noneCarriesZeroTranslation() {
        assertThat(Transform.NONE.transX()).isZero();
        assertThat(Transform.NONE.transY()).isZero();
        assertThat(Transform.NONE.transZ()).isZero();
    }

    @Test
    void rotationWithPitchKeepsDefaultScaleAndBothAngles() {
        Transform t = Transform.rotation(90f, 30f);
        assertThat(t.scaleX()).isEqualTo(1.0f);
        assertThat(t.yawDegrees()).isEqualTo(90f);
        assertThat(t.pitchDegrees()).isEqualTo(30f);
    }

    @Test
    void noneAndYawOnlyRotationCarryZeroPitch() {
        assertThat(Transform.NONE.pitchDegrees()).isZero();
        assertThat(Transform.rotation(45f).pitchDegrees()).isZero();
    }

    @Test
    void withPitchKeepsTheOtherFields() {
        Transform t = Transform.NONE.withScale(2f).withYaw(15f).withPitch(40f);
        assertThat(t.scaleX()).isEqualTo(2f);
        assertThat(t.yawDegrees()).isEqualTo(15f);
        assertThat(t.pitchDegrees()).isEqualTo(40f);
    }

    @Test
    void aPitchedTransformStillMapsItsScale() {
        var bukkit = Transform.rotation(20f, 50f).withScale(1.5f).toBukkit();
        assertThat(bukkit.getScale().x()).isEqualTo(1.5f);
        assertThat(bukkit.getScale().y()).isEqualTo(1.5f);
        assertThat(bukkit.getScale().z()).isEqualTo(1.5f);
    }

    @Test
    void composesYawThenPitchSoTheTiltFollowsTheFacing() {
        // The intent is "turn the display by yaw, then tilt it by pitch about its now-local X". With a
        // 90-degree yaw and a 30-degree pitch the display's up vector must lean within the XY plane (its Z
        // component stays zero): the swapped Rx*Ry order would instead lean the up vector onto +Z, which is
        // the gimbal-locked result we must never ship. Pinning the rotated basis catches a flipped order.
        var rotation = Transform.rotation(90f, 30f).toBukkit().getLeftRotation();

        Vector3f up = rotation.transform(new Vector3f(0f, 1f, 0f));
        assertThat(up.x()).isCloseTo(0.5f, within(1.0e-5f));
        assertThat(up.y()).isCloseTo(0.866025f, within(1.0e-5f));
        assertThat(up.z()).isCloseTo(0f, within(1.0e-5f));

        Vector3f right = rotation.transform(new Vector3f(1f, 0f, 0f));
        assertThat(right.x()).isCloseTo(0f, within(1.0e-5f));
        assertThat(right.y()).isCloseTo(0f, within(1.0e-5f));
        assertThat(right.z()).isCloseTo(-1f, within(1.0e-5f));
    }
}
