package dev.restone0616.ymp.network;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedWriter;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

public final class NetworkUtil {
    private NetworkUtil() {}
    private static final String MARKER_START = "# === YCommunity START ===";
    private static final String MARKER_END = "# === YCommunity END ===";

    private static final Map<String, List<String>> DOMAIN_CANDIDATES = new LinkedHashMap<>();
    static {
        DOMAIN_CANDIDATES.put("github.githubassets.com", Arrays.asList(
                "185.199.111.215",
                "185.199.108.215",
                "185.199.110.215",
                "185.199.109.215"
        ));
        DOMAIN_CANDIDATES.put("github.io", Arrays.asList(
                "185.199.111.215",
                "185.199.108.215",
                "185.199.110.215",
                "185.199.109.215"
        ));
        DOMAIN_CANDIDATES.put("github.com", Arrays.asList(
                "20.205.243.166",
                "140.82.116.4",
                "185.199.108.0",
                "20.27.177.113",
                "20.207.73.82",
                "140.82.116.4",
                "192.30.252.0"
        ));
        DOMAIN_CANDIDATES.put("api.github.com", Arrays.asList(
                "20.205.243.166",
                "140.82.116.4",
                "185.199.108.0",
                "20.27.177.113",
                "20.207.73.82",
                "140.82.116.4",
                "192.30.252.0"
        ));
        DOMAIN_CANDIDATES.put("avatars.githubusercontent.com", Arrays.asList(
                "185.199.108.133",
                "185.199.109.133",
                "185.199.110.133",
                "185.199.111.133"
        ));
        DOMAIN_CANDIDATES.put("avatars0.githubusercontent.com", Arrays.asList(
                "185.199.108.133",
                "185.199.109.133",
                "185.199.110.133",
                "185.199.111.133"
        ));
        DOMAIN_CANDIDATES.put("avatars1.githubusercontent.com", Arrays.asList(
                "185.199.108.133",
                "185.199.109.133",
                "185.199.110.133",
                "185.199.111.133"
        ));
        DOMAIN_CANDIDATES.put("avatars2.githubusercontent.com", Arrays.asList(
                "185.199.108.133",
                "185.199.109.133",
                "185.199.110.133",
                "185.199.111.133"
        ));
        DOMAIN_CANDIDATES.put("avatars3.githubusercontent.com", Arrays.asList(
                "185.199.108.133",
                "185.199.109.133",
                "185.199.110.133",
                "185.199.111.133"
        ));
        DOMAIN_CANDIDATES.put("avatars4.githubusercontent.com", Arrays.asList(
                "185.199.108.133",
                "185.199.109.133",
                "185.199.110.133",
                "185.199.111.133"
        ));
        DOMAIN_CANDIDATES.put("avatars5.githubusercontent.com", Arrays.asList(
                "185.199.108.133",
                "185.199.109.133",
                "185.199.110.133",
                "185.199.111.133"
        ));
        DOMAIN_CANDIDATES.put("raw.githubusercontent.com", Arrays.asList(
                "185.199.108.133",
                "185.199.109.133",
                "185.199.110.133",
                "185.199.111.133"
        ));
        DOMAIN_CANDIDATES.put("alive.github.com", Arrays.asList(
                "140.82.114.25",
                "140.82.112.26",
                "140.82.112.25",
                "140.82.113.25",
                "140.82.113.26",
                "140.82.114.26"
        ));
        DOMAIN_CANDIDATES.put("live.github.com", Arrays.asList(
                "140.82.114.25",
                "140.82.112.26",
                "140.82.112.25",
                "140.82.113.25",
                "140.82.113.26",
                "140.82.114.26"
        ));
        DOMAIN_CANDIDATES.put("central.github.com", Arrays.asList(
                "140.82.113.21",
                "140.82.113.22",
                "140.82.114.21",
                "140.82.112.22",
                "140.82.114.22",
                "140.82.112.21"
        ));
        DOMAIN_CANDIDATES.put("desktop.githubusercontent.com", Arrays.asList(
                "185.199.108.133",
                "185.199.109.133",
                "185.199.110.133",
                "185.199.111.133"
        ));
        DOMAIN_CANDIDATES.put("camo.githubusercontent.com", List.of(
                "185.199.110.133"
        ));
        DOMAIN_CANDIDATES.put("github.map.fastly.net", Arrays.asList(
                "185.199.108.133",
                "185.199.109.133",
                "185.199.110.133",
                "185.199.111.133"
        ));
        DOMAIN_CANDIDATES.put("github.global.ssl.fastly.net", Arrays.asList(
                "31.13.84.2",
                "205.186.152.122",
                "151.101.1.194",
                "103.252.114.11",
                "104.244.46.208",
                "69.63.190.26",
                "202.160.128.96",
                "31.13.67.33",
                "75.126.135.131",
                "151.101.193.194",
                "50.23.209.199",
                "202.160.128.195",
                "199.59.150.49",
                "75.126.124.162",
                "202.160.129.6",
                "66.220.147.11"
        ));
        DOMAIN_CANDIDATES.put("gist.github.com", Arrays.asList(
                "203.98.7.65",
                "46.82.174.68",
                "159.24.3.173",
                "59.24.3.173",
                "20.205.243.166",
                "140.82.116.3"
        ));
        DOMAIN_CANDIDATES.put("collector.github.com", Arrays.asList(
                "140.82.113.21",
                "140.82.114.21",
                "140.82.112.22",
                "140.82.114.22",
                "140.82.112.21"
        ));
    }

    public static void applyGithubHosts() {
        Path hostsPath = getHostsPath();
        List<String> lines;
        try {
            lines = Files.readAllLines(hostsPath);
        } catch (IOException ignored) {
            return;
        }

        if (lines.contains(MARKER_START))
            return;

        try {
            backupHosts(hostsPath);
        } catch (IOException ignored) {
            return;
        }

        Map<String, String> resolved = resolveAllDomains();
        if (resolved.isEmpty()) {
            return;
        }

        try (BufferedWriter writer = Files.newBufferedWriter(hostsPath, StandardOpenOption.APPEND)) {
            writer.newLine();
            writer.write(MARKER_START);
            writer.newLine();
            for (Map.Entry<String, String> entry : resolved.entrySet()) {
                writer.write(entry.getValue() + " " + entry.getKey());
                writer.newLine();
            }
            writer.write(MARKER_END);
            writer.newLine();
        } catch (IOException ignored) {
            return;
        }

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                restoreHosts(hostsPath);
            } catch (IOException ignored) {}
        }));
    }

    private static @NotNull Map<String, String> resolveAllDomains() {
        Map<String, String> result = new LinkedHashMap<>();
        ExecutorService executor = Executors.newFixedThreadPool(DOMAIN_CANDIDATES.size());
        List<Future<Map.Entry<String, String>>> futures = new ArrayList<>();

        for (Map.Entry<String, List<String>> domainEntry : DOMAIN_CANDIDATES.entrySet()) {
            String domain = domainEntry.getKey();
            List<String> ips = domainEntry.getValue();
            futures.add(executor.submit(() -> {
                String reachableIP = findFirstReachable(ips);
                if (reachableIP != null) {
                    return new AbstractMap.SimpleEntry<>(domain, reachableIP);
                }
                return null;
            }));
        }

        for (Future<Map.Entry<String, String>> future : futures) {
            try {
                Map.Entry<String, String> entry = future.get();
                if (entry != null)
                    result.put(entry.getKey(), entry.getValue());
            } catch (InterruptedException | ExecutionException ignored) {}
        }
        executor.shutdown();
        return result;
    }

    private static @Nullable String findFirstReachable(@NotNull List<String> ips) {
        if (ips.isEmpty()) {
            return null;
        }
        List<CompletableFuture<String>> futures = ips.stream()
                .map(ip -> CompletableFuture.supplyAsync(() -> {
                    if (isReachableTcp443(ip)) {
                        return ip;
                    }
                    throw new RuntimeException("unreachable");
                }))
                .toList();

        try {
            return (String) CompletableFuture.anyOf(futures.toArray(new CompletableFuture[0])).get();
        } catch (Exception e) {
            // 所有 IP 均不可达
            return null;
        } finally {
            // 尝试取消还在进行的连接任务
            futures.forEach(f -> f.cancel(true));
        }
    }

    private static boolean isReachableTcp443(@NotNull String ip) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(ip, 443), 3000);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    private static @NotNull Path getHostsPath() {
        String os = System.getProperty("os.name").toLowerCase();
        if (os.contains("win")) {
            return Paths.get(System.getenv("SystemRoot"), "System32", "drivers", "etc", "hosts");
        } else {
            return Paths.get("/etc/hosts");
        }
    }

    private static void backupHosts(@NotNull Path hostsPath) throws IOException {
        Path backup = hostsPath.resolveSibling("hosts.backup_" + System.currentTimeMillis());
        Files.copy(hostsPath, backup, StandardCopyOption.REPLACE_EXISTING);
    }

    private static void restoreHosts(@NotNull Path hostsPath) throws IOException {
        List<String> lines = Files.readAllLines(hostsPath);
        int start = lines.indexOf(MARKER_START);
        int end = lines.indexOf(MARKER_END);
        if (start != -1 && end != -1 && end > start) {
            lines.subList(start, end + 1).clear();
            Files.write(hostsPath, lines);
        }
    }
}