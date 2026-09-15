package com.neps.ai;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class AiAgentRequestGuard {

    private final int maxRequests;
    private final long windowNanos;

    private final Set<String> activeUsers =
            ConcurrentHashMap.newKeySet();

    private final ConcurrentHashMap<
            String,
            ArrayDeque<Long>> requestTimes =
            new ConcurrentHashMap<>();

    public AiAgentRequestGuard(
            @Value(
                    "${app.ai.max-requests-per-window:10}")
            int maxRequests,

            @Value(
                    "${app.ai.rate-limit-window-seconds:60}")
            long windowSeconds) {

        if (maxRequests <= 0
                || windowSeconds <= 0) {

            throw new IllegalArgumentException(
                    "Agent限流参数必须大于0");
        }

        this.maxRequests = maxRequests;
        this.windowNanos =
                Duration.ofSeconds(
                                windowSeconds)
                        .toNanos();
    }

    public Permit acquire(
            String username) {

        /*
         * 同一用户已有请求运行时直接拒绝，
         * 避免重复点击产生多次模型费用。
         */
        if (!activeUsers.add(username)) {
            throw new ResponseStatusException(
                    HttpStatus.TOO_MANY_REQUESTS,
                    "已有AI问答正在处理中，请等待完成");
        }

        try {
            ArrayDeque<Long> times =
                    requestTimes.computeIfAbsent(
                            username,
                            ignored ->
                                    new ArrayDeque<>());

            long now =
                    System.nanoTime();

            long cutoff =
                    now - windowNanos;

            synchronized (times) {
                while (!times.isEmpty()
                        && times.peekFirst()
                        <= cutoff) {

                    times.removeFirst();
                }

                if (times.size()
                        >= maxRequests) {

                    throw new ResponseStatusException(
                            HttpStatus.TOO_MANY_REQUESTS,
                            "AI问答请求过于频繁，请稍后重试");
                }

                times.addLast(now);
            }

            return () ->
                    activeUsers.remove(
                            username);

        } catch (RuntimeException exception) {
            activeUsers.remove(username);
            throw exception;
        }
    }

    /*
     * ponytail: 当前状态仅在单个应用实例内共享；
     * 多实例部署时替换为Redis原子计数和分布式锁。
     */
    @FunctionalInterface
    public interface Permit
            extends AutoCloseable {

        @Override
        void close();
    }
}
