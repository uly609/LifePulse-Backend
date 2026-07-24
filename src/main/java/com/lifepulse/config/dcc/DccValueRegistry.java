package com.lifepulse.config.dcc;

import com.lifepulse.config.policy.RuntimePolicy;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.stereotype.Component;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.Field;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Component
public class DccValueRegistry implements BeanPostProcessor, SmartInitializingSingleton {
    private final List<Binding> bindings = new CopyOnWriteArrayList<>();

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        ReflectionUtils.doWithFields(bean.getClass(), field -> {
            DccValue dccValue = field.getAnnotation(DccValue.class);
            if (dccValue != null) {
                field.setAccessible(true);
                bindings.add(new Binding(bean, field, dccValue.value()));
            }
        });
        return bean;
    }

    @Override
    public void afterSingletonsInstantiated() {
        refreshAll();
    }

    public void refreshAll() {
        RuntimePolicy.Snapshot snapshot = RuntimePolicy.current();
        for (Binding binding : bindings) {
            binding.apply(snapshot);
        }
    }

    private record Binding(Object bean, Field field, String key) {
        void apply(RuntimePolicy.Snapshot snapshot) {
            Object value = switch (key) {
                case "shop-cache-ttl-seconds" -> snapshot.shopCacheTtlSeconds();
                case "seckill-enabled" -> snapshot.seckillEnabled();
                case "seckill-rate-limit-per-second" -> snapshot.seckillRateLimitPerSecond();
                case "group-rate-limit-per-second" -> snapshot.groupRateLimitPerSecond();
                case "order-timeout-minutes" -> snapshot.orderTimeoutMinutes();
                default -> null;
            };
            if (value == null) {
                return;
            }
            try {
                Class<?> type = field.getType();
                if (type == long.class || type == Long.class) {
                    field.set(bean, ((Number) value).longValue());
                } else if (type == int.class || type == Integer.class) {
                    field.set(bean, ((Number) value).intValue());
                } else if (type == boolean.class || type == Boolean.class) {
                    field.set(bean, value);
                } else if (type == String.class) {
                    field.set(bean, String.valueOf(value));
                }
            } catch (IllegalAccessException ignored) {
            }
        }
    }
}
