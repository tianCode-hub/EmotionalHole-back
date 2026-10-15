package com.myk.emotionalHole.config;

import org.springframework.core.convert.converter.Converter;
import org.springframework.core.convert.converter.ConverterFactory;

/**
 * 字符串到数字类型的安全转换器
 *
 * 小程序前端在参数未赋值时常把 JS 的 undefined/null 拼进 URL（如 emotionType=undefined），
 * Spring 默认的 String→Integer 转换会直接抛 NumberFormatException 导致接口 500。
 * 本转换器将 "undefined"/"null"/空字符串 统一视为 null，其余按标准解析，避免整类接口报错。
 */
public class StringToNumberConverterFactory implements ConverterFactory<String, Number> {

    @Override
    public <T extends Number> Converter<String, T> getConverter(Class<T> targetType) {
        return new StringToNumber<>(targetType);
    }

    private static final class StringToNumber<T extends Number> implements Converter<String, T> {

        private final Class<T> targetType;

        private StringToNumber(Class<T> targetType) {
            this.targetType = targetType;
        }

        @Override
        @SuppressWarnings("unchecked")
        public T convert(String source) {
            if (source == null || source.isBlank()
                    || "undefined".equalsIgnoreCase(source.trim())
                    || "null".equalsIgnoreCase(source.trim())) {
                return null;
            }
            String text = source.trim();
            if (targetType.equals(Integer.class)) {
                return (T) Integer.valueOf(text);
            }
            if (targetType.equals(Long.class)) {
                return (T) Long.valueOf(text);
            }
            if (targetType.equals(Double.class)) {
                return (T) Double.valueOf(text);
            }
            if (targetType.equals(Float.class)) {
                return (T) Float.valueOf(text);
            }
            if (targetType.equals(Short.class)) {
                return (T) Short.valueOf(text);
            }
            if (targetType.equals(Byte.class)) {
                return (T) Byte.valueOf(text);
            }
            throw new IllegalArgumentException("Cannot convert String to " + targetType.getName());
        }
    }
}
