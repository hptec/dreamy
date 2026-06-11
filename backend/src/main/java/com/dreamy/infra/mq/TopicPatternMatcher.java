package com.dreamy.infra.mq;

/**
 * AMQP topic 通配匹配（`*`=恰一词，`#`=任意词段）。
 * stub 模式同步分发与单测共用；real 模式由 RabbitMQ broker 原生匹配。
 */
public final class TopicPatternMatcher {

    private TopicPatternMatcher() {
    }

    public static boolean matches(String bindingKey, String routingKey) {
        if (bindingKey == null || routingKey == null) {
            return false;
        }
        if (bindingKey.equals(routingKey)) {
            return true;
        }
        String regex = toRegex(bindingKey);
        return routingKey.matches(regex);
    }

    private static String toRegex(String bindingKey) {
        StringBuilder sb = new StringBuilder();
        String[] words = bindingKey.split("\\.", -1);
        for (int i = 0; i < words.length; i++) {
            if (i > 0) {
                sb.append("\\.");
            }
            switch (words[i]) {
                case "*" -> sb.append("[^.]+");
                case "#" -> sb.append(".*");
                default -> sb.append(java.util.regex.Pattern.quote(words[i]));
            }
        }
        return sb.toString();
    }
}
