package com.qing.timetap;

import android.os.SystemClock;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.atomic.AtomicBoolean;

final class ChinaTime {
    private static final long NTP_UNIX_DELTA = 2208988800L;
    private static volatile boolean synced = false;
    private static volatile long networkBaseMillis = 0L;
    private static volatile long syncElapsedMillis = 0L;
    private static volatile long lastOffsetMillis = 0L;
    private static volatile long lastDelayMillis = -1L;
    private static volatile long lastSyncWallMillis = 0L;
    private static final AtomicBoolean syncing = new AtomicBoolean(false);

    private ChinaTime() {}

    static boolean isSynced() {
        return synced;
    }

    static long getOffsetMillis() {
        return lastOffsetMillis;
    }

    static long getDelayMillis() {
        return lastDelayMillis;
    }

    static long getLastSyncWallMillis() {
        return lastSyncWallMillis;
    }

    static long nowNetworkMillis() {
        if (!synced) return System.currentTimeMillis();
        return networkBaseMillis + (SystemClock.elapsedRealtime() - syncElapsedMillis);
    }

    static boolean syncNow() {
        if (!syncing.compareAndSet(false, true)) {
            for (int i = 0; i < 30 && syncing.get(); i++) {
                try { Thread.sleep(100L); } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return synced;
                }
            }
            return synced;
        }
        try {
            String[] hosts = {"ntp.aliyun.com", "time1.cloud.tencent.com", "time.cloudflare.com"};
            Exception last = null;
            for (String host : hosts) {
                try {
                    NtpResult result = query(host);
                    long syncElapsed = SystemClock.elapsedRealtime();
                    synced = true;
                    networkBaseMillis = result.serverTimeAtReceiveMs
                            + (syncElapsed - result.receiveElapsedRealtimeMs);
                    syncElapsedMillis = syncElapsed;
                    lastOffsetMillis = result.offsetMs;
                    lastDelayMillis = result.delayMs;
                    lastSyncWallMillis = System.currentTimeMillis();
                    return true;
                } catch (Exception e) {
                    last = e;
                }
            }
            if (last != null) last.printStackTrace();
            return false;
        } finally {
            syncing.set(false);
        }
    }

    private static NtpResult query(String host) throws Exception {
        byte[] data = new byte[48];
        data[0] = 0x23;
        DatagramSocket socket = new DatagramSocket();
        try {
            socket.setSoTimeout(1400);
            InetAddress address = InetAddress.getByName(host);
            long t1 = System.currentTimeMillis();
            writeTimeStamp(data, 40, t1);
            socket.send(new DatagramPacket(data, data.length, address, 123));
            DatagramPacket response = new DatagramPacket(data, data.length);
            socket.receive(response);
            long t4 = System.currentTimeMillis();
            long receiveElapsed = SystemClock.elapsedRealtime();
            long t2 = readTimeStamp(data, 32);
            long t3 = readTimeStamp(data, 40);
            long offset = ((t2 - t1) + (t3 - t4)) / 2L;
            long delay = Math.max(0L, (t4 - t1) - (t3 - t2));
            return new NtpResult(t4 + offset, receiveElapsed, offset, delay);
        } finally {
            socket.close();
        }
    }

    private static void writeTimeStamp(byte[] buffer, int offset, long unixMillis) {
        long seconds = Math.floorDiv(unixMillis, 1000L) + NTP_UNIX_DELTA;
        long millis = Math.floorMod(unixMillis, 1000L);
        long fraction = (millis * 0x100000000L) / 1000L;
        ByteBuffer.wrap(buffer, offset, 8).order(ByteOrder.BIG_ENDIAN)
                .putInt((int) seconds).putInt((int) fraction);
    }

    private static long readTimeStamp(byte[] buffer, int offset) {
        ByteBuffer b = ByteBuffer.wrap(buffer, offset, 8).order(ByteOrder.BIG_ENDIAN);
        long seconds = Integer.toUnsignedLong(b.getInt());
        long fraction = Integer.toUnsignedLong(b.getInt());
        long unixSeconds = seconds - NTP_UNIX_DELTA;
        long millis = (fraction * 1000L) >>> 32;
        return unixSeconds * 1000L + millis;
    }

    private static final class NtpResult {
        final long serverTimeAtReceiveMs;
        final long receiveElapsedRealtimeMs;
        final long offsetMs;
        final long delayMs;

        NtpResult(long serverTimeAtReceiveMs, long receiveElapsedRealtimeMs, long offsetMs, long delayMs) {
            this.serverTimeAtReceiveMs = serverTimeAtReceiveMs;
            this.receiveElapsedRealtimeMs = receiveElapsedRealtimeMs;
            this.offsetMs = offsetMs;
            this.delayMs = delayMs;
        }
    }
}
