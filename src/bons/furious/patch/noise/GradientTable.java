package bons.furious.patch.noise;

import net.minecraft.util.Mth;

/** Exact-arithmetic lazy ImprovedNoise gradient table. No RNG or evaluation-order changes. */
public final class GradientTable {
    private GradientTable() {}

    public static double[] build(byte[] permutation, int[][] gradients) {
        double[] table = new double[256 * 4];
        for (int i = 0; i < 256; i++) {
            int[] gradient = gradients[permutation[i] & 15];
            int at = i << 2;
            table[at] = gradient[0];
            table[at + 1] = gradient[1];
            table[at + 2] = gradient[2];
        }
        return table;
    }

    private static double dot(double[] table, int hash, double x, double y, double z) {
        int at = (hash & 255) << 2;
        return table[at] * x + table[at + 1] * y + table[at + 2] * z;
    }

    public static double sample(byte[] p, double[] table, int ix, int iy, int iz,
                                double x, double y, double z, double fadeY) {
        int a = p[ix & 255] & 255;
        int b = p[(ix + 1) & 255] & 255;
        int aa = p[(a + iy) & 255] & 255;
        int ab = p[(a + iy + 1) & 255] & 255;
        int ba = p[(b + iy) & 255] & 255;
        int bb = p[(b + iy + 1) & 255] & 255;
        double n000 = dot(table, aa + iz, x, y, z);
        double n100 = dot(table, ba + iz, x - 1.0D, y, z);
        double n010 = dot(table, ab + iz, x, y - 1.0D, z);
        double n110 = dot(table, bb + iz, x - 1.0D, y - 1.0D, z);
        double n001 = dot(table, aa + iz + 1, x, y, z - 1.0D);
        double n101 = dot(table, ba + iz + 1, x - 1.0D, y, z - 1.0D);
        double n011 = dot(table, ab + iz + 1, x, y - 1.0D, z - 1.0D);
        double n111 = dot(table, bb + iz + 1, x - 1.0D, y - 1.0D, z - 1.0D);
        return Mth.m_14019_(Mth.m_14197_(x), Mth.m_14197_(fadeY), Mth.m_14197_(z),
                n000, n100, n010, n110, n001, n101, n011, n111);
    }
}
