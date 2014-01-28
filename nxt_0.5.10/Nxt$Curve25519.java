static class Curve25519
{
    public static final int KEY_SIZE = 32;
    public static final byte[] ZERO;
    public static final byte[] PRIME;
    public static final byte[] ORDER;
    private static final int P25 = 33554431;
    private static final int P26 = 67108863;
    private static final byte[] ORDER_TIMES_8;
    private static final long10 BASE_2Y;
    private static final long10 BASE_R2Y;
    
    public static final void clamp(final byte[] k) {
        final int n = 31;
        k[n] &= 0x7F;
        final int n2 = 31;
        k[n2] |= 0x40;
        final int n3 = 0;
        k[n3] &= 0xF8;
    }
    
    public static final void keygen(final byte[] P, final byte[] s, final byte[] k) {
        clamp(k);
        core(P, s, k, null);
    }
    
    public static final void curve(final byte[] Z, final byte[] k, final byte[] P) {
        core(Z, null, k, P);
    }
    
    public static final boolean sign(final byte[] v, final byte[] h, final byte[] x, final byte[] s) {
        final byte[] tmp1 = new byte[65];
        final byte[] tmp2 = new byte[33];
        for (int i = 0; i < 32; ++i) {
            v[i] = 0;
        }
        int i = mula_small(v, x, 0, h, 32, -1);
        mula_small(v, v, 0, Curve25519.ORDER, 32, (15 - v[31]) / 16);
        mula32(tmp1, v, s, 32, 1);
        divmod(tmp2, tmp1, 64, Curve25519.ORDER, 32);
        int w = 0;
        for (i = 0; i < 32; ++i) {
            final int n = w;
            final int n2 = i;
            final byte b = tmp1[i];
            v[n2] = b;
            w = (n | b);
        }
        return w != 0;
    }
    
    public static final void verify(final byte[] Y, final byte[] v, final byte[] h, final byte[] P) {
        final byte[] d = new byte[32];
        final long10[] p = { new long10(), new long10() };
        final long10[] s = { new long10(), new long10() };
        final long10[] yx = { new long10(), new long10(), new long10() };
        final long10[] yz = { new long10(), new long10(), new long10() };
        final long10[] t1 = { new long10(), new long10(), new long10() };
        final long10[] t2 = { new long10(), new long10(), new long10() };
        int vi = 0;
        int hi = 0;
        int di = 0;
        int nvh = 0;
        set(p[0], 9);
        unpack(p[1], P);
        x_to_y2(t1[0], t2[0], p[1]);
        sqrt(t1[0], t2[0]);
        int j = is_negative(t1[0]);
        final long10 long10 = t2[0];
        long10._0 += 39420360L;
        mul(t2[1], Curve25519.BASE_2Y, t1[0]);
        sub(t1[j], t2[0], t2[1]);
        add(t1[1 - j], t2[0], t2[1]);
        cpy(t2[0], p[1]);
        final long10 long2 = t2[0];
        long2._0 -= 9L;
        sqr(t2[1], t2[0]);
        recip(t2[0], t2[1], 0);
        mul(s[0], t1[0], t2[0]);
        sub(s[0], s[0], p[1]);
        final long10 long3 = s[0];
        long3._0 -= 486671L;
        mul(s[1], t1[1], t2[0]);
        sub(s[1], s[1], p[1]);
        final long10 long4 = s[1];
        long4._0 -= 486671L;
        mul_small(s[0], s[0], 1L);
        mul_small(s[1], s[1], 1L);
        for (int i = 0; i < 32; ++i) {
            vi = (vi >> 8 ^ (v[i] & 0xFF) ^ (v[i] & 0xFF) << 1);
            hi = (hi >> 8 ^ (h[i] & 0xFF) ^ (h[i] & 0xFF) << 1);
            nvh = (vi ^ hi ^ -1);
            di = ((nvh & (di & 0x80) >> 7) ^ vi);
            di ^= (nvh & (di & 0x1) << 1);
            di ^= (nvh & (di & 0x2) << 1);
            di ^= (nvh & (di & 0x4) << 1);
            di ^= (nvh & (di & 0x8) << 1);
            di ^= (nvh & (di & 0x10) << 1);
            di ^= (nvh & (di & 0x20) << 1);
            di ^= (nvh & (di & 0x40) << 1);
            d[i] = (byte)di;
        }
        di = ((nvh & (di & 0x80) << 1) ^ vi) >> 8;
        set(yx[0], 1);
        cpy(yx[1], p[di]);
        cpy(yx[2], s[0]);
        set(yz[0], 0);
        set(yz[1], 1);
        set(yz[2], 1);
        vi = 0;
        hi = 0;
        int i = 32;
        while (i-- != 0) {
            vi = (vi << 8 | (v[i] & 0xFF));
            hi = (hi << 8 | (h[i] & 0xFF));
            di = (di << 8 | (d[i] & 0xFF));
            j = 8;
            while (j-- != 0) {
                mont_prep(t1[0], t2[0], yx[0], yz[0]);
                mont_prep(t1[1], t2[1], yx[1], yz[1]);
                mont_prep(t1[2], t2[2], yx[2], yz[2]);
                int k = ((vi ^ vi >> 1) >> j & 0x1) + ((hi ^ hi >> 1) >> j & 0x1);
                mont_dbl(yx[2], yz[2], t1[k], t2[k], yx[0], yz[0]);
                k = ((di >> j & 0x2) ^ (di >> j & 0x1) << 1);
                mont_add(t1[1], t2[1], t1[k], t2[k], yx[1], yz[1], p[di >> j & 0x1]);
                mont_add(t1[2], t2[2], t1[0], t2[0], yx[2], yz[2], s[((vi ^ hi) >> j & 0x2) >> 1]);
            }
        }
        int k = (vi & 0x1) + (hi & 0x1);
        recip(t1[0], yz[k], 0);
        mul(t1[1], yx[k], t1[0]);
        pack(t1[1], Y);
    }
    
    private static final void cpy32(final byte[] d, final byte[] s) {
        for (int i = 0; i < 32; ++i) {
            d[i] = s[i];
        }
    }
    
    private static final int mula_small(final byte[] p, final byte[] q, final int m, final byte[] x, final int n, final int z) {
        int v = 0;
        for (int i = 0; i < n; ++i) {
            v += (q[i + m] & 0xFF) + z * (x[i] & 0xFF);
            p[i + m] = (byte)v;
            v >>= 8;
        }
        return v;
    }
    
    private static final int mula32(final byte[] p, final byte[] x, final byte[] y, final int t, final int z) {
        final int n = 31;
        int w = 0;
        int i;
        for (i = 0; i < t; ++i) {
            final int zy = z * (y[i] & 0xFF);
            w += mula_small(p, p, i, x, 31, zy) + (p[i + 31] & 0xFF) + zy * (x[31] & 0xFF);
            p[i + 31] = (byte)w;
            w >>= 8;
        }
        p[i + 31] = (byte)(w + (p[i + 31] & 0xFF));
        return w >> 8;
    }
    
    private static final void divmod(final byte[] q, final byte[] r, int n, final byte[] d, final int t) {
        int rn = 0;
        int dt = (d[t - 1] & 0xFF) << 8;
        if (t > 1) {
            dt |= (d[t - 2] & 0xFF);
        }
        while (n-- >= t) {
            int z = rn << 16 | (r[n] & 0xFF) << 8;
            if (n > 0) {
                z |= (r[n - 1] & 0xFF);
            }
            z /= dt;
            rn += mula_small(r, r, n - t + 1, d, t, -z);
            q[n - t + 1] = (byte)(z + rn & 0xFF);
            mula_small(r, r, n - t + 1, d, t, -rn);
            rn = (r[n] & 0xFF);
            r[n] = 0;
        }
        r[t - 1] = (byte)rn;
    }
    
    private static final int numsize(final byte[] x, int n) {
        while (n-- != 0 && x[n] == 0) {}
        return n + 1;
    }
    
    private static final byte[] egcd32(final byte[] x, final byte[] y, final byte[] a, final byte[] b) {
        int bn = 32;
        for (int i = 0; i < 32; ++i) {
            x[i] = (y[i] = 0);
        }
        x[0] = 1;
        int an = numsize(a, 32);
        if (an == 0) {
            return y;
        }
        final byte[] temp = new byte[32];
        while (true) {
            int qn = bn - an + 1;
            divmod(temp, b, bn, a, an);
            bn = numsize(b, bn);
            if (bn == 0) {
                return x;
            }
            mula32(y, x, temp, qn, -1);
            qn = an - bn + 1;
            divmod(temp, a, an, b, bn);
            an = numsize(a, an);
            if (an == 0) {
                return y;
            }
            mula32(x, y, temp, qn, -1);
        }
    }
    
    private static final void unpack(final long10 x, final byte[] m) {
        x._0 = ((m[0] & 0xFF) | (m[1] & 0xFF) << 8 | (m[2] & 0xFF) << 16 | (m[3] & 0xFF & 0x3) << 24);
        x._1 = ((m[3] & 0xFF & 0xFFFFFFFC) >> 2 | (m[4] & 0xFF) << 6 | (m[5] & 0xFF) << 14 | (m[6] & 0xFF & 0x7) << 22);
        x._2 = ((m[6] & 0xFF & 0xFFFFFFF8) >> 3 | (m[7] & 0xFF) << 5 | (m[8] & 0xFF) << 13 | (m[9] & 0xFF & 0x1F) << 21);
        x._3 = ((m[9] & 0xFF & 0xFFFFFFE0) >> 5 | (m[10] & 0xFF) << 3 | (m[11] & 0xFF) << 11 | (m[12] & 0xFF & 0x3F) << 19);
        x._4 = ((m[12] & 0xFF & 0xFFFFFFC0) >> 6 | (m[13] & 0xFF) << 2 | (m[14] & 0xFF) << 10 | (m[15] & 0xFF) << 18);
        x._5 = ((m[16] & 0xFF) | (m[17] & 0xFF) << 8 | (m[18] & 0xFF) << 16 | (m[19] & 0xFF & 0x1) << 24);
        x._6 = ((m[19] & 0xFF & 0xFFFFFFFE) >> 1 | (m[20] & 0xFF) << 7 | (m[21] & 0xFF) << 15 | (m[22] & 0xFF & 0x7) << 23);
        x._7 = ((m[22] & 0xFF & 0xFFFFFFF8) >> 3 | (m[23] & 0xFF) << 5 | (m[24] & 0xFF) << 13 | (m[25] & 0xFF & 0xF) << 21);
        x._8 = ((m[25] & 0xFF & 0xFFFFFFF0) >> 4 | (m[26] & 0xFF) << 4 | (m[27] & 0xFF) << 12 | (m[28] & 0xFF & 0x3F) << 20);
        x._9 = ((m[28] & 0xFF & 0xFFFFFFC0) >> 6 | (m[29] & 0xFF) << 2 | (m[30] & 0xFF) << 10 | (m[31] & 0xFF) << 18);
    }
    
    private static final boolean is_overflow(final long10 x) {
        return (x._0 > 67108844L && (x._1 & x._3 & x._5 & x._7 & x._9) == 0x1FFFFFFL && (x._2 & x._4 & x._6 & x._8) == 0x3FFFFFFL) || x._9 > 33554431L;
    }
    
    private static final void pack(final long10 x, final byte[] m) {
        int ld = 0;
        int ud = 0;
        ld = (is_overflow(x) ? 1 : 0) - ((x._9 < 0L) ? 1 : 0);
        ud = ld * -33554432;
        ld *= 19;
        long t = ld + x._0 + (x._1 << 26);
        m[0] = (byte)t;
        m[1] = (byte)(t >> 8);
        m[2] = (byte)(t >> 16);
        m[3] = (byte)(t >> 24);
        t = (t >> 32) + (x._2 << 19);
        m[4] = (byte)t;
        m[5] = (byte)(t >> 8);
        m[6] = (byte)(t >> 16);
        m[7] = (byte)(t >> 24);
        t = (t >> 32) + (x._3 << 13);
        m[8] = (byte)t;
        m[9] = (byte)(t >> 8);
        m[10] = (byte)(t >> 16);
        m[11] = (byte)(t >> 24);
        t = (t >> 32) + (x._4 << 6);
        m[12] = (byte)t;
        m[13] = (byte)(t >> 8);
        m[14] = (byte)(t >> 16);
        m[15] = (byte)(t >> 24);
        t = (t >> 32) + x._5 + (x._6 << 25);
        m[16] = (byte)t;
        m[17] = (byte)(t >> 8);
        m[18] = (byte)(t >> 16);
        m[19] = (byte)(t >> 24);
        t = (t >> 32) + (x._7 << 19);
        m[20] = (byte)t;
        m[21] = (byte)(t >> 8);
        m[22] = (byte)(t >> 16);
        m[23] = (byte)(t >> 24);
        t = (t >> 32) + (x._8 << 12);
        m[24] = (byte)t;
        m[25] = (byte)(t >> 8);
        m[26] = (byte)(t >> 16);
        m[27] = (byte)(t >> 24);
        t = (t >> 32) + (x._9 + ud << 6);
        m[28] = (byte)t;
        m[29] = (byte)(t >> 8);
        m[30] = (byte)(t >> 16);
        m[31] = (byte)(t >> 24);
    }
    
    private static final void cpy(final long10 out, final long10 in) {
        out._0 = in._0;
        out._1 = in._1;
        out._2 = in._2;
        out._3 = in._3;
        out._4 = in._4;
        out._5 = in._5;
        out._6 = in._6;
        out._7 = in._7;
        out._8 = in._8;
        out._9 = in._9;
    }
    
    private static final void set(final long10 out, final int in) {
        out._0 = in;
        out._1 = 0L;
        out._2 = 0L;
        out._3 = 0L;
        out._4 = 0L;
        out._5 = 0L;
        out._6 = 0L;
        out._7 = 0L;
        out._8 = 0L;
        out._9 = 0L;
    }
    
    private static final void add(final long10 xy, final long10 x, final long10 y) {
        xy._0 = x._0 + y._0;
        xy._1 = x._1 + y._1;
        xy._2 = x._2 + y._2;
        xy._3 = x._3 + y._3;
        xy._4 = x._4 + y._4;
        xy._5 = x._5 + y._5;
        xy._6 = x._6 + y._6;
        xy._7 = x._7 + y._7;
        xy._8 = x._8 + y._8;
        xy._9 = x._9 + y._9;
    }
    
    private static final void sub(final long10 xy, final long10 x, final long10 y) {
        xy._0 = x._0 - y._0;
        xy._1 = x._1 - y._1;
        xy._2 = x._2 - y._2;
        xy._3 = x._3 - y._3;
        xy._4 = x._4 - y._4;
        xy._5 = x._5 - y._5;
        xy._6 = x._6 - y._6;
        xy._7 = x._7 - y._7;
        xy._8 = x._8 - y._8;
        xy._9 = x._9 - y._9;
    }
    
    private static final long10 mul_small(final long10 xy, final long10 x, final long y) {
        long t = x._8 * y;
        xy._8 = (t & 0x3FFFFFFL);
        t = (t >> 26) + x._9 * y;
        xy._9 = (t & 0x1FFFFFFL);
        t = 19L * (t >> 25) + x._0 * y;
        xy._0 = (t & 0x3FFFFFFL);
        t = (t >> 26) + x._1 * y;
        xy._1 = (t & 0x1FFFFFFL);
        t = (t >> 25) + x._2 * y;
        xy._2 = (t & 0x3FFFFFFL);
        t = (t >> 26) + x._3 * y;
        xy._3 = (t & 0x1FFFFFFL);
        t = (t >> 25) + x._4 * y;
        xy._4 = (t & 0x3FFFFFFL);
        t = (t >> 26) + x._5 * y;
        xy._5 = (t & 0x1FFFFFFL);
        t = (t >> 25) + x._6 * y;
        xy._6 = (t & 0x3FFFFFFL);
        t = (t >> 26) + x._7 * y;
        xy._7 = (t & 0x1FFFFFFL);
        t = (t >> 25) + xy._8;
        xy._8 = (t & 0x3FFFFFFL);
        xy._9 += t >> 26;
        return xy;
    }
    
    private static final long10 mul(final long10 xy, final long10 x, final long10 y) {
        final long x_0 = x._0;
        final long x_ = x._1;
        final long x_2 = x._2;
        final long x_3 = x._3;
        final long x_4 = x._4;
        final long x_5 = x._5;
        final long x_6 = x._6;
        final long x_7 = x._7;
        final long x_8 = x._8;
        final long x_9 = x._9;
        final long y_0 = y._0;
        final long y_ = y._1;
        final long y_2 = y._2;
        final long y_3 = y._3;
        final long y_4 = y._4;
        final long y_5 = y._5;
        final long y_6 = y._6;
        final long y_7 = y._7;
        final long y_8 = y._8;
        final long y_9 = y._9;
        long t = x_0 * y_8 + x_2 * y_6 + x_4 * y_4 + x_6 * y_2 + x_8 * y_0 + 2L * (x_ * y_7 + x_3 * y_5 + x_5 * y_3 + x_7 * y_) + 38L * (x_9 * y_9);
        xy._8 = (t & 0x3FFFFFFL);
        t = (t >> 26) + x_0 * y_9 + x_ * y_8 + x_2 * y_7 + x_3 * y_6 + x_4 * y_5 + x_5 * y_4 + x_6 * y_3 + x_7 * y_2 + x_8 * y_ + x_9 * y_0;
        xy._9 = (t & 0x1FFFFFFL);
        t = x_0 * y_0 + 19L * ((t >> 25) + x_2 * y_8 + x_4 * y_6 + x_6 * y_4 + x_8 * y_2) + 38L * (x_ * y_9 + x_3 * y_7 + x_5 * y_5 + x_7 * y_3 + x_9 * y_);
        xy._0 = (t & 0x3FFFFFFL);
        t = (t >> 26) + x_0 * y_ + x_ * y_0 + 19L * (x_2 * y_9 + x_3 * y_8 + x_4 * y_7 + x_5 * y_6 + x_6 * y_5 + x_7 * y_4 + x_8 * y_3 + x_9 * y_2);
        xy._1 = (t & 0x1FFFFFFL);
        t = (t >> 25) + x_0 * y_2 + x_2 * y_0 + 19L * (x_4 * y_8 + x_6 * y_6 + x_8 * y_4) + 2L * (x_ * y_) + 38L * (x_3 * y_9 + x_5 * y_7 + x_7 * y_5 + x_9 * y_3);
        xy._2 = (t & 0x3FFFFFFL);
        t = (t >> 26) + x_0 * y_3 + x_ * y_2 + x_2 * y_ + x_3 * y_0 + 19L * (x_4 * y_9 + x_5 * y_8 + x_6 * y_7 + x_7 * y_6 + x_8 * y_5 + x_9 * y_4);
        xy._3 = (t & 0x1FFFFFFL);
        t = (t >> 25) + x_0 * y_4 + x_2 * y_2 + x_4 * y_0 + 19L * (x_6 * y_8 + x_8 * y_6) + 2L * (x_ * y_3 + x_3 * y_) + 38L * (x_5 * y_9 + x_7 * y_7 + x_9 * y_5);
        xy._4 = (t & 0x3FFFFFFL);
        t = (t >> 26) + x_0 * y_5 + x_ * y_4 + x_2 * y_3 + x_3 * y_2 + x_4 * y_ + x_5 * y_0 + 19L * (x_6 * y_9 + x_7 * y_8 + x_8 * y_7 + x_9 * y_6);
        xy._5 = (t & 0x1FFFFFFL);
        t = (t >> 25) + x_0 * y_6 + x_2 * y_4 + x_4 * y_2 + x_6 * y_0 + 19L * (x_8 * y_8) + 2L * (x_ * y_5 + x_3 * y_3 + x_5 * y_) + 38L * (x_7 * y_9 + x_9 * y_7);
        xy._6 = (t & 0x3FFFFFFL);
        t = (t >> 26) + x_0 * y_7 + x_ * y_6 + x_2 * y_5 + x_3 * y_4 + x_4 * y_3 + x_5 * y_2 + x_6 * y_ + x_7 * y_0 + 19L * (x_8 * y_9 + x_9 * y_8);
        xy._7 = (t & 0x1FFFFFFL);
        t = (t >> 25) + xy._8;
        xy._8 = (t & 0x3FFFFFFL);
        xy._9 += t >> 26;
        return xy;
    }
    
    private static final long10 sqr(final long10 x2, final long10 x) {
        final long x_0 = x._0;
        final long x_ = x._1;
        final long x_2 = x._2;
        final long x_3 = x._3;
        final long x_4 = x._4;
        final long x_5 = x._5;
        final long x_6 = x._6;
        final long x_7 = x._7;
        final long x_8 = x._8;
        final long x_9 = x._9;
        long t = x_4 * x_4 + 2L * (x_0 * x_8 + x_2 * x_6) + 38L * (x_9 * x_9) + 4L * (x_ * x_7 + x_3 * x_5);
        x2._8 = (t & 0x3FFFFFFL);
        t = (t >> 26) + 2L * (x_0 * x_9 + x_ * x_8 + x_2 * x_7 + x_3 * x_6 + x_4 * x_5);
        x2._9 = (t & 0x1FFFFFFL);
        t = 19L * (t >> 25) + x_0 * x_0 + 38L * (x_2 * x_8 + x_4 * x_6 + x_5 * x_5) + 76L * (x_ * x_9 + x_3 * x_7);
        x2._0 = (t & 0x3FFFFFFL);
        t = (t >> 26) + 2L * (x_0 * x_) + 38L * (x_2 * x_9 + x_3 * x_8 + x_4 * x_7 + x_5 * x_6);
        x2._1 = (t & 0x1FFFFFFL);
        t = (t >> 25) + 19L * (x_6 * x_6) + 2L * (x_0 * x_2 + x_ * x_) + 38L * (x_4 * x_8) + 76L * (x_3 * x_9 + x_5 * x_7);
        x2._2 = (t & 0x3FFFFFFL);
        t = (t >> 26) + 2L * (x_0 * x_3 + x_ * x_2) + 38L * (x_4 * x_9 + x_5 * x_8 + x_6 * x_7);
        x2._3 = (t & 0x1FFFFFFL);
        t = (t >> 25) + x_2 * x_2 + 2L * (x_0 * x_4) + 38L * (x_6 * x_8 + x_7 * x_7) + 4L * (x_ * x_3) + 76L * (x_5 * x_9);
        x2._4 = (t & 0x3FFFFFFL);
        t = (t >> 26) + 2L * (x_0 * x_5 + x_ * x_4 + x_2 * x_3) + 38L * (x_6 * x_9 + x_7 * x_8);
        x2._5 = (t & 0x1FFFFFFL);
        t = (t >> 25) + 19L * (x_8 * x_8) + 2L * (x_0 * x_6 + x_2 * x_4 + x_3 * x_3) + 4L * (x_ * x_5) + 76L * (x_7 * x_9);
        x2._6 = (t & 0x3FFFFFFL);
        t = (t >> 26) + 2L * (x_0 * x_7 + x_ * x_6 + x_2 * x_5 + x_3 * x_4) + 38L * (x_8 * x_9);
        x2._7 = (t & 0x1FFFFFFL);
        t = (t >> 25) + x2._8;
        x2._8 = (t & 0x3FFFFFFL);
        x2._9 += t >> 26;
        return x2;
    }
    
    private static final void recip(final long10 y, final long10 x, final int sqrtassist) {
        final long10 t0 = new long10();
        final long10 t = new long10();
        final long10 t2 = new long10();
        final long10 t3 = new long10();
        final long10 t4 = new long10();
        sqr(t, x);
        sqr(t2, t);
        sqr(t0, t2);
        mul(t2, t0, x);
        mul(t0, t2, t);
        sqr(t, t0);
        mul(t3, t, t2);
        sqr(t, t3);
        sqr(t2, t);
        sqr(t, t2);
        sqr(t2, t);
        sqr(t, t2);
        mul(t2, t, t3);
        sqr(t, t2);
        sqr(t3, t);
        for (int i = 1; i < 5; ++i) {
            sqr(t, t3);
            sqr(t3, t);
        }
        mul(t, t3, t2);
        sqr(t3, t);
        sqr(t4, t3);
        for (int i = 1; i < 10; ++i) {
            sqr(t3, t4);
            sqr(t4, t3);
        }
        mul(t3, t4, t);
        for (int i = 0; i < 5; ++i) {
            sqr(t, t3);
            sqr(t3, t);
        }
        mul(t, t3, t2);
        sqr(t2, t);
        sqr(t3, t2);
        for (int i = 1; i < 25; ++i) {
            sqr(t2, t3);
            sqr(t3, t2);
        }
        mul(t2, t3, t);
        sqr(t3, t2);
        sqr(t4, t3);
        for (int i = 1; i < 50; ++i) {
            sqr(t3, t4);
            sqr(t4, t3);
        }
        mul(t3, t4, t2);
        for (int i = 0; i < 25; ++i) {
            sqr(t4, t3);
            sqr(t3, t4);
        }
        mul(t2, t3, t);
        sqr(t, t2);
        sqr(t2, t);
        if (sqrtassist != 0) {
            mul(y, x, t2);
        }
        else {
            sqr(t, t2);
            sqr(t2, t);
            sqr(t, t2);
            mul(y, t, t0);
        }
    }
    
    private static final int is_negative(final long10 x) {
        return (int)(((is_overflow(x) || x._9 < 0L) ? 1 : 0) ^ (x._0 & 0x1L));
    }
    
    private static final void sqrt(final long10 x, final long10 u) {
        final long10 v = new long10();
        final long10 t1 = new long10();
        final long10 t2 = new long10();
        add(t1, u, u);
        recip(v, t1, 1);
        sqr(x, v);
        mul(t2, t1, x);
        final long10 long10 = t2;
        --long10._0;
        mul(t1, v, t2);
        mul(x, u, t1);
    }
    
    private static final void mont_prep(final long10 t1, final long10 t2, final long10 ax, final long10 az) {
        add(t1, ax, az);
        sub(t2, ax, az);
    }
    
    private static final void mont_add(final long10 t1, final long10 t2, final long10 t3, final long10 t4, final long10 ax, final long10 az, final long10 dx) {
        mul(ax, t2, t3);
        mul(az, t1, t4);
        add(t1, ax, az);
        sub(t2, ax, az);
        sqr(ax, t1);
        sqr(t1, t2);
        mul(az, t1, dx);
    }
    
    private static final void mont_dbl(final long10 t1, final long10 t2, final long10 t3, final long10 t4, final long10 bx, final long10 bz) {
        sqr(t1, t3);
        sqr(t2, t4);
        mul(bx, t1, t2);
        sub(t2, t1, t2);
        mul_small(bz, t2, 121665L);
        add(t1, t1, bz);
        mul(bz, t1, t2);
    }
    
    private static final void x_to_y2(final long10 t, final long10 y2, final long10 x) {
        sqr(t, x);
        mul_small(y2, x, 486662L);
        add(t, t, y2);
        ++t._0;
        mul(y2, t, x);
    }
    
    private static final void core(final byte[] Px, final byte[] s, final byte[] k, final byte[] Gx) {
        final long10 dx = new long10();
        final long10 t1 = new long10();
        final long10 t2 = new long10();
        final long10 t3 = new long10();
        final long10 t4 = new long10();
        final long10[] x = { new long10(), new long10() };
        final long10[] z = { new long10(), new long10() };
        if (Gx != null) {
            unpack(dx, Gx);
        }
        else {
            set(dx, 9);
        }
        set(x[0], 1);
        set(z[0], 0);
        cpy(x[1], dx);
        set(z[1], 1);
        int i = 32;
        while (i-- != 0) {
            if (i == 0) {
                i = 0;
            }
            int j = 8;
            while (j-- != 0) {
                final int bit1 = (k[i] & 0xFF) >> j & 0x1;
                final int bit2 = ((k[i] & 0xFF) ^ -1) >> j & 0x1;
                final long10 ax = x[bit2];
                final long10 az = z[bit2];
                final long10 bx = x[bit1];
                final long10 bz = z[bit1];
                mont_prep(t1, t2, ax, az);
                mont_prep(t3, t4, bx, bz);
                mont_add(t1, t2, t3, t4, ax, az, dx);
                mont_dbl(t1, t2, t3, t4, bx, bz);
            }
        }
        recip(t1, z[0], 0);
        mul(dx, x[0], t1);
        pack(dx, Px);
        if (s != null) {
            x_to_y2(t2, t1, dx);
            recip(t3, z[1], 0);
            mul(t2, x[1], t3);
            add(t2, t2, dx);
            final long10 long10 = t2;
            long10._0 += 486671L;
            final long10 long2 = dx;
            long2._0 -= 9L;
            sqr(t3, dx);
            mul(dx, t2, t3);
            sub(dx, dx, t1);
            final long10 long3 = dx;
            long3._0 -= 39420360L;
            mul(t1, dx, Curve25519.BASE_R2Y);
            if (is_negative(t1) != 0) {
                cpy32(s, k);
            }
            else {
                mula_small(s, Curve25519.ORDER_TIMES_8, 0, k, 32, -1);
            }
            final byte[] temp1 = new byte[32];
            final byte[] temp2 = new byte[64];
            final byte[] temp3 = new byte[64];
            cpy32(temp1, Curve25519.ORDER);
            cpy32(s, egcd32(temp2, temp3, s, temp1));
            if ((s[31] & 0x80) != 0x0) {
                mula_small(s, s, 0, Curve25519.ORDER, 32, 1);
            }
        }
    }
    
    static {
        ZERO = new byte[] { 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 };
        PRIME = new byte[] { -19, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, 127 };
        ORDER = new byte[] { -19, -45, -11, 92, 26, 99, 18, 88, -42, -100, -9, -94, -34, -7, -34, 20, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 16 };
        ORDER_TIMES_8 = new byte[] { 104, -97, -82, -25, -46, 24, -109, -64, -78, -26, -68, 23, -11, -50, -9, -90, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, -128 };
        BASE_2Y = new long10(39999547L, 18689728L, 59995525L, 1648697L, 57546132L, 24010086L, 19059592L, 5425144L, 63499247L, 16420658L);
        BASE_R2Y = new long10(5744L, 8160848L, 4790893L, 13779497L, 35730846L, 12541209L, 49101323L, 30047407L, 40071253L, 6226132L);
    }
    
    private static final class long10
    {
        public long _0;
        public long _1;
        public long _2;
        public long _3;
        public long _4;
        public long _5;
        public long _6;
        public long _7;
        public long _8;
        public long _9;
        
        public long10() {
            super();
        }
        
        public long10(final long _0, final long _1, final long _2, final long _3, final long _4, final long _5, final long _6, final long _7, final long _8, final long _9) {
            super();
            this._0 = _0;
            this._1 = _1;
            this._2 = _2;
            this._3 = _3;
            this._4 = _4;
            this._5 = _5;
            this._6 = _6;
            this._7 = _7;
            this._8 = _8;
            this._9 = _9;
        }
    }
}
