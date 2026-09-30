package bons.furious.patch.distanthorizons;

import com.seibel.distanthorizons.api.objects.math.DhApiMat4f;

/**
 * Remembers the last input and result of one DhApiMat4f operation (invert, or multiply by a second matrix) for one
 * field of one DhApiRenderParam. When the next call has bit-identical inputs, the remembered result is copied into the
 * matrix instead of being computed again.
 *
 * Inputs are compared by their raw float bits, so NaN payloads and -0.0 count as different values. DhApiMat4f.invert
 * and DhApiMat4f.multiply are deterministic in their inputs (plain float arithmetic, no state), so the copied result
 * is exactly what recomputing would produce, including DH's own behaviour for singular matrices. Like the
 * DhApiRenderParam it belongs to, an instance is not thread-safe.
 */
public final class MatrixMemo {
    private final float[] input = new float[16];
    private final float[] operand = new float[16];
    private final float[] result = new float[16];
    private boolean valid;

    /** Leaves {@code matrix} exactly as {@code matrix.invert()} would. */
    public void invert(DhApiMat4f matrix) {
        if (this.valid && same(this.input, matrix)) {
            load(matrix, this.result);
            return;
        }
        this.valid = false;
        store(this.input, matrix);
        matrix.invert();
        store(this.result, matrix);
        this.valid = true;
    }

    /** Leaves {@code left} exactly as {@code left.multiply(right)} would; {@code right} is not changed. */
    public void multiply(DhApiMat4f left, DhApiMat4f right) {
        if (this.valid && same(this.input, left) && same(this.operand, right)) {
            load(left, this.result);
            return;
        }
        this.valid = false;
        store(this.input, left);
        store(this.operand, right);
        left.multiply(right);
        store(this.result, left);
        this.valid = true;
    }

    private static boolean same(float[] a, DhApiMat4f m) {
        return bits(a[0], m.m00) && bits(a[1], m.m01) && bits(a[2], m.m02) && bits(a[3], m.m03)
                && bits(a[4], m.m10) && bits(a[5], m.m11) && bits(a[6], m.m12) && bits(a[7], m.m13)
                && bits(a[8], m.m20) && bits(a[9], m.m21) && bits(a[10], m.m22) && bits(a[11], m.m23)
                && bits(a[12], m.m30) && bits(a[13], m.m31) && bits(a[14], m.m32) && bits(a[15], m.m33);
    }

    private static boolean bits(float a, float b) {
        return Float.floatToRawIntBits(a) == Float.floatToRawIntBits(b);
    }

    private static void store(float[] a, DhApiMat4f m) {
        a[0] = m.m00; a[1] = m.m01; a[2] = m.m02; a[3] = m.m03;
        a[4] = m.m10; a[5] = m.m11; a[6] = m.m12; a[7] = m.m13;
        a[8] = m.m20; a[9] = m.m21; a[10] = m.m22; a[11] = m.m23;
        a[12] = m.m30; a[13] = m.m31; a[14] = m.m32; a[15] = m.m33;
    }

    private static void load(DhApiMat4f m, float[] a) {
        m.m00 = a[0]; m.m01 = a[1]; m.m02 = a[2]; m.m03 = a[3];
        m.m10 = a[4]; m.m11 = a[5]; m.m12 = a[6]; m.m13 = a[7];
        m.m20 = a[8]; m.m21 = a[9]; m.m22 = a[10]; m.m23 = a[11];
        m.m30 = a[12]; m.m31 = a[13]; m.m32 = a[14]; m.m33 = a[15];
    }
}
